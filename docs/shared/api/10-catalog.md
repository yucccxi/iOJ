# 10. 统一搜索（本地题库 + 外部 OJ）

> 返回目录：[iOJ 前端接口文档（v1）](README.md)

把**本地题目**（`problems`）和**外部 OJ 题目**（`external_problems`，只存链接元数据）放进同一个列表里筛选与分页。两类题目共有的字段是 `title`、`difficulty`、`rating`、`scopeId/scopeName`、`tags`，这也是能合并搜索的原因。本地题的 scope 固定为系统保留 scope `LOCAL`；外部题额外返回 `code`（原站题号，如 `CF_123D`），本地题的 `code` 恒为 `null`。

本地题只返回**已发布（`PUBLISHED`）**的题目；外部题没有发布状态，全部参与。草稿/待审核/已下架的本地题请走 `GET /problems/mine` 或管理员审核接口。

> 本地题库首页可以请求 `GET /catalog?scopeId={LOCAL 的 id}`（id 从 `/meta/scopes` 获取）；题目详情与出题相关的写操作仍在 `/problems` 下（见 [4. 题库](04-problems.md)）。

## 10.1 搜索题目

`GET /catalog` ｜ 公开

**查询参数**（都可选，同时传表示"且"关系）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 按**标题**模糊搜索，本地题和外部题都会匹配；空字符串等于不筛选 |
| `difficulty` | string | 档位难度 `EASY` / `MEDIUM` / `HARD` / `UNKNOWN`（`UNKNOWN` 只用于外部题），**对本地题和外部题都生效**；可重复传多个，多个值之间是 OR，例如 `difficulty=EASY&difficulty=HARD`；传其他值返回 `400`（`1000`） |
| `tagId` | 整数 | 按标签筛选，**对两类题目都生效**（本地题查 `problem_tags`，外部题查 `external_problem_tags`）；可重复传多个，标签 id 从标签列表接口获取 |
| `tagMatch` | string | 多标签匹配模式：`ANY`（默认，满足任一标签）或 `ALL`（满足全部标签）；只传一个 `tagId` 时两种模式等价；传其他值返回 `400`（`1000`） |
| `rating` | 整数 | 难度分**精确**匹配 |
| `minRating` / `maxRating` | 整数 | 难度分**区间**（闭区间），可只传一边 |
| `scopeId` | 整数 | scope id（从 `/meta/scopes` 获取）。可重复传多个表示 OR，例如 `scopeId=16&scopeId=7`；本地题的 scope 是 `LOCAL` 的 id，外部题是各自平台的 id。不传表示全部。**传入不存在的 id 返回 `400`（`1000`）**，不是空列表 |
| `sort` | string | 排序依据：`DEFAULT`、`RATING`、`ACCEPTED`、`SUBMISSIONS`、`ACCEPTANCE_RATE`；不传为 `DEFAULT`。`RATING` 必须且只能指定一个 `scopeId` |
| `order` | string | 排序方向：`ASC` / `DESC`；不传为 `DESC`。无提交的题目在 `ACCEPTANCE_RATE` 下始终排最后，不受方向影响 |
| `page` / `size` | 整数 | 分页，见 [1.4 分页](01-conventions.md#14-分页) |

> 按具体 scope 过滤请使用 `scopeId=<id>`。

> 排序示例：`?scopeId=7&sort=RATING&order=ASC` 在 Codeforces 内从易到难，`?sort=ACCEPTED&order=DESC` 按通过数从高到低；`order` 不传时默认 `DESC`。

> 因为不同 scope 的 rating 刻度不可比，`sort=RATING` 必须且只能指定一个 `scopeId`。例如 `scopeId=7&sort=RATING` 合法；不传 `scopeId` 或传多个 `scopeId` 会返回 `400`（`1000`）。

> `scopeId` 的取值是对应 scope 的 id，名称只用于展示；id 从 `/meta/scopes` 获取。多个 `scopeId` 和 `difficulty` 都用重复参数表示 OR。

> 多个 `difficulty` 用重复参数表示 OR：`?difficulty=EASY&difficulty=HARD`；不要写成 `difficulty=EASY,HARD`。

> 多标签筛选示例：
> - 满足任一标签：`?tagId=3&tagId=5&tagMatch=ANY`
> - 满足全部标签：`?tagId=3&tagId=5&tagMatch=ALL`
> - 默认是 `ANY`，所以 `?tagId=3&tagId=5` 等价于上面的 OR 写法。
>
> `tagId` 可以跨本地题和外部题生效：本地题查 `problem_tags`，外部题查 `external_problem_tags`。

> `time` 已转换为 UTC+8，字符串本身不带时区后缀。

> `rating` 的刻度随 scope 而定（例如 Codeforces 800–3500、校内比赛可能没有评分），**跨 scope 排序不可比**；未设置评分的题目接口返回 `0`，但按 `rating` 筛选时不会命中（数据库里仍是 `NULL`）。

> 外部题的 `difficulty` 由 scope 的 rating 推导：Codeforces `<1400` 为 `EASY`、`1400–1999` 为 `MEDIUM`、`≥2000` 为 `HARD`；AtCoder `<800` 为 `EASY`、`800–1599` 为 `MEDIUM`、`≥1600` 为 `HARD`。rating 为空时归为 `UNKNOWN`。

**响应 `data`**

```json
{
  "items": [
    {
      "id": 12,
      "title": "A+B Problem",
      "code": null,
      "difficulty": "EASY",
      "time": null,
      "rating": 800,
      "scopeId": 16,
      "scopeName": "LOCAL",
      "url": null,
      "submitCount": 30,
      "acceptedCount": 18,
      "authorId": 1,
      "authorNickname": "Alice",
      "tags": [{ "id": 3, "name": "数学" }],
      "createdAt": "2026-10-30T11:18:05"
    },
    {
      "id": 5,
      "title": "Two Sum",
      "code": "CF_1A",
      "difficulty": "EASY",
      "time": "2026-10-31T22:35:00",
      "rating": 1200,
      "scopeId": 7,
      "scopeName": "Codeforces",
      "url": "https://codeforces.com/problemset/problem/1/A",
      "submitCount": 4200,
      "acceptedCount": 3100,
      "authorId": null,
      "authorNickname": null,
      "tags": [{ "id": 3, "name": "数学" }],
      "createdAt": "2026-11-01T09:30:00"
    }
  ],
  "total": 2,
  "page": 1,
  "size": 20
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 对应那张表的 id：本地题 → `GET /problems/{id}`；外部题 → 暂无详情接口，用 `url` 直接跳转 |
| `title` | 标题 |
| `code` | 外部题的原站题号（如 `CF_123D`、`AT_ABC123_D`）；本地题恒为 `null`，外部题无法推导题号时也可能为 `null` |
| `difficulty` | `EASY` / `MEDIUM` / `HARD`，外部题 Rating 为空时为 `UNKNOWN`；外部题由 scope 的 rating 推导 |
| `time` | 只有外部题有值，是 clist 的比赛时间，已转换为中国时区（UTC+8），格式 `yyyy-MM-ddTHH:mm:ss`；本地题恒为 `null`，外部题 clist 未提供时间时也可能为 `null` |
| `rating` | 数值难度分；未设置时返回 `0`（数据库仍存 `NULL`） |
| `scopeId` / `scopeName` | scope id / 名称；本地题恒为 `LOCAL`，外部题为原平台 scope，二者都不为 `null` |
| `url` | 只有外部题有值（原题链接）；本地题恒为 `null` |
| `submitCount` / `acceptedCount` | 累计提交次数 / 通过次数；通过率 = `acceptedCount / submitCount`（`submitCount=0` 时不要相除）。本地题由本站评测累加；外部题是原站/导入的数字，本站不维护 |
| `authorId` / `authorNickname` | 只有本地题有值：出题人 id / 当前昵称；外部题恒为 `null` |
| `tags` | 标签数组，两类题目共用标签表；外部题目前只有 Codeforces 有主题标签，AtCoder 通常为空 |
| `createdAt` | 录入时间 |

> 响应中**不再包含 `kind`**。前端可用 `url` 区分：`url == null` 为本地题，非 `null` 为外部题；也可用 `authorId`（本地题非空）或 `scopeName == "LOCAL"` 辅助判断。

**排序**：

| `sort` | 排序规则 |
| --- | --- |
| `DEFAULT` | 本地题按 `updatedAt`，外部题按 `time`；方向由 `order` 决定，默认 `DESC`；时间为空的外部题始终排最后 |
| `RATING` | 按 `rating`；未评分按 `0` 处理 |
| `ACCEPTED` | 按 `acceptedCount` |
| `SUBMISSIONS` | 按 `submitCount` |
| `ACCEPTANCE_RATE` | 按 `acceptedCount / submitCount`；`submitCount=0` 的题目始终排在最后 |

`RATING`、`ACCEPTED`、`SUBMISSIONS`、`ACCEPTANCE_RATE` 的方向也由 `order` 决定，默认 `DESC`。

以上排序在值相同时，都会再按本地/外部和 id 倒序做稳定 tie-break，保证翻页结果稳定。

**常见错误**：`1000`（`scopeId`/`rating`/`minRating`/`maxRating` 不是整数、`scopeId` 对应的 scope 不存在、`tagMatch` 不是 `ANY`/`ALL`、`sort` 或 `order` 不是合法枚举值、`sort=RATING` 时没有指定单一 `scopeId`、分页参数非法）。

## 10.2 前端渲染建议

- 用一个列表渲染，用 `url` 区分卡片：外链题显示 `scopeName + code + rating + 外链图标`；本地题显示 `difficulty + rating + 通过率`（`acceptedCount / submitCount`）；
  > 本接口**不返回** `status`：本地题在列表里恒为已发布。
- 本地题点击 → `GET /problems/{id}`；外链题点击 → `window.open(url)`（不带 `target="_blank"` 之外的额外跳转逻辑）；
- 筛选栏：scope 下拉（选项来自 `GET /meta/scopes`，选中后用其 `id` 作为 `scopeId` 参数，可多选）、标签下拉（按 `tagId`）、难度区间（`minRating`/`maxRating`）、搜索框（`keyword`）；
- `scopeId` 的选项可直接使用 `GET /meta/scopes` 返回的 `id` 与 `name`（其中包含保留 scope `LOCAL`）；
- 选择 scope 或标签后本地题也会参与结果（两者共用 scope 表、标签表），不需要前端做特殊分支。
