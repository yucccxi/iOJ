#!/usr/bin/env python3
"""Sync external OJ problem metadata from clist.by into iOJ's ``external_problems`` table.

Why clist: it already normalises Codeforces and AtCoder into one API, so the script does not have
to scrape each site's HTML and does not risk getting blocked by them. Only these two platforms are
supported; the local catalog does not import other OJs.

Subcommands
    inspect   one request per platform and print the raw payload, so the field mapping can be
              checked against the real API instead of being guessed
    sync      page through ``/api/v4/problem/``, derive ``<PREFIX>_<contest><index>`` codes
              (CF_2278G, AT_AWC0191E, ...) and upsert one row per problem

Field mapping (confirmed against the live API)
    resource            -> sources.name   (created on demand)
    url + short         -> code           "CF_" + contest + index, upper-cased
    name                -> title
    rating              -> rating         (may be null)
    n_attempts          -> submit_count   number of people who attempted
    n_accepted          -> accepted_count number of people who solved
    url                 -> url            the row identity used for updates

clist allows 10 requests per minute, so requests default to one every 6.2 s. ``--full`` walks the
whole platform, the default mode stops as soon as it reaches problems already stored in
``scripts/.state/clist_sync.json``.

``sync --clear`` deletes every row of ``external_problems`` before importing and therefore forces a
full sync; use it when the table should be rebuilt from scratch. It is rejected together with
``--dry-run``.

Credentials come from the environment, or from ``scripts/.env`` (git-ignored):
    CLIST_USERNAME, CLIST_API_KEY
    IOJ_DB_HOST / IOJ_DB_PORT / IOJ_DB_NAME / IOJ_DB_USER / IOJ_DB_PASSWORD

Examples
    python3 scripts/sync_clist_problems.py inspect
    python3 scripts/sync_clist_problems.py sync --dry-run --limit 5
    python3 scripts/sync_clist_problems.py sync --clear
    python3 scripts/sync_clist_problems.py sync --full
    python3 scripts/sync_clist_problems.py sync
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import requests

API_BASE = "https://clist.by/api/v4"
SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_STATE_FILE = SCRIPT_DIR / ".state" / "clist_sync.json"
DEFAULT_INTERVAL_SECONDS = 6.2  # ~9.7 requests per minute, below the 10/min limit
DEFAULT_PAGE_SIZE = 100

# How to turn one clist problem into our own columns.
#   contest -- URL fragment carrying the platform's own contest number (Codeforces: 2278)
#   task    -- URL fragment carrying the problem identity. AtCoder task ids already start with the
#              contest type (abc482_g, arc100_e, agc001_a, awc0191_e, ahc074_a ...), so keeping the
#              whole task id makes the type visible in the code. It is also the stable identity:
#              the same task mirrored into a training contest (adt_all_20260401_1/tasks/abc285_f)
#              keeps one code instead of becoming a duplicate row.
# Only Codeforces and AtCoder are imported; adding another platform here also requires a code
# derivation rule (contest or task) and a matching toolchain for the catalog.
PLATFORMS: dict[str, dict[str, str | None]] = {
    "codeforces.com": {"source": "Codeforces", "prefix": "CF_",
                       "contest": r"/(?:contest|gym|problemset/problem)/(\d+)"},
    "atcoder.jp": {"source": "AtCoder", "prefix": "AT_", "task": r"/tasks/([^/?#]+)"},
}
DEFAULT_PLATFORMS = list(PLATFORMS)

log = logging.getLogger("sync-clist")


def load_dotenv(path: Path) -> None:
    """Loads KEY=VALUE lines into the environment without overwriting what is already set."""
    if not path.is_file():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip("\"'"))


def env(name: str, default: str | None = None, required: bool = False) -> str:
    value = os.environ.get(name, default)
    if required and not value:
        sys.exit(f"缺少环境变量 {name}（可写在 scripts/.env）")
    return value or ""


class ClistClient:
    """Minimal clist API v4 client: API-key auth, hard rate limit, retry on throttling."""

    def __init__(self, username: str, api_key: str, interval: float) -> None:
        if not username or not api_key:
            sys.exit("缺少 CLIST_USERNAME / CLIST_API_KEY（可写在 scripts/.env）")
        self.session = requests.Session()
        self.session.headers.update({
            "Authorization": f"ApiKey {username}:{api_key}",
            "Accept": "application/json",
            "User-Agent": "ioj-clist-sync/1.0",
        })
        self.interval = interval
        self._last_request = 0.0

    def _throttle(self) -> None:
        elapsed = time.monotonic() - self._last_request
        if elapsed < self.interval:
            time.sleep(self.interval - elapsed)
        self._last_request = time.monotonic()

    def get(self, path: str, **params: Any) -> dict[str, Any]:
        last_error = ""
        for attempt in range(1, 5):
            self._throttle()
            try:
                response = self.session.get(f"{API_BASE}{path}", params=params, timeout=30)
            except requests.RequestException as ex:
                last_error = str(ex)
                log.warning("请求失败（第 %s 次）：%s", attempt, ex)
                time.sleep(min(60, 5 * attempt))
                continue
            if response.status_code == 401:
                sys.exit("clist 返回 401：CLIST_USERNAME / CLIST_API_KEY 不正确")
            if response.status_code == 429 or response.status_code >= 500:
                wait = min(60, 5 * attempt)
                log.warning("HTTP %s（第 %s 次），%s 秒后重试", response.status_code, attempt, wait)
                last_error = f"HTTP {response.status_code}"
                time.sleep(wait)
                continue
            if response.status_code >= 400:
                sys.exit(f"clist 返回 {response.status_code}：{response.text[:300]}")
            try:
                return response.json()
            except ValueError:
                sys.exit(f"clist 返回的不是 JSON：{response.text[:300]}")
        sys.exit(f"clist 请求重试 4 次仍失败：{last_error}")

    def problems(self, resource: str, limit: int, offset: int) -> dict[str, Any]:
        return self.get("/problem/", resource=resource, order_by="-id", limit=limit, offset=offset)

    def resources(self, limit: int = 100) -> list[dict[str, Any]]:
        return self.get("/resource/", order_by="id", limit=limit).get("objects", [])


MAX_CODE_LENGTH = 32  # external_problems.code is VARCHAR(32) and MySQL runs in strict mode


def sanitize(token: str) -> str | None:
    """Upper-cases a token and collapses every run of non-alphanumeric characters into ``_``."""
    cleaned = re.sub(r"[^A-Za-z0-9]+", "_", token).strip("_").upper()
    return cleaned or None


def clamp(code: str) -> str:
    """Keeps a code inside the column width without cutting off the problem index.

    MySQL runs with STRICT_TRANS_TABLES, so a longer value would fail the INSERT instead of being
    truncated by the server. The tail (the problem index) is what tells two problems of one contest
    apart, so it is preserved and the contest token is shortened instead.
    """
    if len(code) <= MAX_CODE_LENGTH:
        return code
    head, sep, tail = code.rpartition("_")
    if sep and 0 < len(tail) < MAX_CODE_LENGTH // 2:
        keep = MAX_CODE_LENGTH - len(tail) - 1
        return f"{head[:keep].rstrip('_')}_{tail}"
    return code[:MAX_CODE_LENGTH]


def build_code(platform: str, problem: dict[str, Any]) -> str | None:
    """Derives the catalog identifier, e.g. ``CF_2278G`` / ``AT_ABC482_G`` / ``AT_ARC100_E``, or None.

    AtCoder: the code is the task id, upper-cased. The type (ABC / ARC / AGC / AWC / AHC ...) is the
    leading part of that id, so it is always visible, and training-contest mirrors of the same task
    collapse into one row.

    Codeforces: ``CF_`` + contest number + problem index. The index comes from ``short``; when clist
    leaves it empty it is read from the ``/problem/<index>`` part of the URL.
    """
    config = PLATFORMS[platform]
    url = (problem.get("url") or "").strip()
    short = sanitize(problem.get("short") or "")

    task_pattern = config.get("task")
    if task_pattern:
        if not url:
            return None
        match = re.search(str(task_pattern), url)
        if not match:
            return None
        task = sanitize(match.group(1))
        return clamp(f"{config['prefix']}{task}") if task else None

    contest_pattern = config.get("contest")
    if contest_pattern:
        if not url:
            return None
        match = re.search(str(contest_pattern), url)
        if not match:
            return None
        contest = sanitize(match.group(1))
        index = short
        if not index:
            # Two URL shapes are in use:
            #   /contest/2278/problem/G        -> the segment after /problem/ is the index
            #   /problemset/problem/1/A        -> skip the contest number before the index
            tail = re.search(r"/problem/(?:\d+/)?([A-Za-z0-9]+)", url)
            index = sanitize(tail.group(1)) if tail else None
        if not contest or not index:
            return None
        return clamp(f"{config['prefix']}{contest}{index}")

    return clamp(f"{config['prefix']}{short}") if short else None


class Store:
    """Thin upsert layer; the schema is owned by db/schema.sql, this only writes rows."""

    def __init__(self, connection: Any) -> None:
        self.connection = connection
        self._sources: dict[str, int] = {}

    def source_id(self, name: str) -> int:
        if name in self._sources:
            return self._sources[name]
        with self.connection.cursor() as cursor:
            cursor.execute(
                "INSERT IGNORE INTO sources (name, created_at) VALUES (%s, NOW())", (name,))
            cursor.execute("SELECT id FROM sources WHERE name = %s", (name,))
            row = cursor.fetchone()
        if row is None:
            sys.exit(f"无法创建来源 {name}（sources 表是否有写权限？）")
        self.connection.commit()
        self._sources[name] = int(row[0])
        return int(row[0])

    def upsert(self, source_id: int, code: str, title: str, rating: int | None,
               attempts: int, accepted: int, url: str) -> str:
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT id FROM external_problems WHERE code = %s", (code,))
            row = cursor.fetchone()
            if row:
                cursor.execute(
                    "UPDATE external_problems SET title = %s, rating = %s, submit_count = %s, "
                    "accepted_count = %s, updated_at = NOW() WHERE id = %s",
                    (title, rating, attempts, accepted, row[0]))
                return "updated"
            cursor.execute("SELECT id FROM external_problems WHERE url = %s", (url,))
            row = cursor.fetchone()
            if row:
                # Imported earlier without a code: fill it in and refresh the counters.
                cursor.execute(
                    "UPDATE external_problems SET code = %s, title = %s, rating = %s, submit_count = %s, "
                    "accepted_count = %s, updated_at = NOW() WHERE id = %s",
                    (code, title, rating, attempts, accepted, row[0]))
                return "updated"
            cursor.execute(
                "INSERT INTO external_problems "
                "(source_id, code, title, rating, submit_count, accepted_count, url, created_at, updated_at) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, NOW(), NOW())",
                (source_id, code, title, rating, attempts, accepted, url))
            return "inserted"

    def clear_external_problems(self) -> int:
        """Deletes every external problem and its tag links (the FK cascades)."""
        with self.connection.cursor() as cursor:
            # WHERE id > 0 also works when the client enables sql_safe_updates.
            cursor.execute("DELETE FROM external_problems WHERE id > 0")
            deleted = cursor.rowcount
        self.connection.commit()
        return int(deleted)


def load_state(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        log.warning("状态文件无法解析，按首次同步处理：%s", path)
        return {}


def save_state(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(state, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
                    encoding="utf-8")


def connect() -> Any:
    """Opens the iOJ MySQL database. Imported lazily so ``inspect`` needs no driver."""
    try:
        import pymysql
    except ImportError:
        sys.exit("缺少 PyMySQL：scripts/.venv/bin/pip install -r scripts/requirements.txt")
    return pymysql.connect(
        host=env("IOJ_DB_HOST", "127.0.0.1"),
        port=int(env("IOJ_DB_PORT", "3306")),
        database=env("IOJ_DB_NAME", "ioj"),
        user=env("IOJ_DB_USER", required=True),
        password=env("IOJ_DB_PASSWORD", required=True),
        charset="utf8mb4",
        autocommit=False,
    )


def cmd_inspect(args: argparse.Namespace) -> int:
    client = ClistClient(env("CLIST_USERNAME"), env("CLIST_API_KEY"), args.interval)
    if args.list_resources:
        print(f"=== clist resources（前 {args.page_size} 个）===")
        for resource in client.resources(limit=args.page_size):
            print(json.dumps(resource, ensure_ascii=False))
    for platform in args.platforms:
        data = client.problems(platform, limit=1, offset=0)
        objects = data.get("objects") or []
        print(f"\n=== {platform} ===\nmeta = {json.dumps(data.get('meta'), ensure_ascii=False)}")
        if not objects:
            print("没有返回数据")
            continue
        for obj in objects:
            print(json.dumps(obj, ensure_ascii=False, indent=2, sort_keys=True))
            print(f"derived code = {build_code(platform, obj)}")
    return 0


def cmd_sync(args: argparse.Namespace) -> int:
    if args.clear and args.dry_run:
        sys.exit("--clear 会删除数据库数据，不能与 --dry-run 一起使用")
    if args.clear and (args.limit or args.max_pages):
        log.warning("--clear 后只导入受限数量的题目，表内将不是完整题库；确认这是你要的结果")

    client = ClistClient(env("CLIST_USERNAME"), env("CLIST_API_KEY"), args.interval)
    state = load_state(args.state_file)
    store = None if args.dry_run else Store(connect())
    if store:
        log.info("已连接 %s/%s", env("IOJ_DB_HOST", "127.0.0.1"), env("IOJ_DB_NAME", "ioj"))
        if args.clear:
            deleted = store.clear_external_problems()
            # Clearing the table invalidates every incremental watermark. Persist the empty state
            # immediately so an interrupted import cannot leave a high-water mark behind.
            state = {}
            save_state(args.state_file, state)
            log.info("已清空 external_problems：%s 行", deleted)

    totals = {"inserted": 0, "updated": 0, "skipped": 0}
    skip_reasons: dict[str, int] = {}
    preview: list[tuple[str, str, int | None, int, int]] = []

    for platform in args.platforms:
        config = PLATFORMS[platform]
        source_id = store.source_id(str(config["source"])) if store else None
        stop_below = None if (args.full or args.clear) else (state.get(platform) or {}).get("max_problem_id")
        offset = pages = seen = max_id = 0
        log.info("开始 %s（来源=%s，增量截止 problem id=%s）",
                 platform, config["source"], stop_below if stop_below else "无（全量）")

        while True:
            if args.max_pages and pages >= args.max_pages:
                log.info("%s 达到 --max-pages=%s，停止", platform, args.max_pages)
                break
            objects = client.problems(platform, limit=args.page_size, offset=offset).get("objects") or []
            if not objects:
                break
            pages += 1
            reached_known = False
            for obj in objects:
                if stop_below is not None and int(obj["id"]) <= int(stop_below):
                    reached_known = True
                    continue
                if args.limit and seen >= args.limit:
                    break
                seen += 1
                # Only rows we actually handled move the watermark, so a --limit run leaves the
                # rest of the page for the next run instead of silently skipping them.
                max_id = max(max_id, int(obj["id"]))

                code = build_code(platform, obj)
                url = (obj.get("url") or "").strip()
                if code is None:
                    reason = ("url 缺失" if not url
                              else "url 里取不到比赛号" if config["contest"] else "缺少 short")
                    totals["skipped"] += 1
                    skip_reasons[reason] = skip_reasons.get(reason, 0) + 1
                    continue

                title = (obj.get("name") or "").strip() or code
                rating = obj.get("rating")
                attempts = int(obj.get("n_attempts") or 0)
                accepted = int(obj.get("n_accepted") or 0)

                if store is None:
                    totals["inserted"] += 1
                    if len(preview) < 5:
                        preview.append((code, title, rating, attempts, accepted))
                    continue

                try:
                    action = store.upsert(source_id, code, title, rating, attempts, accepted, url)
                except Exception as ex:  # noqa: BLE001 - one bad row must not stop the run
                    store.connection.rollback()
                    log.warning("写入失败 code=%s：%s", code, ex)
                    totals["skipped"] += 1
                    skip_reasons["数据库写入失败"] = skip_reasons.get("数据库写入失败", 0) + 1
                    continue
                totals[action] += 1
                if action == "inserted" and totals["inserted"] <= 3:
                    log.info("示例新增：%s %s（%s）", code, title, url)

            if store:
                store.connection.commit()
            log.info("%s 第 %s 页：%s 条（本次累计 %s 条）", platform, pages, len(objects), seen)
            if reached_known or len(objects) < args.page_size or (args.limit and seen >= args.limit):
                break
            offset += args.page_size

        state[platform] = {
            "max_problem_id": max_id,
            "synced_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        }

    if store:
        save_state(args.state_file, state)
    if preview:
        print("\n--dry-run：以下 5 条为示例，未写入数据库")
        for code, title, rating, attempts, accepted in preview:
            print(f"  {code:<14} rating={rating!s:<6} 提交人数={attempts:<6} 通过人数={accepted:<6} {title}")
    if args.dry_run:
        log.info("dry-run 完成：可处理 %s 条，跳过 %s 条（没有写入数据库）",
                 totals["inserted"], totals["skipped"])
    else:
        log.info("完成：新增 %s，更新 %s，跳过 %s",
                 totals["inserted"], totals["updated"], totals["skipped"])
    if skip_reasons:
        log.info("跳过原因：%s", "，".join(f"{k} {v} 条" for k, v in sorted(skip_reasons.items())))
    return 0


def main(argv: list[str] | None = None) -> int:
    load_dotenv(SCRIPT_DIR / ".env")
    parser = argparse.ArgumentParser(
        description="把 clist.by 的外部题库同步到 iOJ 的 external_problems 表",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="凭据写在 scripts/.env：CLIST_USERNAME、CLIST_API_KEY、IOJ_DB_*",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    inspect_parser = subparsers.add_parser("inspect", help="打印平台的一条原始数据，核对字段映射")
    inspect_parser.add_argument("--platforms", default=",".join(DEFAULT_PLATFORMS))
    inspect_parser.add_argument("--page-size", type=int, default=DEFAULT_PAGE_SIZE)
    inspect_parser.add_argument("--interval", type=float, default=DEFAULT_INTERVAL_SECONDS)
    inspect_parser.add_argument("--list-resources", action="store_true",
                                help="额外列出 clist 支持的资源（多一次请求）")
    inspect_parser.set_defaults(func=cmd_inspect)

    sync_parser = subparsers.add_parser("sync", help="分页拉取并写入 external_problems")
    sync_parser.add_argument("--platforms", default=",".join(DEFAULT_PLATFORMS),
                             help=f"要同步的平台，逗号分隔；可选：{', '.join(PLATFORMS)}")
    sync_parser.add_argument("--full", action="store_true", help="忽略增量水位，从头同步")
    sync_parser.add_argument("--clear", action="store_true",
                             help="导入前清空 external_problems（隐含 --full，不能与 --dry-run 同用）")
    sync_parser.add_argument("--dry-run", action="store_true", help="只解析不写库")
    sync_parser.add_argument("--limit", type=int, default=0, help="每个平台最多处理多少条（0=不限）")
    sync_parser.add_argument("--max-pages", type=int, default=0, help="每个平台最多翻多少页（0=不限）")
    sync_parser.add_argument("--page-size", type=int, default=DEFAULT_PAGE_SIZE)
    sync_parser.add_argument("--interval", type=float, default=DEFAULT_INTERVAL_SECONDS,
                             help="两次请求的最小间隔秒数，默认 6.2 秒（clist 限制 10 次/分钟）")
    sync_parser.add_argument("--state-file", type=Path, default=DEFAULT_STATE_FILE,
                             help="增量水位文件，默认 scripts/.state/clist_sync.json")
    sync_parser.set_defaults(func=cmd_sync)

    args = parser.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-7s %(message)s",
                        datefmt="%H:%M:%S")
    platforms = [item.strip() for item in args.platforms.split(",") if item.strip()]
    unknown = [item for item in platforms if item not in PLATFORMS]
    if unknown or not platforms:
        parser.error(f"未知平台 {unknown}，可选：{', '.join(PLATFORMS)}")
    args.platforms = platforms
    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
