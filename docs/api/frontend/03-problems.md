# 题目与测试数据

本文说明题目详情、出题 CRUD、判题器和隐藏测试点接口，读者为前端与出题者。统一题库搜索见 [统一题库与参考数据](02-catalog.md)，提交与评测见 [提交与评测](04-submissions.md)。

> 返回目录：[前端 API 索引](README.md)

> 测试数据是判题用的隐藏数据，和题面里的“样例”是两套内容。所有测试数据接口（包括查看）只有题目作者和管理员可以调用：未登录返回 `401`，其他登录用户返回 `403`。

## 1. 我的题目

`GET /api/v1/problems/mine` ｜ 需要登录

返回当前用户上传的全部题目，包括草稿、待审核、已发布和已下架，按 id 倒序。

**查询参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `keyword` | string | 否 | — | 按标题模糊搜索 |
| `page` / `size` | integer | 否 | `1` / `20` | 分页 |

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": 13,
        "title": "待审核的题",
        "difficulty": "EASY",
        "rating": 800,
        "status": "PENDING_REVIEW",
        "submitCount": 0,
        "acceptedCount": 0,
        "authorId": 12,
        "authorNickname": "Alice",
        "tags": [{ "id": 1, "name": "入门" }],
        "createdAt": "2026-10-30T11:21:21"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 20
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.items[].id` | integer | 题目 id |
| `data.items[].title` | string | 标题 |
| `data.items[].difficulty` | string | `EASY` / `MEDIUM` / `HARD` |
| `data.items[].rating` | integer | 数值难度分；未评分返回 `-1` |
| `data.items[].status` | string | `DRAFT` / `PENDING_REVIEW` / `PUBLISHED` / `HIDDEN` |
| `data.items[].submitCount` / `acceptedCount` | integer | 提交数 / 通过数 |
| `data.items[].authorId` / `authorNickname` | integer / string | 作者 id / 当前昵称 |
| `data.items[].tags` | array | 标签 `{ id, name }`；可能为 `[]` |
| `data.items[].createdAt` | string | 创建时间 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 401 | `1002` | 未登录或 token 失效 |

> 当前版本没有任何接口会产生 `DRAFT`。上传只会得到 `PUBLISHED` 或 `PENDING_REVIEW`；管理员只能把题目改成 `PUBLISHED` / `HIDDEN`。`DRAFT` 是为将来“保存草稿”预留的枚举值。

## 2. 题目详情

`GET /api/v1/problems/{id}` ｜ 公开（未发布题目仅作者/管理员可见）

| 路径参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | integer | 题目 id |

**可见性**

- `PUBLISHED`：所有人可看。
- `PENDING_REVIEW` / `HIDDEN` / `DRAFT`：只有作者和管理员可看，其他人返回 `403` / `3002`。

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 12,
    "title": "A+B Problem",
    "difficulty": "EASY",
    "rating": 800,
    "description": "输入两个整数 a、b，输出它们的和。",
    "inputFormat": "一行两个整数",
    "outputFormat": "一个整数",
    "hint": "注意使用 64 位整数",
    "scopeId": 16,
    "scopeName": "LOCAL",
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
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.id` | integer | 题目 id |
| `data.title` | string | 标题 |
| `data.difficulty` | string | 档位难度 `EASY` / `MEDIUM` / `HARD` |
| `data.rating` | integer | 数值难度分 0-5000；未评分返回 `-1` |
| `data.description` | string | 题面正文，Markdown 或纯文本；渲染前请做 XSS 清洗 |
| `data.inputFormat` / `outputFormat` | string / null | 输入/输出格式说明，可能为 `null` |
| `data.hint` | string / null | 提示，可能为 `null` |
| `data.scopeId` / `scopeName` | integer / string | 本地题固定为 `LOCAL`，两者都不为 `null` |
| `data.timeLimitMs` / `memoryLimitMb` | integer | 时间限制（毫秒）/ 内存限制（MB） |
| `data.status` | string | 题目状态 |
| `data.authorId` / `authorNickname` | integer / string | 作者 id / 当前昵称；作者注销时显示“已注销用户” |
| `data.tags` | array | 标签 `{ id, name }`；可能为 `[]` |
| `data.samples` | array | 题面样例；可能为 `[]`，`ordinal` 从 1 开始 |
| `data.samples[].input` / `output` | string | 样例输入/输出，可为空字符串 |
| `data.samples[].explanation` | string / null | 样例解释 |
| `data.submitCount` / `acceptedCount` | integer | 累计提交次数 / 通过次数；通过率 = 通过数 / 提交数 |
| `data.solved` | boolean | 当前查看者是否已通过；未登录恒为 `false` |
| `data.createdAt` / `updatedAt` | string | 创建 / 最后更新时间 |
| `data.hasChecker` | boolean | 是否配置了自定义判题器；源码不在此响应中 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 404 | `3001` | 题目不存在 |
| 403 | `3002` | 题目未发布/已下架，且当前用户不是作者或管理员 |

