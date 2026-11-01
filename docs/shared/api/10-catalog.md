# 10. 统一搜索（本地题库 + 外部 OJ）

> 返回目录：[iOJ 前端接口文档（v1）](README.md)

把**本地题目**（`problems`）和**外部 OJ 题目**（`external_problems`，只存链接元数据）放进同一个列表里筛选与分页。两类题目共有的字段是 `title`、`rating`、`sourceId/sourceName`、`tags`，这也是能合并搜索的原因。

本地题只返回**已发布（`PUBLISHED`）**的题目；外部题没有发布状态，全部参与。草稿/待审核/已下架的本地题请走 `GET /problems/mine` 或管理员审核接口。

> 本地题库首页就是 `GET /catalog?kind=LOCAL`；题目详情与出题相关的写操作仍在 `/problems` 下（见 [4. 题库](04-problems.md)）。

## 10.1 搜索题目

`GET /catalog` ｜ 公开

**查询参数**（都可选，同时传表示"且"关系）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 按**标题**模糊搜索，本地题和外部题都会匹配；空字符串等于不筛选 |
| `sourceId` | 整数 | 按来源筛选，**对两类题目都生效**（本地题与外部题共用同一张来源表）；没有该来源时返回空列表，不报错 |
| `difficulty` | string | 档位难度 `EASY` / `MEDIUM` / `HARD`，**只对本地题生效**（外部题没有档位）；传其他值返回 `400`（`1000`） |
| `tagId` | 整数 | 按标签筛选，**对两类题目都生效**（各自查自己的关联表）；标签 id 从标签列表接口获取 |
| `rating` | 整数 | 难度分**精确**匹配 |
| `minRating` / `maxRating` | 整数 | 难度分**区间**（闭区间），可只传一边 |
| `kind` | string | `LOCAL` / `EXTERNAL`（大小写不敏感）；不传表示两类都要。传其他值返回 `400`（`1000`） |
| `page` / `size` | 整数 | 分页，见 [1.4 分页](01-conventions.md#14-分页) |

> `rating` 的刻度随来源而定（例如 Codeforces 800–3500、校内比赛可能没有评分），**跨来源排序不可比**；未设置评分的题目在按 `rating` 筛选时不会命中。

**响应 `data`**

```json
{
  "items": [
    {
      "kind": "LOCAL",
      "id": 12,
      "title": "A+B Problem",
      "difficulty": "EASY",
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
      "difficulty": null,
      "rating": 1200,
      "sourceId": 7,
      "sourceName": "Codeforces",
      "url": "https://codeforces.com/problemset/problem/1/A",
      "submitCount": null,
      "acceptedCount": null,
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
| `difficulty` | 只有 `LOCAL` 有值（`EASY`/`MEDIUM`/`HARD`）；`EXTERNAL` 恒为 `null` |
| `rating` | 数值难度分；可能为 `null`（未设置） |
| `sourceId` / `sourceName` | 来源 id / 名称；未设置来源时都为 `null`（`EXTERNAL` 一定有值） |
| `url` | 只有 `EXTERNAL` 有值（原题链接）；`LOCAL` 恒为 `null` |
| `submitCount` / `acceptedCount` | 只有 `LOCAL` 有值：累计提交次数 / 通过次数；通过率 = `acceptedCount / submitCount`（`submitCount=0` 时不要相除）。`EXTERNAL` 恒为 `null` |
| `authorId` / `authorNickname` | 只有 `LOCAL` 有值：出题人 id / 当前昵称；`EXTERNAL` 恒为 `null` |
| `tags` | 标签数组，两类题目共用标签表 |
| `createdAt` | 录入时间 |

**排序**：按 `createdAt` 倒序（最新在前）；同一时间戳时 `LOCAL` 在 `EXTERNAL` 之后、再按 id 倒序，保证翻页稳定。

**常见错误**：`1000`（`kind` 非法、`rating`/`minRating`/`maxRating` 不是整数、分页参数非法）。

## 10.2 前端渲染建议

- 用一个列表渲染，按 `kind` 切换卡片：`EXTERNAL` 显示 `sourceName + rating + 外链图标`，`LOCAL` 显示 `difficulty + rating + 通过率`（`acceptedCount / submitCount`）；
  > 本接口**不返回** `status`：本地题在列表里恒为已发布。
- 点击 `LOCAL` → `GET /problems/{id}`；点击 `EXTERNAL` → `window.open(url)`（不带 `target="_blank"` 之外的额外跳转逻辑）；
- 筛选栏：来源下拉（按 `sourceId`）、标签下拉（按 `tagId`）、难度区间（`minRating`/`maxRating`）、搜索框（`keyword`）；可再加一个"只看本站 / 只看外部"的 `kind` 开关；
- 选来源或标签后本地题也会参与结果（两者共用来源表、标签表），不需要前端做特殊分支。
