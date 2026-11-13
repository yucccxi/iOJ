# 管理后台

本文说明题目审核、用户管理、标签/scope 字典和判题机管理的 HTTP 接口，读者为管理员与前端开发人员。所有 `/api/v1/admin/**` 接口都要求 `role=ADMIN`：未登录返回 `401` + `1002`，普通用户返回 `403` + `1003`。

> 返回目录：[前端 API 索引](README.md)

> 管理员不得修改自己的角色，也不得封禁自己；这两条限制用于避免误锁最后一个管理员。管理员可以查看未发布题目和隐藏测试数据，但普通用户不得。

## 1. 题目审核

### 1.1 审核列表

`GET /api/v1/admin/problems` ｜ 管理员

**查询参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `keyword` | string | 否 | — | 标题模糊搜索 |
| `status` | string | 否 | — | `DRAFT` / `PENDING_REVIEW` / `PUBLISHED` / `HIDDEN`；不传表示全部；非法值返回 `1000` |
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

字段与 [我的题目](03-problems.md#1-我的题目) 的摘要相同：`status` 是审核页面的核心字段。审核时用 `GET /problems/{id}` 查看题面，管理员可以读取未发布题目。

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1000` | `status` 不是合法枚举 |
| 401 / 403 | `1002` / `1003` | 未登录 / 非管理员 |

### 1.2 上架 / 下架题目

`PATCH /api/v1/admin/problems/{id}/status` ｜ 管理员

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `status` | string | 是 | — | 只接受 `PUBLISHED` 或 `HIDDEN` |

```json
{
  "status": "PUBLISHED"
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
| 400 | `1000` | 传了 `PUBLISHED` / `HIDDEN` 以外的状态 |
| 404 | `3001` | 题目不存在 |
| 400 | `3003` | 上架时题目还没有测试数据 |
| 401 / 403 | `1002` / `1003` | 未登录 / 非管理员 |

**行为说明**

- 上架后题目出现在公开题库，所有人可见、可提交。
- 下架后从公开题库消失，未登录和其他用户访问详情返回 `403` / `3002`；已有提交记录仍可查询。
- 该接口不会修改题目内容、作者、测试数据或判题器。
- `DRAFT` / `PENDING_REVIEW` 不得通过本接口设置；它们由上传时的自动发布策略产生。

## 2. 用户管理

### 2.1 用户列表

`GET /api/v1/admin/users` ｜ 管理员

**查询参数**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `keyword` | string | 否 | — | 用户名、昵称、邮箱任一模糊匹配；为空不筛选 |
| `page` / `size` | integer | 否 | `1` / `20` | 分页 |

**成功响应**（按 id 倒序）

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": 13,
        "username": "bob",
        "nickname": "Bob",
        "role": "USER",
        "createdAt": "2026-10-30T11:20:05"
      },
      {
        "id": 12,
        "username": "alice",
        "nickname": "Alice",
        "role": "USER",
        "createdAt": "2026-10-30T11:18:04"
      }
    ],
    "total": 2,
    "page": 1,
    "size": 20
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.items[].id` | integer | 用户 id，用于改角色/封禁 |
| `data.items[].username` / `nickname` | string | 登录名 / 展示名 |
| `data.items[].role` | string | `USER` / `ADMIN` |
| `data.items[].createdAt` | string | 注册时间 |

> 当前列表**不返回邮箱和封禁状态**。管理页如果需要展示“谁被封禁”，先与后端约定新增字段；不得用角色或昵称推断状态。

### 2.2 修改用户角色

`PATCH /api/v1/admin/users/{id}/role` ｜ 管理员

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `role` | string | 是 | — | `USER` / `ADMIN` |

```json
{
  "role": "ADMIN"
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
| 400 | `1000` | 修改自己的角色；`role` 取值非法 |
| 404 | `2001` | 目标用户不存在 |
| 401 / 403 | `1002` / `1003` | 未登录 / 非管理员 |

**行为说明**

- 角色变更在对方下一次请求时生效，因为每次请求都从数据库重新加载角色；不会使已签发的 token 立即失效。
- 管理员不得修改自己的角色，避免误锁。

### 2.3 封禁 / 解封用户

`PATCH /api/v1/admin/users/{id}/status` ｜ 管理员

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `status` | string | 是 | — | `ACTIVE`（正常）/ `BANNED`（封禁） |

```json
{
  "status": "BANNED"
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

**封禁后的表现**

| 场景 | 结果 |
| --- | --- |
| 被封禁用户尝试登录 | `403` + `2004` |
| 被封禁用户已有 token | 下一次请求被拒绝；需要登录的接口返回 `401` / `1002` |
| 被封禁用户浏览公开页面 | 按未登录处理，公开内容仍可浏览 |
| 解封后 | 可用原密码正常登录 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1000` | 封禁自己；`status` 取值非法 |
| 404 | `2001` | 目标用户不存在 |
| 401 / 403 | `1002` / `1003` | 未登录 / 非管理员 |

## 3. 标签管理

标签是共享字典：本地题与外部题都用它，出题时只能选已有 id，不得通过题目接口创建。列表（含使用量）走公开的 [`GET /meta/tags`](02-catalog.md#22-标签列表)，本节只负责写操作。

### 3.1 新建标签

`POST /api/v1/admin/tags` ｜ 管理员

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `name` | string | 是 | — | 1-32 位字母、数字、中文或 `_+#.-`；首尾空格会去掉 |

```json
{
  "name": "图论"
}
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 3,
    "name": "图论",
    "problemCount": 0
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.id` | integer | 标签 id，出题时作为 `tagIds` 元素 |
| `data.name` | string | 标签名 |
| `data.problemCount` | integer | 使用该标签的本地题（所有状态）+ 外部题数量；新建时为 0 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1001` | 名称为空、超过 32 字符或含非法字符 |
| 409 | `1005` | 标签已存在 |
| 401 / 403 | `1002` / `1003` | 未登录 / 非管理员 |

### 3.2 重命名标签

`PUT /api/v1/admin/tags/{id}` ｜ 管理员

| 路径参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | integer | 标签 id |

请求体同 3.1；成功响应也同 3.1，`problemCount` 为改名后的当前使用量（统计所有状态的本地题 + 外部题）。

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1001` | 名称为空、超过 32 字符或含非法字符 |
| 404 | `1004` | 标签不存在 |
| 409 | `1005` | 新名称与另一个标签重名 |
| 401 / 403 | `1002` / `1003` | 未登录 / 非管理员 |

### 3.3 删除标签

`DELETE /api/v1/admin/tags/{id}` ｜ 管理员

| 参数 | 位置 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `id` | path | integer | 是 | — | 标签 id |
| `force` | query | boolean | 否 | `false` | 为 `true` 时先解除所有关联再删除；为 `false` 时在用标签返回冲突 |

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
| 404 | `1004` | 标签不存在 |
| 409 | `1005` | 标签仍在使用且 `force=false` |
| 401 / 403 | `1002` / `1003` | 未登录 / 非管理员 |

**行为说明**

- 重名请求返回 `1005`。
- `force=true` 会先解除该标签与本地题、外部题的全部关联，再删除标签。
- 改名后所有引用该标签的题目，其 `tags[].name` 立即变化。
- 公开 `GET /meta/tags` 的 `problemCount` 只统计已发布本地题；管理写操作返回的 `problemCount` 统计所有状态，两者口径不同。

## 4. Scope 管理

scope 是 OJ 平台/比赛的共享字典，外部题用它分类；所有本地题固定引用系统保留 scope `LOCAL`。列表走公开的 [`GET /meta/scopes`](02-catalog.md#23-scope-列表)，本节只负责写操作。

### 4.1 新建 Scope

`POST /api/v1/admin/scopes` ｜ 管理员

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `name` | string | 是 | — | 1-128 字符；首尾空格会去掉 |

```json
{
  "name": "Codeforces"
}
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 7,
    "name": "Codeforces",
    "createdAt": "2026-11-01T09:00:00",
    "problemCount": 0
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.id` | integer | scope id |
| `data.name` | string | scope 名 |
| `data.createdAt` | string | 创建时间 |
| `data.problemCount` | integer | 引用该 scope 的本地题（所有状态）+ 外部题数量；新建时为 0 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1001` | 名称为空或超过 128 字符 |
| 409 | `1005` | scope 已存在 |
| 401 / 403 | `1002` / `1003` | 未登录 / 非管理员 |

### 4.2 重命名 Scope

`PUT /api/v1/admin/scopes/{id}` ｜ 管理员

| 路径参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | integer | scope id |

请求体同 4.1；成功响应同 4.1，`problemCount` 为改名后的当前使用量（统计所有状态的本地题 + 外部题）。

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1001` | 名称为空或超过 128 字符 |
| 404 | `1004` | scope 不存在 |
| 409 | `1005` | 新名称与另一个 scope 重名；试图改名 `LOCAL` |
| 401 / 403 | `1002` / `1003` | 未登录 / 非管理员 |

### 4.3 删除 Scope

`DELETE /api/v1/admin/scopes/{id}` ｜ 管理员

| 路径参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | integer | scope id |

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
| 404 | `1004` | scope 不存在 |
| 409 | `1005` | scope 仍被使用；试图删除 `LOCAL` |
| 401 / 403 | `1002` / `1003` | 未登录 / 非管理员 |

**行为说明**

- `LOCAL` 是系统保留 scope，不得改名、不得删除。
- 删除仍被引用的 scope 会返回 `1005`；引用包括所有状态的本地题和外部题，**不支持** `force`。需要先把相关题目迁移到其他 scope 或删除。
- 外部题 scope 改名后，统一题库的 `scopeName` 立即变化；本地题仍显示 `LOCAL`。

## 5. 判题机 Key 管理

`/api/v1/admin/judge-keys` ｜ 管理员

判题 Agent 使用 key 访问 `/internal/judge/**`。**明文 key 只在创建响应中出现一次**，之后只能看到前缀。

| 接口 | 说明 |
| --- | --- |
| `POST /api/v1/admin/judge-keys` | 创建，请求体 `{ "name": "judge-1", "expiresAt": null }` |
| `GET /api/v1/admin/judge-keys` | 列表，不返回明文 key |
| `PATCH /api/v1/admin/judge-keys/{id}` | 启用/禁用，请求体 `{ "enabled": false }` |
| `DELETE /api/v1/admin/judge-keys/{id}` | 删除 |

### 5.1 创建 Key

`POST /api/v1/admin/judge-keys` ｜ 管理员

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `name` | string | 是 | — | 备注/节点名，≤128 字符 |
| `expiresAt` | string / null | 否 | `null` | 过期时间，格式 `yyyy-MM-ddTHH:mm:ss`；`null` 表示不过期 |

```json
{
  "name": "judge-1",
  "expiresAt": null
}
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 3,
    "name": "judge-1",
    "prefix": "jsk_3f9a2c7e",
    "key": "jsk_<judge-key>",
    "expiresAt": null,
    "createdAt": "2026-11-11T10:00:00"
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.id` | integer | key id |
| `data.name` | string | 备注/节点名 |
| `data.prefix` | string | 展示用前缀，后续列表用它识别 key |
| `data.key` | string | 完整 key，**只返回一次**；保存到判题机的 `IOJ_JUDGE_KEY` |
| `data.expiresAt` | string / null | 过期时间 |
| `data.createdAt` | string | 创建时间 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1001` | `name` 为空或超过 128 字符 |
| 401 / 403 | `1002` / `1003` | 未登录 / 非管理员 |

### 5.2 Key 列表

`GET /api/v1/admin/judge-keys` ｜ 管理员

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": 3,
      "name": "judge-1",
      "prefix": "jsk_3f9a2c7e",
      "enabled": true,
      "expiresAt": null,
      "lastUsedAt": "2026-11-11T10:05:00",
      "createdAt": "2026-11-11T10:00:00"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data[].enabled` | boolean | 禁用后不得 claim/result |
| `data[].lastUsedAt` | string / null | 最近一次成功领取到任务的时间；空轮询不刷新 |
| `data[].expiresAt` | string / null | 过期时间 |

列表不返回明文 key 或哈希。

### 5.3 启用 / 禁用 Key

`PATCH /api/v1/admin/judge-keys/{id}` ｜ 管理员

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `enabled` | boolean | 是 | — | `true` 启用，`false` 禁用 |

```json
{
  "enabled": false
}
```

**成功响应**：`{ "code": 0, "message": "ok", "data": null }`。

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 404 | `1004` | key 不存在 |
| 400 | `1001` | `enabled` 缺失 |

### 5.4 删除 Key

`DELETE /api/v1/admin/judge-keys/{id}` ｜ 管理员

**成功响应**：`{ "code": 0, "message": "ok", "data": null }`。

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 404 | `1004` | key 不存在 |

**行为说明**

- 禁用/删除 key 后，该节点不得继续领取任务或回报结果；未完成任务的租约到期后由主服务器回收。
- 删除 key 会同时删除对应判题节点记录；历史提交仍保留，只是不再关联该 key。
- key 丢失时直接删除并重新创建，不得尝试找回明文。
- 一个判题节点建议使用一个独立 key。

## 6. 判题节点列表

`GET /api/v1/admin/judge-nodes` ｜ 管理员

查看当前判题机是否在线、支持哪些语言、正在跑多少任务。

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "nodeId": "judge-1",
      "hostname": "judge-1.campus",
      "version": "0.0.1",
      "languages": ["JAVA", "CPP", "PYTHON3"],
      "maxConcurrency": 2,
      "runningTasks": 1,
      "isolateVersion": "2.7",
      "status": "ONLINE",
      "lastHeartbeatAt": "2026-11-11T10:00:00"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data[].nodeId` | string | Agent 配置的节点 id |
| `data[].hostname` | string / null | 主机名 |
| `data[].version` | string / null | Agent 版本 |
| `data[].languages` | array | Agent 上报的语言能力 |
| `data[].maxConcurrency` / `runningTasks` | integer | 最大并发 / 最近一次心跳上报的运行数 |
| `data[].isolateVersion` | string / null | Agent 上报的 Isolate 版本；当前 Agent 默认上报 `null` |
| `data[].status` | string | `ONLINE` / `OFFLINE` |
| `data[].lastHeartbeatAt` | string / null | 最近心跳时间 |

**行为说明**

- `OFFLINE` 表示超过 `node-offline-seconds` 没有心跳；任务不会立即丢弃，等租约过期后由主服务器回收。
- 节点由 Agent 首次 claim 或节点心跳自动注册；一个 key 只能绑定一个 `nodeId`。
- 节点列表只读，没有手动上下线接口。

## 相关文档

- [前端 API 索引](README.md)
- [题目与测试数据](03-problems.md)
- [内部判题 API](../judge/README.md)
- [API 通用约定](../README.md)