**行为说明**

- `solved=false` 可能是未登录，也可能确实没通过；不要用它判断“没做过”。
- 测试点内容、判题器源码不会出现在详情响应里；`hasChecker` 只是布尔值。
- 题面/样例是公开内容，隐藏测试数据请用第 7 节接口。

## 3. 创建题目

`POST /api/v1/problems` ｜ 需要登录

任何登录用户都可以上传题目。初始状态由身份和平台配置决定，以响应里的 `status` 为准：

| 情况 | 初始状态 |
| --- | --- |
| 管理员上传 | `PUBLISHED` |
| 普通用户上传，`auto-publish=true` | `PUBLISHED` |
| 普通用户上传，`auto-publish=false` | `PENDING_REVIEW` |

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `title` | string | 是 | — | 标题，≤128 字；首尾空格会去掉 |
| `difficulty` | string | 是 | — | `EASY` / `MEDIUM` / `HARD`；本地题不接受 `UNKNOWN` |
| `rating` | integer | 否 | `null` | 数值难度分 0-5000；不传则不设置，不会从 `difficulty` 推算 |
| `description` | string | 是 | — | 题面，≤200000 字，支持 Markdown |
| `inputFormat` | string | 否 | `null` | 输入格式说明，≤20000 字 |
| `outputFormat` | string | 否 | `null` | 输出格式说明，≤20000 字 |
| `hint` | string | 否 | `null` | 提示，≤20000 字 |
| `timeLimitMs` | integer | 否 | `1000` | 100-30000 毫秒 |
| `memoryLimitMb` | integer | 否 | `256` | 16-1024 MB |
| `tagIds` | integer[] | 否 | `[]` | 最多 10 个已存在标签 id；重复 id 去重；不存在的 id 返回 `1000` |
| `samples` | object[] | 否 | `[]` | 最多 10 个题面样例 |

`tagIds` 只能选已有标签，服务端不会自动创建；标签由管理员维护。`scopeId` 不是请求字段：本地题一律使用保留 scope `LOCAL`，即使传了也会被忽略。

`samples` 每一项：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `input` | string | 是 | — | 样例输入，≤10240 字，可为空字符串 |
| `output` | string | 是 | — | 样例输出，≤10240 字，可为空字符串 |
| `explanation` | string | 否 | `null` | 样例解释，≤10240 字 |

```json
{
  "title": "A+B Problem",
  "difficulty": "EASY",
  "rating": 800,
  "description": "输入两个整数 a、b，输出它们的和。",
  "inputFormat": "一行两个整数",
  "outputFormat": "一个整数",
  "timeLimitMs": 1000,
  "memoryLimitMb": 256,
  "tagIds": [1, 3],
  "samples": [
    { "input": "1 2\n", "output": "3\n", "explanation": "1+2=3" }
  ]
}
```

**成功响应**

