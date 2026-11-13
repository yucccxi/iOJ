# 内部判题 API

本文是主服务器与判题 Agent 之间的内部 HTTP 契约，读者为 Agent 开发、后端开发和判题机运维人员。它描述请求字段、响应字段、租约与幂等规则；Isolate 参数和状态机解释见 [判题设计](../../server/03-judge.md)。

> 返回目录：[内部判题 API 索引](README.md)

> **内部接口**：仅供内部调用方使用，不得暴露到公网或写进公开调用示例。生产部署必须使用内网地址、VPN 或限制来源 IP。

## 1. 认证与统一响应

### 1.1 Base URL 与认证

| 项 | 说明 |
| --- | --- |
| Base URL | `http://<server-host>:8080/internal/judge` |
| 认证 | `Authorization: Bearer <judge-key>` |
| key 来源 | 管理员通过 `POST /api/v1/admin/judge-keys` 创建，明文只返回一次 |
| key 存储 | 服务端只存 SHA-256 哈希与 12 位前缀；Agent 把明文放在权限 0600 的环境文件中 |
| 适用角色 | `ROLE_JUDGE`；用户 JWT 不适用 |

key 失效条件：

- key 不存在、被禁用、已过期，或请求头缺失/格式错误：`401` + `1002`。
- 使用用户 JWT 访问内部接口：`403` + `1003`（身份有效但不是 JUDGE）。
- 一个 key 只能绑定一个 `nodeId`；同一 `nodeId` 换 key 注册会返回 `409` + `1005`。处理方式是换一个 `nodeId`，或删除旧 key（会级联删除旧节点）后重建。

### 1.2 响应信封

所有 JSON 响应使用：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | integer | `0` 成功，非 `0` 为错误码 |
| `message` | string | 可读错误信息 |
| `data` | object / array / null | 业务数据 |

测试数据下载接口是唯一的非 JSON 成功响应（`application/zip`），失败时仍返回 JSON 错误信封。

### 1.3 协议版本

当前 `ProtocolVersion.CURRENT = 2`。

| 项 | 说明 |
| --- | --- |
| v2 要求 | 测试数据 bundle 必须是 ZIP，归档内唯一入口为 `testcases.json`；旧 JSON bundle 已移除 |
| 启动校验 | Agent 拉取 `/config` 后必须校验 `protocolVersion`；不一致时拒绝启动，退出码 `78` |
| claim/heartbeat 字段 | 请求中的 `protocolVersion` 用于诊断；当前服务端不会因该字段不同而拒绝 claim，真正的兼容性由启动校验保证 |
| 升级策略 | 不兼容变更必须递增版本，并同时升级主服务器与 Agent；不得用字段缺失来“兼容”旧 Agent |

## 2. 启动配置

### 2.1 获取 Agent 配置

`GET /internal/judge/config` ｜ Bearer judge key

Agent 启动时调用一次，获取协议版本、必须支持的语言、语言命令快照、checker 引导开关和工具链探测命令。checker 的实际执行开关和限制由 Agent 本地配置决定，不在此响应中。

