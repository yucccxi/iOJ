# 提交与评测

本文说明提交代码、提交列表和提交详情接口，以及评测状态与逐测试点结果的可见性，读者为前端与外部调用方。判题机的调度与沙箱细节见 [判题设计](../../server/03-judge.md)。

> 返回目录：[前端 API 索引](README.md)

## 1. 提交代码

`POST /api/v1/submissions` ｜ 需要登录

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `problemId` | integer | 是 | — | 题目 id |
| `language` | string | 是 | — | 语言代码，必须取自 `GET /meta/languages` 的 `code` |
| `code` | string | 是 | — | 源代码，不得为空白；长度上限由 `ioj.submission.max-code-length` 配置，默认 65536 字符 |

```json
{
  "problemId": 12,
  "language": "JAVA",
  "code": "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        System.out.println(in.nextLong() + in.nextLong());\n    }\n}\n"
}
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 500,
    "problemId": 12,
    "problemTitle": "A+B Problem",
    "userId": 12,
    "userNickname": "Alice",
    "language": "JAVA",
    "code": "import java.util.Scanner;\n ... \n}\n",
    "status": "PENDING",
    "statusText": "排队中",
    "timeUsedMs": null,
    "memoryUsedKb": null,
    "compileMessage": null,
    "judgeMessage": null,
    "createdAt": "2026-10-30T11:18:05",
    "judgedAt": null,
    "cases": []
  }
}
```

字段含义同第 3 节。提交成功后题目的提交次数计数立即 +1，但评测异步进行。

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 404 | `3001` | 题目不存在 |
| 403 | `3002` | 题目未发布，且当前用户不是作者或管理员 |
| 400 | `3003` | 题目还没有测试数据 |
| 400 | `4002` | 语言枚举合法但服务端未启用 |
| 400 | `4003` | 代码长度超过配置上限 |
| 400 | `1001` | `problemId` / `language` 缺失，或 `code` 为空白 |
| 400 | `1000` | `language` 不是合法枚举，或请求体不是合法 JSON |

**行为说明**

- 评测是异步的：接口返回只代表“已提交成功”，请轮询提交详情，直到状态不再是 `PENDING` / `JUDGING`。
- 重复点击会创建多条提交，前端必须做提交按钮防重复。
- 提交者本人、题目作者、管理员可以查看代码与逐点结果；其他调用方在详情中看到 `code=null`、`cases=[]`。
- 提交目前没有用户级频率限制，不保证幂等。

## 2. 提交列表

`GET /api/v1/submissions` ｜ 公开

用于题目页“最近提交”、个人主页提交记录和评测排队查询。列表不返回代码和逐点结果。