结构同 2 的题目详情；`id` 是新题目 id，`status` 是发布结果。

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1000` | 本地题使用 `UNKNOWN` 难度；标签 id 不存在 |
| 400 | `1001` | 字段缺失、类型错误或超出长度/范围 |
| 401 | `1002` | 未登录或 token 失效 |

**行为说明**

- 本接口不接收测试数据；没有测试点的题可以打开题面，但提交返回 `3003`。
- 创建成功后返回完整详情，包含 `scopeId`、`status`、时间等只读字段。
- 题目作者记录为当前登录用户。

## 4. 全量更新题目

`PUT /api/v1/problems/{id}` ｜ 仅题目作者或管理员

**这是整体替换**：请求体按第 3 节的完整结构提交，未传的可选字段会被清空或恢复默认值。

| 字段 | 不传的后果 |
| --- | --- |
| `title` / `difficulty` / `description` | 必填，缺失返回 `1001` |
| `rating` | 清空为 `NULL`（响应返回 `-1`），不会用 `difficulty` 反推 |
| `inputFormat` / `outputFormat` / `hint` | 清空为 `null` |
| `timeLimitMs` / `memoryLimitMb` | 恢复默认值 `1000` / `256` |
| `tagIds` | 传 `[]` 或不传会清空标签 |
| `samples` | 传 `[]` 或不传会清空样例 |

该接口不会改变：

- 发布状态（上架/下架使用管理员的 `PATCH /admin/problems/{id}/status`）。
- 自定义判题器（使用 `PUT /problems/{id}/checker`）。
- 题目作者、提交数、通过数、创建时间。
- scope（本地题固定为 `LOCAL`）。

**响应**：更新后的题目详情。

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 404 | `3001` | 题目不存在 |
| 403 | `1003` | 不是题目作者，也不是管理员 |
| 400 | `1000` | `UNKNOWN` 难度或标签 id 不存在 |
| 400 | `1001` | 字段缺失、类型错误或超出长度/范围 |
| 401 | `1002` | 未登录或 token 失效 |

## 5. 删除题目

`DELETE /api/v1/problems/{id}` ｜ 仅题目作者或管理员

删除后不可恢复，并会一并删除该题的样例、标签关联、测试数据、提交记录和逐测试点结果。前端应二次确认。

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 404 | `3001` | 题目不存在 |
| 403 | `1003` | 不是作者或管理员 |
| 401 | `1002` | 未登录或 token 失效 |

## 6. 判题器（Special Judge）

有些题目的答案不唯一，由出题者上传一段 C++ 判题器源码判断输出是否可接受；没有判题器的题目按逐行文本比对。题目详情只给 `hasChecker`，源码只对作者与管理员可见。

判题器的编写约定、退出码和本地测试见 [自定义判题器](../../server/08-special-judge.md)。

### 6.1 查看判题器源码

`GET /api/v1/problems/{id}/checker` ｜ 作者/管理员

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "code": "#include <cstdio>\n\nint main() {\n    return 0;\n}\n"
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.code` | string | 判题器源码；题目未配置时为**空字符串**，不是 `null` |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 404 | `3001` | 题目不存在 |
| 403 | `1003` | 不是作者或管理员 |
| 401 | `1002` | 未登录或 token 失效 |

### 6.2 上传 / 清空判题器

`PUT /api/v1/problems/{id}/checker` ｜ 作者/管理员

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `code` | string | 否 | `null` | C++ 源码，≤65536 字符；`""`、纯空白、`null` 或不传都表示清空，恢复内置比对 |