**请求**：无参数、无请求体。

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "protocolVersion": 2,
    "configVersion": "<sha256-hex>",
    "requiredLanguages": ["JAVA", "CPP", "C", "PYTHON3"],
    "languages": {
      "JAVA": {
        "sourceFile": "Main.java",
        "compileCommand": "javac -encoding UTF-8 -d . Main.java",
        "runCommand": "java -Xmx{memoryMb}m -Xss64m -XX:+UseSerialGC Main",
        "runWrapper": "exec {command}",
        "timeMultiplier": 2.0,
        "isolateProcesses": 64,
        "isolateMemoryExtraMb": 256,
        "copySourceToRun": false,
        "probeCommands": ["javac -version", "java -version"]
      },
      "CPP": {
        "sourceFile": "main.cpp",
        "compileCommand": "g++ -O2 -std=c++17 -pipe -o main main.cpp",
        "runCommand": "./main",
        "runWrapper": "ulimit -v {memoryKb}; ulimit -s 65536; exec {command}",
        "timeMultiplier": 1.0,
        "isolateProcesses": 1,
        "isolateMemoryExtraMb": 16,
        "copySourceToRun": false,
        "probeCommands": ["g++ --version"]
      },
      "C": {
        "sourceFile": "main.c",
        "compileCommand": "gcc -O2 -std=c11 -pipe -o main main.c",
        "runCommand": "./main",
        "runWrapper": "ulimit -v {memoryKb}; ulimit -s 65536; exec {command}",
        "timeMultiplier": 1.0,
        "isolateProcesses": 1,
        "isolateMemoryExtraMb": 16,
        "copySourceToRun": false,
        "probeCommands": ["gcc --version"]
      },
      "PYTHON3": {
        "sourceFile": "main.py",
        "compileCommand": "python3 -m py_compile main.py",
        "runCommand": "python3 main.py",
        "runWrapper": "ulimit -v {memoryKb}; exec {command}",
        "timeMultiplier": 3.0,
        "isolateProcesses": 1,
        "isolateMemoryExtraMb": 64,
        "copySourceToRun": true,
        "probeCommands": ["python3 --version"]
      }
    },
    "checkerEnabled": true,
    "checkerProbeCommands": ["g++ --version"]
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.protocolVersion` | integer | 内部协议版本，当前 `2` |
| `data.configVersion` | string | 语言/checker 配置的 SHA-256，十六进制，用于诊断 |
| `data.requiredLanguages` | string[] | Agent 必须支持的全部语言；等于主服务器 `ioj.judge.languages` 的 key 集合 |
| `data.languages` | object | 按语言名索引的命令快照，见 §9.2 |
| `data.checkerEnabled` | boolean | 主服务器是否声明 checker 可用并下发 checker 探针命令；**不是远程 Agent 的实际执行开关**。当前 Agent 实际是否执行 checker 由本地 `ioj.judge.checker.enabled` 决定 |
| `data.checkerProbeCommands` | string[] | 校验 checker 编译器的命令；主服务器 checker 关闭时为空数组 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 401 | `1002` | key 缺失、错误、禁用或过期 |
| 403 | `1003` | 使用了用户 JWT 而不是 judge key |
| 500 | `1500` | 服务端内部错误 |

**行为说明**

- `requiredLanguages` 是所有 Agent 的“同构”要求：主服务器支持的语言，每台 Agent 都必须具备对应工具链，不得只领部分语言。
- Agent 必须在启动时逐条执行 `probeCommands`，全部退出码为 0 才算通过；缺任意语言必须拒绝启动，而不是先发心跳或 claim。
- `configVersion` 只用于日志/诊断，不参与权限判断。
- 语言命令的权威来源是主服务器；Agent 本地只配置 Isolate 路径、box id、额外挂载目录等机器参数。
- `checkerEnabled` 只表示主服务器的引导开关，不是远程 Agent 的执行开关；`judge` 配置不包含 checker 的时限、内存或编译限制，远程 Agent 使用本地 `ioj.judge.checker.*`。

## 3. 节点心跳

### 3.1 注册 / 刷新节点

`POST /internal/judge/nodes/heartbeat` ｜ Bearer judge key

Agent 启动校验通过后立即发一次，之后按服务端返回的 `offlineSeconds / 3` 周期发送。节点不存在时自动注册。

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `nodeId` | string | 是 | — | Agent 实例唯一 id；一台 Agent 一个 |
| `hostname` | string / null | 否 | `null` | 主机名，供运维查看 |
| `version` | string / null | 否 | `null` | Agent 版本 |
| `languages` | string[] | 否 | `[]` | Agent 能判的语言，元素为 `Language` 枚举名 |
| `maxConcurrency` | integer | 是 | — | 最大并发；小于 1 会被纠正为 1 |
| `runningTasks` | integer | 是 | — | 当前运行任务数；小于 0 会被纠正为 0 |
| `isolateVersion` | string / null | 否 | `null` | Agent 上报的 Isolate 版本；当前 Agent 默认上报 `null` |

```json
{
  "nodeId": "judge-1",
  "hostname": "judge-1.campus",
  "version": "0.0.1",
  "languages": ["JAVA", "CPP", "PYTHON3"],
  "maxConcurrency": 2,
  "runningTasks": 1,
  "isolateVersion": "2.7"
}
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "offlineSeconds": 90
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.offlineSeconds` | integer | 超过该秒数没有心跳，节点标记为 `OFFLINE`；最小 30 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1000` | `nodeId` 为空；请求体不是合法 JSON |
| 409 | `1005` | 该 `nodeId` 已绑定其他 key |
| 401 | `1002` | key 无效 |
| 403 | `1003` | 非 JUDGE 身份 |