**查询参数**（都可选，条件之间是“且”关系；按提交 id 倒序）

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `problemId` | integer | 否 | — | 只看某道题 |
| `userId` | integer | 否 | — | 只看某个用户 |
| `status` | string | 否 | — | 只看某个状态，例如 `ACCEPTED`、`PENDING`；非法值返回 `1000` |
| `page` / `size` | integer | 否 | `1` / `20` | 分页 |

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": 500,
        "problemId": 12,
        "problemTitle": "A+B Problem",
        "userId": 12,
        "userNickname": "Alice",
        "language": "JAVA",
        "status": "ACCEPTED",
        "statusText": "通过",
        "timeUsedMs": 46,
        "memoryUsedKb": 33116,
        "createdAt": "2026-10-30T11:18:05"
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
| `data.items[].id` | integer | 提交 id，用于查询详情 |
| `data.items[].problemId` / `problemTitle` | integer / string | 题目 id / 标题 |
| `data.items[].userId` / `userNickname` | integer / string | 提交者 id / 当前昵称 |
| `data.items[].language` | string | 提交语言 |
| `data.items[].status` / `statusText` | string | 结果状态 / 中文文案 |
| `data.items[].timeUsedMs` / `memoryUsedKb` | integer / null | 最大耗时（毫秒）/ 内存峰值（KB） |
| `data.items[].createdAt` | string | 提交时间 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1000` | `status` 不是合法枚举；分页/筛选参数类型错误 |

**行为说明**

- 该接口不校验题目是否已发布：用 `?problemId={id}` 查询草稿/待审核/已下架的题，也可能返回提交摘要和 `problemTitle`。未发布题目的“不可见”只约束题面详情。
- 题目被删除时，其提交会级联删除，列表里不会出现“题目已删除”的记录。
- 列表永远不返回 `code`、`compileMessage`、`judgeMessage` 或 `cases`。

## 3. 提交详情

`GET /api/v1/submissions/{id}` ｜ 公开，敏感字段按身份隐藏

| 路径参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | integer | 提交 id |

**不同身份看到的内容**

| 字段 | 提交者本人 / 题目作者 / 管理员 | 其他登录用户 / 未登录 |
| --- | --- | --- |
| 状态、耗时、时间、题目名、提交者昵称、语言 | 正常返回 | 正常返回 |
| `code` | 源代码 | `null` |
| `compileMessage` / `judgeMessage` | 正常返回 | `null` |
| `cases` | 逐点结果 | `[]` |

**本人 / 题目作者 / 管理员看到的响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 500,
    "problemId": 12,
    "problemTitle": "A+B Problem",
    "userId": 12,
    "userNickname": "Alice",
    "language": "JAVA",
    "code": "import java.util.Scanner;\n ... \n}\n",
    "status": "ACCEPTED",
    "statusText": "通过",
    "timeUsedMs": 46,
    "memoryUsedKb": 33116,
    "compileMessage": null,
    "judgeMessage": null,
    "createdAt": "2026-10-30T11:18:05",
    "judgedAt": "2026-10-30T11:18:06",
    "cases": [
      {
        "ordinal": 1,
        "status": "ACCEPTED",
        "statusText": "通过",
        "timeUsedMs": 44,
        "memoryUsedKb": 33116,
        "message": null
      },
      {
        "ordinal": 2,
        "status": "ACCEPTED",
        "statusText": "通过",
        "timeUsedMs": 46,
        "memoryUsedKb": 31060,
        "message": null
      }
    ]
  }
}
```

**其他登录用户 / 未登录看到的响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 500,
    "problemId": 12,
    "problemTitle": "A+B Problem",
    "userId": 12,
    "userNickname": "Alice",
    "language": "JAVA",
    "code": null,
    "status": "ACCEPTED",
    "statusText": "通过",
    "timeUsedMs": 46,
    "memoryUsedKb": 33116,
    "compileMessage": null,
    "judgeMessage": null,
    "createdAt": "2026-10-30T11:18:05",
    "judgedAt": "2026-10-30T11:18:06",
    "cases": []
  }
}
```

**字段含义**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.problemId` / `problemTitle` | integer / string | 题目 id / 标题；题目删除后标题为“已删除的题目” |
| `data.userId` / `userNickname` | integer / string | 提交者 id / 当前昵称；用户注销后显示“已注销用户” |
| `data.language` | string | 提交语言 |
| `data.code` | string / null | 源代码；无权限时为 `null` |
| `data.status` / `statusText` | string | 总状态 / 中文文案，见第 4 节 |
| `data.timeUsedMs` | integer / null | 已执行测试点的最大耗时（毫秒）；未开始、编译错误、CHECKER/SYSTEM 错误时可能为 `null` |
| `data.memoryUsedKb` | integer / null | 内存峰值（KB）；引擎无法统计时为 `null` |
| `data.compileMessage` | string / null | 编译器输出，只有 `COMPILATION_ERROR` 时有值；编译超时为 `Compile Time Exceeded` |
| `data.judgeMessage` | string / null | 系统级/判题器级错误说明；`SYSTEM_ERROR` 与判题器编译/启动失败时可能有值 |
| `data.createdAt` / `judgedAt` | string / null | 提交时间 / 评测完成时间；未完成时 `judgedAt=null` |
| `data.cases` | array | 逐测试点结果；无权限、编译错误、系统错误或判题器准备失败时为 `[]` |

