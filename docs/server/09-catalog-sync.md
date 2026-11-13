# 外部题库同步

本文说明如何用 `scripts/sync_clist_problems.py` 把 clist.by 的 Codeforces / AtCoder 题目标题、评分、链接和统计导入 `external_problems`，读者为运维与后端开发人员。外部题库只存元数据，不存题面、不存测试数据，也不参与本站判题。

> 返回目录：[后端文档索引](README.md)

## 1. 数据来源与边界

| 项 | 说明 |
| --- | --- |
| 数据源 | [clist.by](https://clist.by/api/v4/doc/) 的 `/api/v4/problem/` |
| 支持平台 | `codeforces.com`、`atcoder.jp`；脚本不导入其他 OJ |
| 写入表 | `external_problems`、`external_problem_tags`，必要时在 `tags` / `scopes` 中创建新行 |
| 不写入 | 题面、测试数据、提交记录；外部题不能在本站提交 |
| 标签来源 | Codeforces 官方 `problemset.problems` API；AtCoder 没有主题标签来源，普通同步不改动已有标签 |
| 时间 | clist 返回 UTC，脚本转换为中国时区（UTC+8）后写入 `external_problems.time` |
| 去重 | `url` 唯一；`code`（如 `CF_2278G`）唯一，无法推导时可为 `NULL` |

`code` 与 `difficulty` 的推导规则：

- Codeforces：`CF_` + contest + index；AtCoder：`AT_` + task id，例如 `AT_ABC482_G`。
- 难度按平台阈值从 `rating` 推导：
  - Codeforces：`< 1400` 为 `EASY`，`1400–1999` 为 `MEDIUM`，`>= 2000` 为 `HARD`。
  - AtCoder：`< 800` 为 `EASY`，`800–1599` 为 `MEDIUM`，`>= 1600` 为 `HARD`。
  - `rating` 缺失时写入 `UNKNOWN`。

## 2. 环境准备

要求 Python 3.10+，安装依赖：

```bash
cd scripts
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
```

复制环境变量模板并填写：

```bash
cp .env.example .env
```

配置完成后回到仓库根目录，后续命令都在仓库根目录执行：

```bash
cd ..
```

`scripts/.env` 的字段：

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `CLIST_USERNAME` | 是 | clist.by 账号 |
| `CLIST_API_KEY` | 是 | clist.by API key |
| `IOJ_DB_HOST` / `IOJ_DB_PORT` / `IOJ_DB_NAME` | 否 | 默认 `127.0.0.1` / `3306` / `ioj` |
| `IOJ_DB_USER` / `IOJ_DB_PASSWORD` | 是 | 与后端使用同一套数据库凭据 |

`scripts/.env` 已被 Git 忽略；不要把真实凭据提交到仓库。脚本也支持直接使用当前 shell 的环境变量，环境变量优先于 `.env`。

## 3. 常用命令

以下命令在**仓库根目录**执行，先激活虚拟环境：

```bash
# 检查 clist 返回的原始字段，不写库
python3 scripts/sync_clist_problems.py inspect

# 只解析前 5 条，打印将要写入的内容
python3 scripts/sync_clist_problems.py sync --dry-run --limit 5

# 增量同步：从上次水位继续，遇到已同步过的题目停止
python3 scripts/sync_clist_problems.py sync

# 全量同步：忽略增量水位，遍历两个平台
python3 scripts/sync_clist_problems.py sync --full

# 只同步某个平台
python3 scripts/sync_clist_problems.py sync --platforms codeforces.com

# 限制每个平台处理的条数/页数，便于调试
python3 scripts/sync_clist_problems.py sync --limit 100 --max-pages 2
```

clist.by 限制每分钟 10 次请求，脚本默认每次请求间隔 6.2 秒。不要为了“快一点”把 `--interval` 调到远低于默认值，否则可能被限流或封禁。

## 4. 全量重建与破坏性操作

```bash
python3 scripts/sync_clist_problems.py sync --clear
```

`--clear` 会：

1. 先按 `url` 快照已有的标签关联。
2. 删除 `external_problems` 的全部行（外键会级联删除 `external_problem_tags`）。
3. 从头导入两个平台，并恢复可恢复的标签关联；Codeforces 标签从官方 API 重建，AtCoder 使用快照。

> **破坏性操作**：`--clear` 会清空外部题库，影响范围是 `external_problems` 与 `external_problem_tags`，不影响本地题目、提交和用户。执行前先备份数据库（见 [部署与运维 §7](04-deployment.md#7-备份与恢复)），并确认 `external_problems` 中没有人工维护、无法从 clist 重新导入的行。`--clear` 不能与 `--dry-run` 同时使用。

## 5. 增量水位与状态文件

脚本默认把每个平台已经处理到的最大题目 id 写入 `scripts/.state/clist_sync.json`：

- 默认 `sync` 从水位之后继续，遇到已存在的题目停止。
- `--full` 忽略水位，从头遍历。
- `--limit` / `--max-pages` 只处理受限数量；只有实际处理过的行才推进水位。
- 删除状态文件或使用 `--full` 会触发全量扫描；不会删除数据库行。

`--state-file <path>` 可以指定其他水位文件，适合在 CI 或多环境隔离时使用。

## 6. 失败与重试

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `401` / `403` | clist 账号或 API key 错误 | 检查 `CLIST_USERNAME` / `CLIST_API_KEY`，确认 key 未过期 |
| `429` | 请求过快 | 保持默认 `--interval`，等待后重试；不要并发跑多个同步 |
| `Unknown database` / `Access denied` | 数据库环境变量未生效 | 检查 `scripts/.env` 或当前 shell；确认与后端使用同一个库 |
| 导入后 `code` 为 `NULL` | clist 的 URL 不符合平台正则 | 用 `inspect` 查看原始 `url`，再调整 `PLATFORMS` 的推导规则 |
| 难度全为 `UNKNOWN` | 源数据没有 `rating` | 正常现象；外部题无法从其他字段可靠推导难度 |
| 标签为空 | 平台没有标签来源，或尚未同步 Codeforces 标签 | 重新执行一次 Codeforces 同步；AtCoder 保留已有标签 |
| `--clear` 后题目变少 | 本次同步被 `--limit` / `--max-pages` 限制 | 去掉限制重新执行 `sync --full` |

## 7. 与接口的关系

- `GET /api/v1/catalog` 把外部题和本地已发布题目合并分页，字段与筛选规则见 [统一题库 API](../api/frontend/02-catalog.md)。
- `GET /api/v1/meta/scopes` 会显示外部平台的 scope 和使用量；scope 由脚本按平台名创建或复用。
- 外部题的 `submitCount` / `acceptedCount` 是 clist 或人工导入的统计，**不会**随本站提交增加。

## 相关文档

- [数据库设计](02-database.md)
- [统一题库 API](../api/frontend/02-catalog.md)
- [部署与运维](04-deployment.md)