**行为说明**

- 节点以 `nodeId` 为主键 upsert；同一 key 重复心跳刷新 `languages`、`maxConcurrency`、`runningTasks` 和 `lastHeartbeatAt`。
- 心跳会把节点状态改回 `ONLINE`；离线由服务端定时器标记。
- Agent 启动后先发一次节点心跳，再进入 claim 循环；拿到响应前使用本地默认心跳间隔。
- 服务端的最小离线阈值是 30 秒；配置更小时会被提升到 30，避免心跳过于频繁。

## 4. 任务领取

### 4.1 领取任务

`POST /internal/judge/tasks/claim` ｜ Bearer judge key

从 `PENDING` 提交中按 id 升序领取最多 `maxTasks` 条，语言必须匹配请求中的 `languages`。服务端会用行锁保证同一条提交不会被两个 Agent 同时领取。

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `nodeId` | string | 是 | — | Agent 节点 id；不存在时自动注册 |
| `maxTasks` | integer | 否 | `1` | 期望领取数量；服务端再按 `ioj.judge.dispatch.max-claim-batch` 截断 |
| `languages` | string[] | 否 | `[]` | 可判语言；空数组表示不按语言过滤 |
| `protocolVersion` | integer | 否 | `2` | 协议版本，用于诊断 |

```json
{
  "nodeId": "judge-1",
  "maxTasks": 2,
  "languages": ["JAVA", "CPP"],
  "protocolVersion": 2
}
```

**成功响应（有任务）**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "tasks": [
      {
        "taskId": 500,
        "submissionId": 500,
        "claimToken": "9f1c2d3e4a5b6c7d8e9f0a1b2c3d4e5f",
        "leaseSeconds": 300,
        "language": "JAVA",
        "code": "public class Main { ... }",
        "timeLimitMs": 1000,
        "memoryLimitMb": 256,
        "languageConfig": {
          "sourceFile": "Main.java",
          "compileCommand": "javac -encoding UTF-8 -d . Main.java",
          "runCommand": "java -Xmx{memoryMb}m -Xss64m -XX:+UseSerialGC Main",
          "runWrapper": "exec {command}",
          "timeMultiplier": 2.0,
          "isolateProcesses": 64,
          "isolateMemoryExtraMb": 256,
          "copySourceToRun": false,
          "probeCommands": ["javac -version", "java -version"]
        },
        "checkerCode": null,
        "testcases": [
          { "ordinal": 1, "input": "1 2\n", "output": "3\n" },
          { "ordinal": 2, "input": "100 -50\n", "output": "50\n" }
        ],
        "testcaseBundleUrl": null,
        "bundleSha256": null
      }
    ]
  }
}
```

**成功响应（无任务）**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "tasks": []
  }
}
```

空数组表示当前没有工作；Agent 必须退避后重试，而不是忙轮询。

**字段说明**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.tasks[].taskId` | integer | 当前实现等于 `submissionId` |
| `data.tasks[].submissionId` | integer | 被评测的提交 id |
| `data.tasks[].claimToken` | string | fencing token，回报心跳/结果/释放时必须原样带回 |
| `data.tasks[].leaseSeconds` | integer | 本次租约秒数，最小 30 |
| `data.tasks[].language` | string | 提交语言 |
| `data.tasks[].code` | string | 源代码 |
| `data.tasks[].timeLimitMs` | integer | 题目基准时限；运行时限 = 基准 × `languageConfig.timeMultiplier` |
| `data.tasks[].memoryLimitMb` | integer | 题目内存限制 |
| `data.tasks[].languageConfig` | object | 主服务器的语言命令快照，见 §9.2 |
| `data.tasks[].checkerCode` | string / null | 自定义判题器源码；`null` 表示使用内置文本比对 |
| `data.tasks[].testcases` | array | 内联测试数据；使用 bundle 时为 `[]` |
| `data.tasks[].testcaseBundleUrl` | string / null | 相对 URL，指向 ZIP 测试数据；内联时为 `null` |
| `data.tasks[].bundleSha256` | string / null | 预留的 bundle 校验值；当前服务端不填充，始终为 `null` |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1000` | `nodeId` 为空；请求体不是合法 JSON |
| 409 | `1005` | `nodeId` 已绑定其他 key |
| 401 | `1002` | key 无效 |
| 403 | `1003` | 非 JUDGE 身份 |