`cases` 每一项：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `ordinal` | integer | 对应测试数据编号，从 1 开始 |
| `status` / `statusText` | string | 该测试点结果 / 中文文案 |
| `timeUsedMs` | integer / null | 该测试点耗时 |
| `memoryUsedKb` | integer / null | 该测试点内存峰值（KB） |
| `message` | string / null | 失败原因，最多约 1024 字符；不会包含期望输出 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 404 | `4001` | 提交记录不存在 |

**行为说明**

- 评测遇到第一个未通过的测试点就停止，所以 `cases` 可能少于测试点总数；答案错误时通常只有一条 `WRONG_ANSWER`。
- 不同引擎的 `message` 文案可能不同（英文固定文案或中文带具体限制），前端只做展示/折叠，不得解析。
- `code`、`compileMessage`、`judgeMessage`、`cases` 的隐藏只按身份判断，不按题目可见性判断。

## 4. 评测状态

| `status` | `statusText` | 含义 | 是否终态 |
| --- | --- | --- | --- |
| `PENDING` | 排队中 | 已提交，等待 Agent 领取 | 否 |
| `JUDGING` | 评测中 | Agent 已领取并持有租约 | 否 |
| `ACCEPTED` | 通过 | 全部测试点通过 | 是 |
| `WRONG_ANSWER` | 答案错误 | 输出与期望不一致，或判题器返回 1/2 | 是 |
| `TIME_LIMIT_EXCEEDED` | 超出时间限制 | 超过 CPU/墙钟限制 | 是 |
| `MEMORY_LIMIT_EXCEEDED` | 超出内存限制 | cgroup OOM 或内存超限 | 是 |
| `OUTPUT_LIMIT_EXCEEDED` | 输出超出限制 | 输出内容过大 | 是 |
| `RUNTIME_ERROR` | 运行错误 | 非零退出码或被信号终止 | 是 |
| `COMPILATION_ERROR` | 编译错误 | 编译失败或编译超时 | 是 |
| `CHECKER_ERROR` | 判题器错误 | 题目自带判题器编译失败、超时、崩溃或返回非法退出码；系统侧问题 | 是 |
| `SYSTEM_ERROR` | 系统错误 | 题目/测试点/语言/沙箱/租约异常 | 是 |

建议配色：`ACCEPTED` 绿色；`WRONG_ANSWER` / `TIME_LIMIT_EXCEEDED` / `RUNTIME_ERROR` / `COMPILATION_ERROR` 红色；`PENDING` / `JUDGING` 灰色；`CHECKER_ERROR` / `SYSTEM_ERROR` 橙色并提示稍后重试。

## 5. 轮询评测结果

```js
const BASE = "http://localhost:8080/api/v1";

async function waitForJudge(id, token, onUpdate) {
  for (let i = 0; i < 60; i += 1) {
    const response = await fetch(`${BASE}/submissions/${id}`, {
      headers: { Authorization: `Bearer ${token}` },
    }).then((r) => r.json());

    const data = response.data;
    onUpdate?.(data);

    if (!["PENDING", "JUDGING"].includes(data.status)) return data;
    await new Promise((resolve) => setTimeout(resolve, 800));
  }
  throw new Error("评测时间较长，请稍后到“我的提交”查看");
}
```

轮询建议：

- 间隔 800-1000 ms，最多 30-60 秒；超时后提示用户到提交列表查看。
- 不得并发轮询同一条提交；页面隐藏时暂停。
- 收到 `CHECKER_ERROR` / `SYSTEM_ERROR` 不得提示“代码错误”，必须提示评测侧问题并建议稍后重试。

## 相关文档

- [前端 API 索引](README.md)
- [题目与测试数据](03-problems.md)
- [判题设计](../../server/03-judge.md)
- [API 通用约定](../README.md)
