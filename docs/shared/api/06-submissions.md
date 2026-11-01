# 6. 提交与评测

> 返回目录：[iOJ 前端接口文档（v1）](README.md)

## 6.1 提交代码

`POST /submissions` ｜ 需要登录

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `problemId` | 整数 | 是 | 题目 id |
| `language` | string | 是 | 语言代码，必须取自"可选语言"接口返回的 `code` |
| `code` | string | 是 | 源代码，≤ 上限（默认 65536 字符，由平台配置），超长返回 `4003`；Java 请使用 `public class Main` |

```json
{
  "problemId": 12,
  "language": "JAVA",
  "code": "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        System.out.println(in.nextLong() + in.nextLong());\n    }\n}\n"
}
```

**响应 `data`**：刚创建的提交详情，此时 `status` 为 `PENDING`（排队中）：

```json
{
  "id": 500, "problemId": 12, "problemTitle": "A+B Problem",
  "userId": 12, "userNickname": "Alice", "language": "JAVA",
  "code": "import java.util.Scanner;\n ... \n}\n",
  "status": "PENDING", "statusText": "排队中",
  "timeUsedMs": null, "memoryUsedKb": null,
  "compileMessage": null, "judgeMessage": null,
  "createdAt": "2026-10-30T11:18:05", "judgedAt": null,
  "cases": []
}
```

**评测是异步的**：接口返回只代表"已提交成功"。请轮询提交详情（见 6.5），直到 `status` 不再是 `PENDING` / `JUDGING`。
通常 1-3 秒内出结果；重复点击会创建多条提交，提交按钮请做防重复处理。

**常见错误**

| HTTP | code | 场景 |
| --- | --- | --- |
| 404 | 3001 | 题目不存在 |
| 403 | 3002 | 题目未发布，且你不是作者/管理员 |
| 400 | 3003 | 题目还没有测试数据 |
| 400 | 1001 | 字段缺失或为空 |
| 400 | 4003 | 代码超过上限（默认 65536 字符，由平台配置） |
| 400 | 4002 / 1000 | 语言不受支持（请使用语言列表里的 `code`）；`4002` 是枚举合法但服务端未启用，`1000` 是枚举本身不合法 |

## 6.2 提交详情

`GET /submissions/{id}` ｜ 公开，但敏感字段按身份隐藏

| 路径参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | 整数 | 提交 id |

**不同身份看到的内容不同**：

| 字段 | 提交者本人 / 题目作者 / 管理员 | 其他登录用户 / 未登录 |
| --- | --- | --- |
| 状态、耗时、时间、题目名、提交者昵称、语言 | ✅ 正常返回 | ✅ 正常返回 |
| `code`（源代码） | ✅ | `null` |
| `compileMessage`、`judgeMessage` | ✅ | `null` |
| `cases`（逐个测试点结果） | ✅ | `[]` |

**本人 / 题目作者 / 管理员看到的内容**

```json
{
  "id": 500, "problemId": 12, "problemTitle": "A+B Problem",
  "userId": 12, "userNickname": "Alice", "language": "JAVA",
  "code": "import java.util.Scanner;\n ... \n}\n",
  "status": "ACCEPTED", "statusText": "通过",
  "timeUsedMs": 46, "memoryUsedKb": 33116,
  "compileMessage": null, "judgeMessage": null,
  "createdAt": "2026-10-30T11:18:05", "judgedAt": "2026-10-30T11:18:06",
  "cases": [
    { "ordinal": 1, "status": "ACCEPTED", "statusText": "通过", "timeUsedMs": 44, "memoryUsedKb": 33116, "message": null },
    { "ordinal": 2, "status": "ACCEPTED", "statusText": "通过", "timeUsedMs": 46, "memoryUsedKb": 31060, "message": null }
  ]
}
```

**其他登录用户 / 未登录看到的内容**

```json
{
  "id": 500, "problemId": 12, "problemTitle": "A+B Problem",
  "userId": 12, "userNickname": "Alice", "language": "JAVA",
  "code": null,
  "status": "ACCEPTED", "statusText": "通过",
  "timeUsedMs": 46, "memoryUsedKb": 33116,
  "compileMessage": null, "judgeMessage": null,
  "createdAt": "2026-10-30T11:18:05", "judgedAt": "2026-10-30T11:18:06",
  "cases": []
}
```

**字段含义**

| 字段 | 说明 |
| --- | --- |
| `problemId` / `problemTitle` | 题目 id / 标题 |
| `userId` / `userNickname` | 提交者 id / 当前昵称 |
| `language` | 提交使用的语言 |
| `code` | 源代码；无权限时为 `null` |
| `status` / `statusText` | 状态枚举 / 中文文案，见 6.4 |
| `timeUsedMs` | 已执行测试点中的**最大耗时**（毫秒）；尚未开始评测、编译错误、`SYSTEM_ERROR`、`CHECKER_ERROR`（判题器在编译/启动阶段失败）时为 `null` |
| `memoryUsedKb` | 内存峰值（KB）；该次评测未提供该项统计时为 `null`，前端需判空 |
| `compileMessage` | 编译器输出，只有 `COMPILATION_ERROR` 时有值；编译超时时为固定英文说明 `Compile Time Exceeded` |
| `judgeMessage` | 系统级/判题器级错误说明：`SYSTEM_ERROR` 与 `CHECKER_ERROR`（判题器编译或启动失败）时有值，其他情况为 `null`。内容可能较长，前端请做折行/截断 |
| `createdAt` / `judgedAt` | 提交时间 / 评测完成时间；未完成时 `judgedAt` 为 `null` |
| `cases` | 逐个测试点的结果；无权限、编译错误、`SYSTEM_ERROR`、判题器编译/启动失败（`CHECKER_ERROR`）时为 `[]` |