```json
{
  "code": "#include <cstdio>\nint main() { return 0; }\n"
}
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 404 | `3001` | 题目不存在 |
| 403 | `1003` | 不是作者或管理员 |
| 400 | `1001` | 源码超过 65536 字符 |
| 401 | `1002` | 未登录或 token 失效 |

**行为说明**

- 保存后题目详情 `hasChecker=true`；清空后变回 `false`。
- 只影响之后的评测，已出结果的提交不会改变。
- 本接口只改判题器，不动题面、标签、样例和发布状态；`PUT /problems/{id}` 也不会改判题器，所以判题器要有独立表单与提交按钮。
- 判题器不可用时，该题之后的提交会得到 `CHECKER_ERROR`，这是评测侧问题，不是选手代码问题。

## 7. 测试数据

测试数据是判题用的隐藏数据，和题面样例是两套内容。所有接口（包括查看）都只有题目作者和管理员可以调用。

公共路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `problemId` | integer | 题目 id |

本章共用的失败情况：

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 401 | `1002` | 未登录 |
| 403 | `1003` | 已登录但不是该题作者，也不是管理员 |
| 404 | `3001` | `problemId` 对应的题目不存在 |
| 404 | `3004` | `testcaseId` 不存在或不属于该 `problemId` |
| 400 | `3005` | 单题测试数据数量超过上限（默认 100 条） |
| 400 | `1000` | 单条 `input`/`output` 的 UTF-8 字节数超过上限（默认 4 MiB）；路径参数不是整数 |
| 400 | `1001` | 缺少 `input` / `output` |

### 7.1 测试数据列表

`GET /api/v1/problems/{problemId}/testcases` ｜ 作者/管理员

只返回基本信息和字节数，不返回内容。

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "id": 101, "ordinal": 1, "inputBytes": 4, "outputBytes": 2, "createdAt": "2026-10-30T11:18:05" },
    { "id": 102, "ordinal": 2, "inputBytes": 8, "outputBytes": 3, "createdAt": "2026-10-30T11:18:05" }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data[].id` | integer | 测试数据 id |
| `data[].ordinal` | integer | 编号，从 1 开始；与提交结果 `cases[].ordinal` 对应 |
| `data[].inputBytes` / `outputBytes` | integer | 输入/输出的 UTF-8 字节数，不是字符数 |
| `data[].createdAt` | string | 创建时间 |

列表按 `ordinal` 升序；删除中间数据后编号可能不连续。

### 7.2 查看单条测试数据

`GET /api/v1/problems/{problemId}/testcases/{testcaseId}` ｜ 作者/管理员

| 路径参数 | 类型 | 说明 |
| --- | --- | --- |
| `testcaseId` | integer | 测试数据 id，必须属于 `problemId` |

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 101,
    "ordinal": 1,
    "input": "1 2\n",
    "output": "3\n"
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.input` | string | 标准输入原文，保留换行 |
| `data.output` | string | 期望输出原文 |

### 7.3 追加一条测试数据

`POST /api/v1/problems/{problemId}/testcases` ｜ 作者/管理员

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `input` | string | 是 | — | 标准输入，UTF-8 字节数 ≤ 上限；可为空字符串，不能为 `null` |
| `output` | string | 是 | — | 期望输出，限制同上；可为空字符串 |

```json
{
  "input": "1 2\n",
  "output": "3\n"
}
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 103,
    "ordinal": 3,
    "inputBytes": 4,
    "outputBytes": 2,
    "createdAt": "2026-10-30T11:20:00"
  }
}
```

编号自动取“当前最大编号 + 1”；单题最多 100 条（默认），超出返回 `3005`。

### 7.4 覆盖全部测试数据

`PUT /api/v1/problems/{problemId}/testcases` ｜ 作者/管理员

请求体是数组，**数组顺序就是编号顺序**：

```json
[
  { "input": "1 2\n", "output": "3\n" },
  { "input": "100 -50\n", "output": "50\n" }
]
```

- 会替换该题原来的全部测试数据，编号从 1 重新排。
- 传 `[]` 表示清空；清空后该题无法提交/上架（返回 `3003`）。
- 单次请求最多 100 条；每个元素的 `input`/`output` 都不能为 `null`。
- 单个 `input`/`output` 的 UTF-8 字节数不能超过 `ioj.problem.max-testcase-bytes`（默认 4 MiB）。

**成功响应**：替换后的列表，结构同 7.1。

**失败情况**：`1001`（数组元素缺少字段）、`1000`（字节数超限）、`3005`（数量超限）。

### 7.5 删除一条测试数据

`DELETE /api/v1/problems/{problemId}/testcases/{testcaseId}` ｜ 作者/管理员

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

删除后其他数据的编号保持不变；不会自动重排。

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 404 | `3004` | 测试数据不存在或不属于该题 |

## 相关文档

- [前端 API 索引](README.md)
- [统一题库与参考数据](02-catalog.md)
- [提交与评测](04-submissions.md)
- [自定义判题器](../../server/08-special-judge.md)
