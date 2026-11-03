# 10. 统一搜索（本地题库 + 外部 OJ）

> 返回目录：[iOJ 前端接口文档（v1）](README.md)

把**本地题目**（`problems`）和**外部 OJ 题目**（`external_problems`，只存链接元数据）放进同一个列表里筛选与分页。两类题目共有的字段是 `title`、`difficulty`、`rating`、`sourceId/sourceName`、`tags`，这也是能合并搜索的原因。外部题额外返回 `code`（原站题号，如 `CF_123D`），本地题的 `code` 恒为 `null`。

本地题只返回**已发布（`PUBLISHED`）**的题目；外部题没有发布状态，全部参与。草稿/待审核/已下架的本地题请走 `GET /problems/mine` 或管理员审核接口。

> 本地题库首页就是 `GET /catalog?scope=LOCAL`；题目详情与出题相关的写操作仍在 `/problems` 下（见 [4. 题库](04-problems.md)）。

## 10.1 搜索题目

`GET /catalog` ｜ 公开

**查询参数**（都可选，同时传表示"且"关系）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 按**标题**模糊搜索，本地题和外部题都会匹配；空字符串等于不筛选 |
| `sourceId` | 整数 | 按来源筛选，**对两类题目都生效**（本地题与外部题共用同一张来源表）；没有该来源时返回空列表，不报错 |
| `difficulty` | string | 档位难度 `EASY` / `MEDIUM` / `HARD` / `UNKNOWN`（`UNKNOWN` 只用于外部题），**对本地题和外部题都生效**；可重复传多个，多个值之间是 OR，例如 `difficulty=EASY&difficulty=HARD`；传其他值返回 `400`（`1000`） |
| `tagId` | 整数 | 按标签筛选，**对两类题目都生效**（本地题查 `problem_tags`，外部题查 `external_problem_tags`）；可重复传多个，标签 id 从标签列表接口获取 |
| `tagMatch` | string | 多标签匹配模式：`ANY`（默认，满足任一标签）或 `ALL`（满足全部标签）；只传一个 `tagId` 时两种模式等价；传其他值返回 `400`（`1000`） |
| `rating` | 整数 | 难度分**精确**匹配 |
| `minRating` / `maxRating` | 整数 | 难度分**区间**（闭区间），可只传一边 |
| `scope` | string | 来源范围：`LOCAL`（本地题库）、`EXTERNAL`（全部外部题），或来源名称（如 `Codeforces`、`AtCoder`，从 `/meta/sources` 获取）。大小写不敏感，可重复传多个表示 OR，例如 `scope=LOCAL&scope=Codeforces`；不传表示全部。未知来源返回空列表，不报错 |
| `page` / `size` | 整数 | 分页，见 [1.4 分页](01-conventions.md#14-分页) |

> `scope` 用于区分本地题库和外部平台；`sourceId` 是 `sources` 表的具体来源 id，两者可以组合使用。多个 `scope` 和 `difficulty` 都用重复参数表示 OR。

> 多个 `difficulty` 用重复参数表示 OR：`?difficulty=EASY&difficulty=HARD`；不要写成 `difficulty=EASY,HARD`。

> 多标签筛选示例：
> - 满足任一标签：`?tagId=3&tagId=5&tagMatch=ANY`
> - 满足全部标签：`?tagId=3&tagId=5&tagMatch=ALL`
> - 默认是 `ANY`，所以 `?tagId=3&tagId=5` 等价于上面的 OR 写法。
>
> `tagId` 可以跨本地题和外部题生效：本地题查 `problem_tags`，外部题查 `external_problem_tags`。

> `time` 已转换为中国时区（UTC+8），字符串本身不带时区后缀，前端按 `Asia/Shanghai` 展示即可。

> `rating` 的刻度随来源而定（例如 Codeforces 800–3500、校内比赛可能没有评分），**跨来源排序不可比**；未设置评分的题目接口返回 `0`，但按 `rating` 筛选时不会命中（数据库里仍是 `NULL`）。

> 外部题的 `difficulty` 由来源 rating 推导：Codeforces `<1400` 为 `EASY`、`1400–1999` 为 `MEDIUM`、`≥2000` 为 `HARD`；AtCoder `<800` 为 `EASY`、`800–1599` 为 `MEDIUM`、`≥1600` 为 `HARD`。rating 为空时归为 `UNKNOWN`。

**响应 `data`**

```json
{
  "items": [
    {
      "kind": "LOCAL",
      "id": 12,
      "title": "A+B Problem",
      "code": null,
      "difficulty": "EASY",
      "time": null,
      "rating": 800,
      "sourceId": 2,
      "sourceName": "2026 新生赛",
      "url": null,
      "submitCount": 30,
      "acceptedCount": 18,
      "authorId": 1,
      "authorNickname": "Alice",
      "tags": [{ "id": 3, "name": "数学" }],
      "createdAt": "2026-10-30T11:18:05"
    },
    {
      "kind": "EXTERNAL",
      "id": 5,
      "title": "Two Sum",
      "code": "CF_1A",
      "difficulty": "EASY",
      "time": "2026-10-31T22:35:00",
      "rating": 1200,
      "sourceId": 7,
      "sourceName": "Codeforces",
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
| `kind` | `LOCAL`（本站题目）/ `EXTERNAL`（外部 OJ 链接）。**详情要走不同接口**，见下 |
| `id` | 对应那张表的 id：`LOCAL` → `GET /problems/{id}`；`EXTERNAL` → 暂无详情接口，用 `url` 直接跳转 |
| `title` | 标题 |
| `code` | `EXTERNAL` 的原站题号（如 `CF_123D`、`AT_ABC123_D`）；`LOCAL` 恒为 `null`，外部题无法推导题号时也可能为 `null` |
| `difficulty` | `EASY` / `MEDIUM` / `HARD`，外部题 Rating 为空时为 `UNKNOWN`；外部题由来源 rating 推导 |
| `time` | 只有 `EXTERNAL` 有值，是 clist 的比赛时间，已转换为中国时区（UTC+8），格式 `yyyy-MM-ddTHH:mm:ss`；`LOCAL` 恒为 `null`，外部题 clist 未提供时间时也可能为 `null` |
| `rating` | 数值难度分；未设置时返回 `0`（数据库仍存 `NULL`） |
| `sourceId` / `sourceName` | 来源 id / 名称；未设置来源时都为 `null`（`EXTERNAL` 一定有值） |
| `url` | 只有 `EXTERNAL` 有值（原题链接）；`LOCAL` 恒为 `null` |
| `submitCount` / `acceptedCount` | 累计提交次数 / 通过次数；通过率 = `acceptedCount / submitCount`（`submitCount=0` 时不要相除）。`LOCAL` 由本站评测累加；`EXTERNAL` 是原站/导入的数字，本站不维护 |
| `authorId` / `authorNickname` | 只有 `LOCAL` 有值：出题人 id / 当前昵称；`EXTERNAL` 恒为 `null` |
| `tags` | 标签数组，两类题目共用标签表；外部题目前只有 Codeforces 有主题标签，AtCoder 通常为空 |
| `createdAt` | 录入时间 |

**排序**：按 `createdAt` 倒序（最新在前）；同一时间戳时 `LOCAL` 在 `EXTERNAL` 之后、再按 id 倒序，保证翻页稳定。

**常见错误**：`1000`（`rating`/`minRating`/`maxRating` 不是整数、`tagMatch` 不是 `ANY`/`ALL`、分页参数非法）。未知的 `scope` 不报错，返回空列表。

## 10.2 前端渲染建议

- 用一个列表渲染，按 `kind` 切换卡片：`EXTERNAL` 显示 `sourceName + code + rating + 外链图标`，`LOCAL` 显示 `difficulty + rating + 通过率`（`acceptedCount / submitCount`）；
  > 本接口**不返回** `status`：本地题在列表里恒为已发布。
- 点击 `LOCAL` → `GET /problems/{id}`；点击 `EXTERNAL` → `window.open(url)`（不带 `target="_blank"` 之外的额外跳转逻辑）；
- 筛选栏：来源范围下拉（`scope=LOCAL` / `scope=Codeforces` / `scope=AtCoder`，可多选）、标签下拉（按 `tagId`）、难度区间（`minRating`/`maxRating`）、搜索框（`keyword`）；
- `scope` 的来源选项建议从 `GET /meta/sources` 动态生成，本地题库固定加一个 `LOCAL`；
- 选来源或标签后本地题也会参与结果（两者共用来源表、标签表），不需要前端做特殊分支。
