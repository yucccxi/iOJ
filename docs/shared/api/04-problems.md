# 4. 题库

> 返回目录：[iOJ 前端接口文档（v1）](README.md)

## 4.1 题目列表

`GET /problems` ｜ 公开

题库首页使用，只返回**已发布**的题目，按 id 倒序（等价于最新创建的在前）。列表只给概览信息，不包含题面正文。

**查询参数**（都可选，同时传表示"且"关系）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 按**标题**模糊搜索；不搜题面、标签；空字符串等于不筛选 |
| `difficulty` | string | 难度：`EASY` / `MEDIUM` / `HARD`；传其他值会报 `1000` |
| `tagId` | 整数 | 标签 id（不是标签名，id 从标签列表接口获取）；没有该标签时返回空列表，不报错 |
| `page` / `size` | 整数 | 分页，见 [1.4 分页](01-conventions.md#14-分页) |

**响应 `data`**

```json
{
  "items": [
    {
      "id": 12,
      "title": "A+B Problem",
      "difficulty": "EASY",
      "status": "PUBLISHED",
      "submitCount": 30,
      "acceptedCount": 18,
      "authorId": 1,
      "authorNickname": "Alice",
      "tags": [{ "id": 3, "name": "数学" }],
      "createdAt": "2026-10-30T11:18:05"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 20
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 题目 id，详情页与提交都用它 |
| `title` | 标题 |
| `difficulty` | 难度枚举 |
| `status` | 列表里恒为 `PUBLISHED` |
| `submitCount` | 累计提交次数（含未通过） |
| `acceptedCount` | 累计通过次数；通过率可用 `acceptedCount / submitCount` 计算，注意 `submitCount=0` 时不要相除 |
| `authorId` / `authorNickname` | 出题人 id / 当前昵称 |
| `tags` | 标签数组，元素为 `{ id, name }`；`id` 可直接用于 `tagId` 筛选 |
| `createdAt` | 创建时间 |

**常见错误**：`1000`（`difficulty` 或 `page`/`size` 传了非法值）。

## 4.2 我上传的题目

`GET /problems/mine` ｜ 需要登录

用于"我的题目"页面，返回当前用户上传的**全部**题目，包括草稿、待审核、已下架。

**查询参数**

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 按标题模糊搜索 |
| `page` / `size` | 整数 | 分页 |

> 该接口只支持按标题搜索和分页，不支持难度、标签筛选。

**响应 `data`**：结构与 4.1 相同，但 `status` 可能是 `DRAFT` / `PENDING_REVIEW` / `PUBLISHED` / `HIDDEN`：

```json
{
  "items": [
    {
      "id": 13, "title": "待审核的题", "difficulty": "EASY", "status": "PENDING_REVIEW",
      "submitCount": 0, "acceptedCount": 0,
      "authorId": 12, "authorNickname": "Alice",
      "tags": [{ "id": 1, "name": "入门" }],
      "createdAt": "2026-10-30T11:21:21"
    }
  ],
  "total": 1, "page": 1, "size": 20
}
```

`status` 是这张页面的核心字段，建议用不同颜色/文案区分：草稿、待审核、已发布、已下架。

> 注意：**当前版本没有任何接口会产生 `DRAFT`**。上传只会得到 `PUBLISHED` 或 `PENDING_REVIEW`（见 4.4），管理员只能把题目改成 `PUBLISHED` / `HIDDEN`（见 [8.5 上架 / 下架题目](08-admin.md#85-上架--下架题目)）。`DRAFT` 是为将来"保存草稿"预留的枚举值，前端可以不为它单独做页面，但状态映射表里保留一个默认分支更安全（后端以后可能启用）。

## 4.3 题目详情

`GET /problems/{id}` ｜ 公开（未发布题目仅作者/管理员可见）

| 路径参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | 整数 | 题目 id |

**可见性**

- 已发布（`PUBLISHED`）：所有人可看；
- 草稿 / 待审核 / 已下架：只有作者和管理员可看，其他人返回 `403`（`3002`）。

**响应 `data`**

```json
{
  "id": 12,
  "title": "A+B Problem",
  "difficulty": "EASY",
  "description": "输入两个整数 a、b，输出它们的和。",
  "inputFormat": "一行两个整数",
  "outputFormat": "一个整数",
  "hint": "注意使用 64 位整数",
  "source": "2026 新生赛",
  "timeLimitMs": 1000,
  "memoryLimitMb": 256,
  "status": "PUBLISHED",
  "authorId": 1,
  "authorNickname": "Alice",
  "tags": [{ "id": 3, "name": "数学" }],
  "samples": [
    { "ordinal": 1, "input": "1 2\n", "output": "3\n", "explanation": "1+2=3" }
  ],
  "submitCount": 30,
  "acceptedCount": 18,
  "solved": true,
  "createdAt": "2026-10-30T11:18:05",
  "updatedAt": "2026-10-30T11:18:32",
  "hasChecker": false
}
```

| 字段 | 说明 |
| --- | --- |
| `description` | 题面正文（Markdown 或纯文本），渲染前请做 XSS 清洗 |
| `inputFormat` / `outputFormat` | 输入/输出格式说明，可能为 `null` |
| `hint` | 提示，可能为 `null` |
| `source` | 题目来源，可能为 `null` |
| `timeLimitMs` | 时间限制（毫秒），用于展示与提交前提示 |
| `memoryLimitMb` | 内存限制（MB） |
| `status` | `DRAFT` / `PENDING_REVIEW` / `PUBLISHED` / `HIDDEN` |
| `tags` | 标签数组 |
| `samples` | 展示样例，`ordinal` 从 1 开始；`explanation` 可能为 `null` |
| `submitCount` / `acceptedCount` | 同 4.1 |
| `solved` | **当前查看者是否已通过该题**。未登录时恒为 `false`；已登录且有通过记录才为 `true` |
| `hasChecker` | 该题是否配置了自定义判题器（Special Judge）。**只给布尔值，永远不返回源码**，源码请用 [4.7 判题器](#47-判题器special-judge) 的接口（仅作者/管理员） |
| `createdAt` / `updatedAt` | 创建时间 / 最后更新时间 |

> `solved=false` 有两种可能：未登录，或确实没通过。请不要用它判断"没做过"，先判断是否有登录态。

**常见错误**：`3001`（题目不存在）、`3002`（题目未发布/已下架）。

## 4.4 上传题目

`POST /problems` ｜ 需要登录

任何登录用户都可以上传题目。上传后的初始状态由**平台设置和身份**决定，以响应里的 `status` 为准：

| 情况 | 初始状态 |
| --- | --- |
| 管理员上传 | `PUBLISHED`（直接进入题库） |
| 普通用户上传，平台默认（自动发布） | `PUBLISHED` |
| 普通用户上传，平台改为人工审核 | `PENDING_REVIEW`（等待管理员审核，此时只有你自己和管理员能看到） |

> 前端不要写死"上传 → 待审核"，请直接读响应里的 `status` 展示。多数部署是自动发布，普通用户上传后立刻就能在题库看到自己的题。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | string | 是 | 标题，≤128 字；首尾空格会被去掉 |
| `difficulty` | string | 是 | `EASY` / `MEDIUM` / `HARD` |
| `description` | string | 是 | 题面正文，≤200000 字，支持 Markdown |
| `inputFormat` | string | 否 | 输入格式说明，≤20000 字 |
| `outputFormat` | string | 否 | 输出格式说明，≤20000 字 |
| `hint` | string | 否 | 提示，≤20000 字 |
| `source` | string | 否 | 来源，≤128 字 |
| `timeLimitMs` | 整数 | 否 | 时间限制，100-30000 毫秒，默认 1000 |
| `memoryLimitMb` | 整数 | 否 | 内存限制，16-1024 MB，默认 256 |
| `tags` | string[] | 否 | 最多 10 个，每个 1-32 字（字母、数字、`_+#.-`，中文等非 ASCII 字母也算）；会自动 trim、去重、丢弃空白项；不存在的新标签会自动创建 |
| `samples` | object[] | 否 | 最多 10 个题面样例，见下 |

`samples` 每一项：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `input` | string | 是 | 样例输入，≤10240 字，可为空字符串 |
| `output` | string | 是 | 样例输出，≤10240 字，可为空字符串 |
| `explanation` | string | 否 | 样例解释，≤10240 字 |

```json
{
  "title": "A+B Problem",
  "difficulty": "EASY",
  "description": "输入两个整数 a、b，输出它们的和。",
  "inputFormat": "一行两个整数",
  "outputFormat": "一个整数",
  "timeLimitMs": 1000,
  "memoryLimitMb": 256,
  "tags": ["入门", "数学"],
  "samples": [{ "input": "1 2\n", "output": "3\n", "explanation": "1+2=3" }]
}
```

**响应 `data`**：完整题目详情（结构同 4.3），其中 `id` 是新题目 id，`status` 是发布结果。

> 题目和"判题用的测试数据"是两回事：本接口不接收测试数据。若题目还没有测试数据，用户可以打开题面，但提交会返回 `3003`。

**常见错误**：`1001`（字段不合法）、`1002`（未登录）。

## 4.5 编辑题目

`PUT /problems/{id}` ｜ 仅题目作者或管理员

**这是"整体替换"**：请求体必须按 4.4 的完整结构提交，未传的可选字段会被清空或恢复默认值。

| 字段 | 不传的后果 |
| --- | --- |
| `title` / `difficulty` / `description` | 必填，缺失会返回 `1001` |
| `inputFormat` / `outputFormat` / `hint` / `source` | 被清空为 `null` |
| `timeLimitMs` / `memoryLimitMb` | 恢复默认值 1000 / 256 |
| `tags` | 传 `[]` 或不传会**清空标签** |
| `samples` | 传 `[]` 或不传会**清空样例** |

> 该接口**不会改变题目的发布状态**；上架/下架请使用管理员的审核接口（8.5）。

**响应 `data`**：更新后的题目详情。

**常见错误**：`3001`（题目不存在）、`1003`（不是作者/管理员）、`1001`（字段不合法）。

## 4.6 删除题目

`DELETE /problems/{id}` ｜ 仅题目作者或管理员

删除后**不可恢复**，并且会一并删除该题的测试数据与全部提交记录。前端应二次确认。

**响应**：`{ "code": 0, "message": "ok", "data": null }`

**常见错误**：`3001`（题目不存在）、`1003`（不是作者/管理员）。

## 4.7 判题器（Special Judge）

> 有些题目的答案不唯一，由出题者上传一段 C++ 判题器源码来判断输出是否可接受；没有判题器的题目按逐行文本比对答案。
> 这对前端是透明的：题目详情只给出 `hasChecker`，前端据它决定是否显示判题器入口。
> 判题器源码怎么写、退出码怎么约定，见 [special-judge.md](../special-judge.md)（出题者视角）。
> 两个接口都**只有题目作者和管理员**可以调用：未登录返回 `401`，其他登录用户返回 `403`。

### 4.7.1 查看判题器源码

`GET /problems/{id}/checker` ｜ 作者/管理员

```json
{ "code": 0, "message": "ok", "data": { "code": "#include <cstdio>\n\nint main() {\n    ...\n}\n" } }
```

| 字段 | 说明 |
| --- | --- |
| `code` | 判题器源码；题目未配置时为**空字符串**（不是 `null`） |

**常见错误**：`3001`（题目不存在）、`1003`（不是作者/管理员）。

### 4.7.2 上传 / 清空判题器

`PUT /problems/{id}/checker` ｜ 作者/管理员

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `code` | string | 否 | C++ 源码，≤ 65536 字符；传 `""`、纯空白、`null` 或不传都表示**清空判题器**，恢复内置比对 |

```json
{ "code": "#include <cstdio>\nint main() { return 0; }\n" }
```

**响应**：`{ "code": 0, "message": "ok", "data": null }`

**效果**

- 保存后题目详情的 `hasChecker` 变为 `true`，清空后变回 `false`；
- 只影响之后的评测，已出结果的提交不会改变；
- 本接口只改判题器，不动题面、标签、样例和发布状态；反过来 `PUT /problems/{id}` 也不会改动判题器，所以判题器要有独立的表单与提交按钮。

> 判题器本身不可用时，该题之后的提交会得到 `CHECKER_ERROR`（响应字段见 [6.2 提交详情](06-submissions.md#62-提交详情)）——这是判题侧的问题，不是选手代码的问题。

**常见错误**：`3001`（题目不存在）、`1003`（不是作者/管理员）、`1001`（源码超过 65536 字符）。