**行为说明**

- 只领取 `status=PENDING` 的提交，按 id 升序；每条领取成功后变为 `JUDGING`，写入 `judge_key_id`、`judge_node_id`、`claim_token`、`claimed_at`、`lease_expires_at`，并递增 `attempt_count`。
- 服务端把 `maxTasks` 截断到 `ioj.judge.dispatch.max-claim-batch`（默认 10）且至少 1；Agent 侧还必须受自身并发限制。
- 测试数据总 UTF-8 字节数超过 `ioj.judge.dispatch.inline-max-bytes`（默认 1 MiB）时，`testcases` 为空并返回 `testcaseBundleUrl`；否则内联。
- 领取时如果题目、语言或测试数据已不可用，该提交会被标记为 `SYSTEM_ERROR`，不会返回给 Agent，循环继续尝试下一条。
- 测试数据 bundle URL 是相对路径（`/internal/judge/tasks/{submissionId}/testcases`），Agent 用同一个 base URL 和 `claimToken` 下载。
- 节点不存在时自动注册，但 `nodeId` 绑定 key 的规则仍然生效。

## 5. 任务心跳

### 5.1 续租

`POST /internal/judge/tasks/{submissionId}/heartbeat` ｜ Bearer judge key

长任务在租约过半前续租；Agent 默认按 `leaseSeconds / 3` 调度。

**路径参数**

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `submissionId` | integer | 提交 id，也就是 claim 返回的 `taskId` |

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `nodeId` | string | 否 | `null` | 当前实现只用于诊断，不参与校验 |
| `claimToken` | string | 是 | — | 当前租约 token |
| `runningTasks` | integer | 否 | `0` | 当前运行任务数，仅上报 |
| `protocolVersion` | integer | 否 | `2` | 协议版本，仅用于诊断 |

```json
{
  "nodeId": "judge-1",
  "claimToken": "9f1c2d3e4a5b6c7d8e9f0a1b2c3d4e5f",
  "runningTasks": 1,
  "protocolVersion": 2
}
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "leaseSeconds": 300,
    "cancel": false
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.leaseSeconds` | integer | 续期后的租约秒数；claim 无效时为 `0` |
| `data.cancel` | boolean | `true` 表示 Agent 必须放弃该任务 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 401 | `1002` | key 无效 |
| 403 | `1003` | 非 JUDGE 身份 |

**行为说明**

- 只有在 `claim_token` 匹配、提交仍为 `JUDGING`、且 `lease_expires_at > now` 时才续租；否则返回 `leaseSeconds=0, cancel=true`。
- 租约边界是半开区间：`lease_expires_at > now` 仍有效，`lease_expires_at <= now` 可被回收器处理。
- `nodeId`、`runningTasks`、`protocolVersion` 当前不参与服务端判断；建议 Agent 继续发送，便于日志和后续扩展。
- 收到 `cancel=true` 时，Agent 必须停止执行并丢弃本地结果；不得再调用 result。

## 6. 测试数据 Bundle

### 6.1 下载 ZIP 测试数据

`GET /internal/judge/tasks/{submissionId}/testcases?claimToken=...` ｜ Bearer judge key

当 claim 返回 `testcaseBundleUrl` 时调用。服务端始终返回 ZIP（协议 v2），不再提供 JSON bundle。

**路径 / 查询参数**

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `submissionId` | integer | 是 | 提交 id |
| `claimToken` | string | 是 | 当前租约 token |

**成功响应**

| 项 | 值 |
| --- | --- |
| HTTP | `200` |
| `Content-Type` | `application/zip` |
| `Content-Disposition` | `attachment; filename="testcases-<submissionId>.zip"` |
| `Cache-Control` | `no-store` |
| 归档内容 | 唯一入口 `testcases.json`，内容是 `JudgeTestCase[]` JSON 数组 |