**`cases` 每一项**

| 字段 | 说明 |
| --- | --- |
| `ordinal` | 对应测试数据的编号（从 1 开始） |
| `status` / `statusText` | 该测试点的结果 |
| `timeUsedMs` | 该测试点耗时 |
| `memoryUsedKb` | 该测试点的内存峰值（KB）；未提供该项统计时为 `null` |
| `message` | 该测试点的失败原因，供展示用，**不要当作文案解析**。常见取值：超时 `Time Limit Exceeded`、超内存 `Memory Limit Exceeded`、输出超限 `Output Limit Exceeded`；运行错误给出程序 stderr（程序自身无输出时为 `Killed by signal` / `Non-zero exit code`）；答案错误一般为 `null`。不同部署的文案可能不同（例如中文且带具体数值的 `超过时间限制 1000ms`），前端只做展示/折叠即可。不会包含期望输出 |

> 评测遇到第一个未通过的测试点就会停止，所以 `cases` 可能少于测试数据总数。
> 例如答案错误时通常只有 1 条 `WRONG_ANSWER`。

> `message` 长度上限约 **1024 字符**：内容过长时该条记录可能没有说明，前端展示时请自行截断，不要假设它一定有值。

## 6.3 提交列表

`GET /submissions` ｜ 公开

用于题目页的"最近提交"、个人主页的提交记录、评测排队查询。列表**不返回代码和逐点结果**。

**查询参数**（都可选，"且"关系；按提交 id 倒序，即最新的在前）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `problemId` | 整数 | 只看某道题的提交 |
| `userId` | 整数 | 只看某个用户的提交 |
| `status` | string | 只看某个状态，例如 `ACCEPTED`、`PENDING`；非法值返回 `1000` |
| `page` / `size` | 整数 | 分页，见 [1.4 分页](01-conventions.md#14-分页) |

> 该接口**不校验题目是否已发布**：用 `?problemId={id}` 查 `DRAFT` / `PENDING_REVIEW` / `HIDDEN` 的题目时，任何人（含未登录）都能拿到提交摘要，并且能看到 `problemTitle`。详见 [1.6 谁可以调用、能看到什么](01-conventions.md#16-谁可以调用能看到什么) 末尾的说明。
> 题目被删除时它的提交会被级联删除，所以列表里不会出现"题目已删除"的记录。

**响应 `data`**

```json
{
  "items": [
    {
      "id": 500, "problemId": 12, "problemTitle": "A+B Problem",
      "userId": 12, "userNickname": "Alice", "language": "JAVA",
      "status": "ACCEPTED", "statusText": "通过",
      "timeUsedMs": 46, "memoryUsedKb": 33116,
      "createdAt": "2026-10-30T11:18:05"
    }
  ],
  "total": 1, "page": 1, "size": 20
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 提交 id，用于查询详情 |
| `problemId` / `problemTitle` | 题目 id / 标题 |
| `userId` / `userNickname` | 提交者 id / 昵称 |
| `language` | 语言 |
| `status` / `statusText` | 结果状态 / 中文文案 |
| `timeUsedMs` / `memoryUsedKb` | 最大耗时（毫秒）/ 内存峰值（KB，可能为 `null`） |
| `createdAt` | 提交时间 |

## 6.4 评测状态

| `status` | `statusText` | 含义 | 是否最终结果 |
| --- | --- | --- | --- |
| `PENDING` | 排队中 | 已提交，等待评测 | 否 |
| `JUDGING` | 评测中 | 正在评测 | 否 |
| `ACCEPTED` | 通过 | 全部测试点通过 | 是 |
| `WRONG_ANSWER` | 答案错误 | 有测试点输出与期望不一致 | 是 |
| `TIME_LIMIT_EXCEEDED` | 超出时间限制 | 运行超时 | 是 |
| `MEMORY_LIMIT_EXCEEDED` | 超出内存限制 | 内存使用超过题目限制 | 是 |
| `OUTPUT_LIMIT_EXCEEDED` | 输出超出限制 | 程序输出内容过大 | 是 |
| `RUNTIME_ERROR` | 运行错误 | 程序异常退出 | 是 |
| `COMPILATION_ERROR` | 编译错误 | 代码编译失败 | 是 |
| `CHECKER_ERROR` | 判题器错误 | 该题的自定义判题器编译失败/超时/崩溃。**这是题目/系统侧的问题，不是选手代码的问题**，应提示"评测机正在处理，请稍后重试"并通知出题者 | 是 |
| `SYSTEM_ERROR` | 系统错误 | 评测环境异常，可提示用户稍后重试 | 是 |

建议配色：`ACCEPTED` 绿色；`WRONG_ANSWER` / `TIME_LIMIT_EXCEEDED` / `RUNTIME_ERROR` / `COMPILATION_ERROR` 红色；`PENDING` / `JUDGING` 灰色；`CHECKER_ERROR` / `SYSTEM_ERROR` 橙色（带"重试"入口）。

## 6.5 轮询评测结果（示例）

```js
const BASE = 'http://localhost:8080/api/v1';

async function waitForJudge(id, token, onUpdate) {
  for (let i = 0; i < 60; i++) {
    const res = await fetch(`${BASE}/submissions/${id}`, {
      headers: { Authorization: `Bearer ${token}` }
    }).then(r => r.json());

    const data = res.data;
    onUpdate?.(data); // 可用于实时刷新状态、耗时、逐点结果

    if (!['PENDING', 'JUDGING'].includes(data.status)) return data;
    await new Promise(r => setTimeout(r, 800));
  }
  throw new Error('评测时间较长，请稍后到"我的提交"查看');
}
```