`testcases.json` 示例：

```json
[
  { "ordinal": 1, "input": "1 2\n", "output": "3\n" },
  { "ordinal": 2, "input": "100 -50\n", "output": "50\n" }
]
```

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1000` | `claimToken` 缺失或为空 |
| 404 | `4001` | 提交不存在 |
| 409 | `1005` | 提交不再是 `JUDGING`，或 `claimToken` 不是当前租约 |
| 401 | `1002` | key 无效 |
| 403 | `1003` | 非 JUDGE 身份 |

**行为说明**

- 下载必须使用 claim 时拿到的同一个 `claimToken`；租约过期或被重领后返回 `409`。
- Agent 必须校验 HTTP 状态和 `Content-Type`，不得把 HTML 错误页当 ZIP 解压。
- ZIP 只有一个 `testcases.json` 条目；不得按“一题一文件”实现。
- `bundleSha256` 当前为 `null`，Agent 不得依赖它做完整性校验。
- 响应不缓存；重复下载是安全的，但会增加带宽。

## 7. 结果回报

### 7.1 回报终态结果

`POST /internal/judge/tasks/{submissionId}/result` ｜ Bearer judge key

将一次评测的最终状态、整体耗时/内存、编译信息和逐测试点结果写回主服务器。

**路径参数**

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `submissionId` | integer | 提交 id |

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `claimToken` | string | 是 | — | 当前租约 token |
| `status` | string | 是 | — | 终态：`ACCEPTED` / `WRONG_ANSWER` / `TIME_LIMIT_EXCEEDED` / `MEMORY_LIMIT_EXCEEDED` / `OUTPUT_LIMIT_EXCEEDED` / `RUNTIME_ERROR` / `COMPILATION_ERROR` / `CHECKER_ERROR` / `SYSTEM_ERROR` |
| `timeUsedMs` | integer / null | 否 | `null` | 整体最大耗时（毫秒） |
| `memoryUsedKb` | integer / null | 否 | `null` | 整体内存峰值（KB） |
| `compileMessage` | string / null | 否 | `null` | 编译器输出；仅编译错误时有意义 |
| `judgeMessage` | string / null | 否 | `null` | 系统/判题器错误说明 |
| `cases` | array | 否 | `[]` | 逐测试点结果；允许为空 |

`cases` 每一项：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `ordinal` | integer | 是 | — | 测试点编号，必须 > 0 且不重复 |
| `status` | string | 是 | — | 终态（不含 `PENDING` / `JUDGING`） |
| `timeUsedMs` | integer / null | 否 | `null` | 该点耗时，不得为负数 |
| `memoryUsedKb` | integer / null | 否 | `null` | 该点内存，不得为负数 |
| `message` | string / null | 否 | `null` | 失败原因，最多 1024 字符 |

```json
{
  "claimToken": "9f1c2d3e4a5b6c7d8e9f0a1b2c3d4e5f",
  "status": "WRONG_ANSWER",
  "timeUsedMs": 3,
  "memoryUsedKb": 2048,
  "compileMessage": null,
  "judgeMessage": null,
  "cases": [
    {
      "ordinal": 1,
      "status": "WRONG_ANSWER",
      "timeUsedMs": 3,
      "memoryUsedKb": 2048,
      "message": "output mismatch"
    }
  ]
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
| 400 | `1000` | `claimToken` 缺失；`status` 不是终态；`cases` 元素非法/序号重复/耗时为负 |
| 404 | `4001` | 提交不存在 |
| 409 | `1005` | 提交不再是 `JUDGING`，或 `claimToken` 不是当前租约 |
| 401 | `1002` | key 无效 |
| 403 | `1003` | 非 JUDGE 身份 |

**行为说明**

- 只有 `status=JUDGING` 且 `claim_token` 匹配时才写入；旧 token 或已重领的任务返回 `409`，不会覆盖新结果。
- 同一 `claimToken` 且提交已有 `judgedAt` 时，重复回报视为幂等成功，不重复写、不重复增加通过数。
- `status=ACCEPTED` 时，只有真正写入成功才把 `problems.accepted_count` +1。
- `compileMessage` / `judgeMessage` 最多保留 65536 字符，`cases[].message` 最多 1024 字符；超长由服务端截断。
- `cases` 是本次评测的完整结果：服务端按 `(submission_id, case_ordinal)` upsert，并删除本次结果中不存在的旧序号，所以重判结果更少时不会残留旧点。
- 结果里的 `cases[].message` 不得包含隐藏测试数据或标准答案。
- 失败即停是 Agent/引擎的行为：可以只回报已执行的测试点，`cases` 允许少于题目测试点总数。

## 8. 任务释放

### 8.1 主动释放任务

`POST /internal/judge/tasks/{submissionId}/release` ｜ Bearer judge key

Agent 优雅停机或没有空闲并发时，把尚未开始执行的任务交还队列。

**路径参数**

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `submissionId` | integer | 提交 id |

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `claimToken` | string / null | 否 | `null` | 要释放的租约；为空时服务端不做任何事 |
| `reason` | string / null | 否 | `null` | 释放原因，仅用于日志；Agent 会压缩空白并截断到 200 字符 |

```json
{
  "claimToken": "9f1c2d3e4a5b6c7d8e9f0a1b2c3d4e5f",
  "reason": "agent shutdown"
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
| 401 | `1002` | key 无效 |
| 403 | `1003` | 非 JUDGE 身份 |

**行为说明**

- 只释放 `claim_token` 匹配且状态为 `JUDGING` 的任务；不匹配时静默成功，不会报错。
- 释放后提交回到 `PENDING`，清空 `judge_key_id` / `judge_node_id` / `claim_token` / `claimed_at` / `lease_expires_at`；`attempt_count` 不重置。
- 已经执行过的任务不得 release 后再次回报结果；租约已失效，结果会被 `409` 拒绝。
- Agent 正常停机时会等待执行中的任务最多 30 秒；未完成的任务会尝试 release，再强制结束。

## 9. 数据模型

### 9.1 语言枚举

| 值 | 含义 |
| --- | --- |
| `JAVA` | Java 21 |
| `CPP` | C++17 |
| `C` | C11 |
| `PYTHON3` | Python 3 |

语言命令来自主服务器配置，不在 Agent 本地维护。新增语言时，主服务器增加枚举和 `ioj.judge.languages` 配置，Agent 启动探测通过后自动支持。

### 9.2 `JudgeLanguageConfig`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sourceFile` | string | 源代码写入的文件名 |
| `compileCommand` | string / null | 编译命令；解释型语言可为 `null` |
| `runCommand` | string | 运行命令，支持 `{memoryMb}` / `{memoryKb}` / `{timeMs}` 占位符 |
| `runWrapper` | string / null | 本地进程引擎的 shell 包装；Isolate 引擎不使用 |
| `timeMultiplier` | number | 题目时限的倍数；Java 2.0、Python 3.0 |
| `isolateProcesses` | integer / null | 运行阶段允许的进程/线程数；Java 64，其余通常 1 |
| `isolateMemoryExtraMb` | integer / null | 运行阶段在题目内存上额外预留的 MB；Java 256 |
| `copySourceToRun` | boolean | 是否把源码复制到每个运行目录（Python 需要） |
| `probeCommands` | string[] | 启动时校验工具链的命令，每条退出码必须为 0 |

### 9.3 `JudgeTask`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `taskId` | integer | 当前实现等于 `submissionId` |
| `submissionId` | integer | 提交 id |
| `claimToken` | string | fencing token，32 位十六进制 |
| `leaseSeconds` | integer | 租约秒数，最小 30 |
| `language` | string | 语言枚举 |
| `code` | string | 源代码 |
| `timeLimitMs` | integer | 题目基准时限 |
| `memoryLimitMb` | integer | 题目内存限制 |
| `languageConfig` | object | 语言命令快照 |
| `checkerCode` | string / null | 判题器源码；无 checker 时为 `null` |
| `testcases` | array | 内联测试点；bundle 模式为空数组 |
| `testcaseBundleUrl` | string / null | 相对 URL；内联时为 `null` |
| `bundleSha256` | string / null | 预留，当前为 `null` |

### 9.4 `JudgeTestCase`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `ordinal` | integer | 编号，从 1 开始 |
| `input` | string | 标准输入 |
| `output` | string | 期望输出 |

### 9.5 `JudgeResult` / `JudgeCaseResult`

`JudgeResult` 字段见 §7.1；`JudgeCaseResult` 字段见 `cases` 表。

### 9.6 提交状态与结果状态

终态：`ACCEPTED`、`WRONG_ANSWER`、`TIME_LIMIT_EXCEEDED`、`MEMORY_LIMIT_EXCEEDED`、`OUTPUT_LIMIT_EXCEEDED`、`RUNTIME_ERROR`、`COMPILATION_ERROR`、`CHECKER_ERROR`、`SYSTEM_ERROR`。

`PENDING` / `JUDGING` 不是合法结果状态；回报它们会返回 `400`。

`CHECKER_ERROR` 表示题目自带判题器失败，不是选手代码错误；`SYSTEM_ERROR` 表示题目/测试数据/语言/沙箱/租约等系统问题。

## 10. 租约、回收与离线

- 租约时长由主服务器 `ioj.judge.dispatch.lease-seconds` 决定（默认 300 秒，最小 30）。
- 任务心跳间隔建议为租约的 1/3；节点心跳间隔为 `offlineSeconds / 3`。
- 主服务器每 `reaper-interval-ms`（默认 30 秒）回收一次过期租约：
  - `lease_expires_at <= now` 且 `attempt_count < max-attempts`：改回 `PENDING`，等待重新领取。
  - `lease_expires_at <= now` 且 `attempt_count >= max-attempts`：改判 `SYSTEM_ERROR`，`judge_message` 为“判题机多次领取任务后未回报结果，任务已终止”。
- `attempt_count` 在每次成功 claim 时 +1，release / 重新排队不会清零；因此坏任务不会无限重试。
- 节点超过 `offlineSeconds` 没有心跳会被标记 `OFFLINE`；任务不会因节点离线立即失败，仍由租约回收器处理。
- 所有租约时间以主服务器时钟为准；Agent 必须开启 NTP，不得用本地时钟判断任务是否过期。

## 11. 重试与异常处理

| 场景 | 建议 |
| --- | --- |
| 空 claim | 退避 1-5 秒并加随机抖动，不得忙轮询 |
| 节点心跳失败 | 记录日志，按本地间隔重试；网络恢复后节点重新上线 |
| 任务心跳返回 `cancel=true` | 立即停止执行，丢弃本地结果，不得调用 result |
| 任务心跳网络失败 | 按租约的 1/3 重试；若租约即将过期，优先停止并等待回收 |
| bundle 下载失败 | 用同一 token 重试；收到 `409` 时放弃任务 |
| result 网络失败 | 用同一 token 重试，建议 1 s / 2 s / 5 s；同一 token 幂等 |
| result 返回 `409` | 租约已失效或被重领，不得继续重试 |
| result 返回 `400` | 检查终态、ordinal 是否重复、耗时是否为负；修正后重试 |
| 配置拉取失败 | 启动阶段按 `config-fetch-retries` 重试；key 认证失败直接退出 `78` |
| 工具链缺失 | 不启动；安装对应工具链后重启 Agent |

## 12. 安全要求

- 只通过内网、VPN 或来源 IP 白名单暴露 `/internal/judge/**`；公网 Nginx 不得转发该前缀。
- key 放在权限 `0600` 的环境文件或密钥管理系统中，不写进 unit 文件、不提交 Git、不打印到日志。
- 一个 Agent 一个 key、一个唯一 `nodeId`；怀疑泄露时创建新 key、切换 Agent、禁用旧 key。
- 删除 key 会级联删除节点记录，并把历史提交的 `judge_key_id` 置为 `NULL`；不会删除历史提交。
- Agent 以非 root 用户运行；Isolate 的 setuid 位由官方包/系统配置管理。
- 判题机不保存数据库密码、JWT 密钥、SMTP 凭据或证书；它只需要出站访问主服务器。
- 主服务器与判题机之间建议使用 TLS 或受控内网；不得用明文 HTTP 穿越不可信网络。

## 相关文档

- [内部判题 API 索引](README.md)
- [判题设计](../../server/03-judge.md)
- [判题 Agent 部署与运维](../../server/07-judge-agent.md)
- [Isolate 沙箱运维](../../server/06-isolate.md)
- [API 通用约定](../README.md)
