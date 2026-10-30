# iOJ 前端接口文档（v1）

本文件面向前端开发：只说明接口行为、参数含义和字段含义，不涉及服务端实现。
所有接口都以 `/api/v1` 为前缀；示例中的 id、时间仅为示意。

## 目录

- [1. 通用约定](#1-通用约定)
  - [1.1 地址与格式](#11-地址与格式)
  - [1.2 认证](#12-认证)
  - [1.3 统一响应](#13-统一响应)
  - [1.4 分页](#14-分页)
  - [1.5 错误码](#15-错误码)
  - [1.6 谁可以调用、能看到什么](#16-谁可以调用能看到什么)
- [2. 账号](#2-账号)
  - [2.1 注册](#21-注册)
  - [2.2 登录](#22-登录)
  - [2.3 当前登录用户](#23-当前登录用户)
- [3. 用户资料](#3-用户资料)
  - [3.1 查看个人主页](#31-查看个人主页)
  - [3.2 查看自己的主页](#32-查看自己的主页)
  - [3.3 修改昵称](#33-修改昵称)
  - [3.4 修改密码](#34-修改密码)
- [4. 题库](#4-题库)
  - [4.1 题目列表](#41-题目列表)
  - [4.2 我上传的题目](#42-我上传的题目)
  - [4.3 题目详情](#43-题目详情)
  - [4.4 上传题目](#44-上传题目)
  - [4.5 编辑题目](#45-编辑题目)
  - [4.6 删除题目](#46-删除题目)
- [5. 测试数据](#5-测试数据)
  - [5.1 测试数据列表](#51-测试数据列表)
  - [5.2 查看单条测试数据](#52-查看单条测试数据)
  - [5.3 追加一条测试数据](#53-追加一条测试数据)
  - [5.4 覆盖全部测试数据](#54-覆盖全部测试数据)
  - [5.5 删除一条测试数据](#55-删除一条测试数据)
- [6. 提交与评测](#6-提交与评测)
  - [6.1 提交代码](#61-提交代码)
  - [6.2 提交详情](#62-提交详情)
  - [6.3 提交列表](#63-提交列表)
  - [6.4 评测状态](#64-评测状态)
  - [6.5 轮询评测结果（示例）](#65-轮询评测结果示例)
- [7. 参考数据](#7-参考数据)
  - [7.1 可选语言](#71-可选语言)
  - [7.2 标签列表](#72-标签列表)
- [8. 管理后台](#8-管理后台)
  - [8.1 用户列表](#81-用户列表)
  - [8.2 修改用户角色](#82-修改用户角色)
  - [8.3 封禁 / 解封用户](#83-封禁--解封用户)
  - [8.4 题目审核列表](#84-题目审核列表)
  - [8.5 上架 / 下架题目](#85-上架--下架题目)
- [9. 附录](#9-附录)
  - [9.1 枚举速查](#91-枚举速查)
  - [9.2 可能为 null 的字段](#92-可能为-null-的字段)
  - [9.3 常见现象与处理](#93-常见现象与处理)
  - [9.4 页面与接口对照](#94-页面与接口对照)

## 1. 通用约定

### 1.1 地址与格式

| 项 | 说明 |
| --- | --- |
| Base URL | `http://<host>:8080/api/v1` |
| 请求格式 | 有请求体时使用 `Content-Type: application/json`，UTF-8 |
| 字段命名 | 大小写敏感；请求体里多传的字段会被忽略，不会报错 |
| 时间 | 本地时间字符串，无时区，例如 `2026-10-30T11:18:05`；个别接口可能带毫秒/纳秒，按字符串或日期库解析即可 |
| 枚举 | 大写英文，例如 `EASY`、`PUBLISHED`、`WRONG_ANSWER` |

### 1.2 认证

除了标注「公开」的接口，都需要携带登录后拿到的令牌：

```
Authorization: Bearer <token>
```

- 令牌由登录接口返回，默认有效期 7 天。
- **令牌缺失、过期或账号被封禁时不会单独报错**：
  - 公开接口照常返回，效果等同未登录（例如题目 `solved=false`、提交 `code=null`）；
  - 需要登录的接口返回 `401`（错误码 `1002`）。
- 被封禁的账号登录会直接返回 `403`（错误码 `2004`）。
- 切换账号、退出登录、令牌过期时，前端清掉本地令牌即可。

### 1.3 统一响应

```json
{ "code": 0, "message": "ok", "data": {} }
```

| 字段 | 说明 |
| --- | --- |
| `code` | `0` 成功；非 `0` 为错误码 |
| `message` | 成功固定 `ok`；失败为可直接展示给用户的提示 |
| `data` | 业务数据；删除、改密码、审核等无返回值的接口为 `null` |

两个注意点：

1. **值为 null 的字段也会返回**，例如 `"code": null`、`"judgeMessage": null`。判断"有没有值"请用 `!= null`，不要用 `!value`。
2. 参数校验失败时 `message` 会拼接多个字段原因，格式如 `password: 密码长度需为 8-64 位; email: 邮箱格式不正确`。

```json
{ "code": 1001, "message": "password: 密码长度需为 8-64 位; email: 邮箱格式不正确", "data": null }
```

### 1.4 分页

列表接口统一使用 `page` + `size`：

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `page` | 1 | 页码，从 1 开始；传 0 或负数会被当成 1 |
| `size` | 20 | 每页条数；传 0/负数会被当成 1，超过 100 会被当成 100 |

```json
{ "items": [], "total": 0, "page": 1, "size": 20 }
```

| 字段 | 说明 |
| --- | --- |
| `items` | 当前页数据；没有数据或页码超出范围时为 `[]` |
| `total` | 满足筛选条件的总条数（不是总页数） |
| `page` / `size` | 实际生效的页码和条数（可能被你传入的值被纠正过） |

### 1.5 错误码

| code | HTTP | 含义 | 前端处理建议 |
| --- | --- | --- | --- |
| 0 | 200 | 成功 | — |
| 1000 | 400 | 请求参数错误 | 检查参数名、类型、枚举值、请求方法、`Content-Type` |
| 1001 | 400 | 参数校验失败 | 按 `message` 提示用户 |
| 1002 | 401 | 未登录或登录已失效 | 清本地令牌，跳转登录 |
| 1003 | 403 | 没有权限 | 提示无权限；后台页面需校验角色 |
| 1004 | 404 | 请求的资源不存在 | 跳 404 页 |
| 1005 | 409 | 资源冲突 | 提示稍后重试或更换信息 |
| 2001 | 404 | 用户不存在 | 展示"用户不存在" |
| 2002 | 409 | 用户名已被占用 | 在用户名输入框下提示 |
| 2003 | 409 | 邮箱已被注册 | 在邮箱输入框下提示 |
| 2004 | 403 | 账号已被禁用 | 提示联系管理员 |
| 2005 | 401 | 用户名或密码错误 | 提示"账号或密码错误" |
| 2006 | 400 | 原密码不正确 | 在旧密码输入框下提示 |
| 3001 | 404 | 题目不存在 | 跳 404 页 |
| 3002 | 403 | 题目尚未发布 | 提示"题目不可见或已下架"（不要显示"无权限"） |
| 3003 | 400 | 题目还没有测试数据 | 提交按钮置灰并提示等待作者补充 |
| 3004 | 404 | 测试点不存在 | 刷新测试点列表 |
| 3005 | 400 | 测试点数量超出上限 | 提示最多数量 |
| 4001 | 404 | 提交记录不存在 | 跳回提交列表 |
| 4002 | 400 | 不支持的语言 | 重新拉取语言列表 |
| 4003 | 400 | 代码过长 | 提示字数限制 |
| 1500 | 500 | 服务器内部错误 | 提示稍后重试 |

### 1.6 谁可以调用、能看到什么

| 接口 | 未登录 | 登录用户 | 题目作者 | 管理员 | 返回差异 |
| --- | --- | --- | --- | --- | --- |
| 注册、登录 | ✅ | ✅ | ✅ | ✅ | 无 |
| `GET /auth/me` | ❌ 401 | ✅ | ✅ | ✅ | — |
| `GET /users/{id}` | ✅ | ✅ | ✅ | ✅ | 无（公开资料） |
| `GET /users/me`、改昵称、改密码 | ❌ 401 | ✅ | ✅ | ✅ | — |
| `GET /problems` | ✅ | ✅ | ✅ | ✅ | 无（只含已发布题） |
| `GET /problems/mine` | ❌ 401 | ✅ | ✅ | ✅ | 只返回自己的题，含未发布 |
| `GET /problems/{id}` | ✅ 仅已发布 | ✅ 仅已发布 | ✅ 含未发布 | ✅ 含未发布 | `solved` 随查看者变化 |
| 上传题目 | ❌ 401 | ✅ | ✅ | ✅ | 新题初始状态可能不同 |
| 编辑、删除题目 | ❌ 401 | ❌ 403 | ✅ | ✅ | — |
| 测试数据相关（含查询） | ❌ 401 | ❌ 403 | ✅ | ✅ | — |
| 提交代码 | ❌ 401 | ✅ | ✅ | ✅ | — |
| `GET /submissions/{id}` | ✅ | ✅ | ✅ | ✅ | 代码、编译信息、逐点结果仅本人/作者/管理员可见 |
| `GET /submissions` | ✅ | ✅ | ✅ | ✅ | 无（列表永远不含代码） |
| `GET /meta/**` | ✅ | ✅ | ✅ | ✅ | 无 |
| `/admin/**` | ❌ 401 | ❌ 403 | ❌ 403 | ✅ | — |

> `401` 表示"没有有效身份"，`403` 表示"身份有效但无权操作"。前端遇到 `1002` 统一走登录流程即可。

## 2. 账号

### 2.1 注册

`POST /auth/register` ｜ 公开

创建一个普通用户账号。**注册成功不会返回令牌**，需要再调用登录接口。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 登录名，3-32 位字母/数字/下划线，全局唯一，注册后不可修改 |
| `email` | string | 是 | 邮箱，全局唯一，也可以用邮箱登录 |
| `password` | string | 是 | 8-64 位 |
| `nickname` | string | 否 | 展示昵称，≤32 字；不传时默认用 `username` |

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "password123",
  "nickname": "Alice"
}
```

**响应 `data`**

```json
{
  "id": 12,
  "username": "alice",
  "nickname": "Alice",
  "role": "USER",
  "createdAt": "2026-10-30T11:18:05"
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 用户 id，用于个人主页、按用户筛提交等 |
| `username` | 登录名 |
| `nickname` | 展示名 |
| `role` | `USER` 或 `ADMIN` |
| `createdAt` | 注册时间 |

**常见错误**：`1001`（字段不合法）、`2002`（用户名已存在）、`2003`（邮箱已注册）。

### 2.2 登录

`POST /auth/login` ｜ 公开

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `account` | string | 是 | 用户名或邮箱，二选一都可以 |
| `password` | string | 是 | 登录密码 |

```json
{ "account": "alice", "password": "password123" }
```

**响应 `data`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJpT0ki...",
  "tokenType": "Bearer",
  "expiresIn": 604800,
  "user": {
    "id": 12, "username": "alice", "nickname": "Alice",
    "role": "USER", "createdAt": "2026-10-30T11:18:05"
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `token` | 访问令牌，之后放到请求头 `Authorization: Bearer <token>` |
| `tokenType` | 固定为 `Bearer` |
| `expiresIn` | 有效期（秒），默认 604800 秒（7 天） |
| `user` | 登录用户信息，可直接用于渲染导航栏 |

建议前端保存 `token` 与 `user`；`user.role` 用于决定是否显示后台入口。

**常见错误**：`2005`（账号不存在或密码错误，不区分具体原因）、`2004`（账号已被禁用）、`1001`（参数为空）。

### 2.3 当前登录用户

`GET /auth/me` ｜ 需要登录

返回当前账号信息，可用于刷新页面后恢复登录态。

**响应 `data`**：同 2.1 的响应结构（`id / username / nickname / role / createdAt`）。

**常见错误**：`1002`（未登录或登录已失效）→ 清本地令牌并跳转登录。

## 3. 用户资料

### 3.1 查看个人主页

`GET /users/{id}` ｜ 公开

| 路径参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | 整数 | 用户 id（来自注册/登录响应），不是用户名 |

**响应 `data`**

```json
{
  "id": 12, "username": "alice", "nickname": "Alice", "role": "USER",
  "createdAt": "2026-10-30T11:18:05", "solvedCount": 5
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 用户 id |
| `username` | 登录名 |
| `nickname` | 展示名 |
| `role` | `USER` 或 `ADMIN` |
| `createdAt` | 注册时间 |
| `solvedCount` | 该用户通过过的**不同题目数量**；同一题多次通过只算 1 次 |

未登录与登录看到的资料一致（邮箱等隐私信息不返回）。

**常见错误**：`2001`（用户不存在）。

### 3.2 查看自己的主页

`GET /users/me` ｜ 需要登录

响应与 3.1 相同，`id` 为当前登录用户。

### 3.3 修改昵称

`PATCH /users/me` ｜ 需要登录

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `nickname` | string | 是 | 1-32 个字符 |

```json
{ "nickname": "新的昵称" }
```

**响应 `data`**：昵称更新后的账号信息（`id / username / nickname / role / createdAt`）。

> 昵称会同步显示在题目列表的作者名、提交列表的提交者名中，修改后刷新列表即可看到。

**常见错误**：`1001`（昵称为空或过长）。

### 3.4 修改密码

`PUT /users/me/password` ｜ 需要登录

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `oldPassword` | string | 是 | 当前密码，用于安全校验 |
| `newPassword` | string | 是 | 新密码，8-64 位 |

```json
{ "oldPassword": "password123", "newPassword": "password456" }
```

**响应**：`{ "code": 0, "message": "ok", "data": null }`

> 修改成功后建议前端清除本地登录态并要求用户用新密码重新登录（当前登录凭证不会立即失效）。

**常见错误**：`2006`（原密码不正确）、`1001`（新密码长度不符合要求）。

## 4. 题库

### 4.1 题目列表

`GET /problems` ｜ 公开

题库首页使用，只返回**已发布**的题目，按最新创建排序。列表只给概览信息，不包含题面正文。

**查询参数**（都可选，同时传表示"且"关系）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 按**标题**模糊搜索；不搜题面、标签；空字符串等于不筛选 |
| `difficulty` | string | 难度：`EASY` / `MEDIUM` / `HARD`；传其他值会报 `1000` |
| `tagId` | 整数 | 标签 id（不是标签名，id 从标签列表接口获取）；没有该标签时返回空列表，不报错 |
| `page` / `size` | 整数 | 分页，见 1.4 |

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

### 4.2 我上传的题目

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

### 4.3 题目详情

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
  "updatedAt": "2026-10-30T11:18:32"
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
| `createdAt` / `updatedAt` | 创建时间 / 最后更新时间 |

> `solved=false` 有两种可能：未登录，或确实没通过。请不要用它判断"没做过"，先判断是否有登录态。

**常见错误**：`3001`（题目不存在）、`3002`（题目未发布/已下架）。

### 4.4 上传题目

`POST /problems` ｜ 需要登录

任何登录用户都可以上传题目。上传后可能是"已发布"或"待审核"，以响应里的 `status` 为准：
`PUBLISHED` 表示已进入题库；`PENDING_REVIEW` 表示等待管理员审核（此时只有你自己能看到）。

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
| `tags` | string[] | 否 | 最多 10 个，每个 1-32 字（字母/数字/`_+#.-`）；不存在的新标签会自动创建 |
| `samples` | object[] | 否 | 最多 10 个题面样例，见下 |

`samples` 每一项：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `input` | string | 是 | 样例输入，≤10240 字，可为空字符串 |
| `output` | string | 是 | 样例输出，≤10240 字，可为空字符串 |
| `explanation` | string | 否 | 样例解释 |

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

### 4.5 编辑题目

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

### 4.6 删除题目

`DELETE /problems/{id}` ｜ 仅题目作者或管理员

删除后**不可恢复**，并且会一并删除该题的测试数据与全部提交记录。前端应二次确认。

**响应**：`{ "code": 0, "message": "ok", "data": null }`

**常见错误**：`3001`（题目不存在）、`1003`（不是作者/管理员）。

## 5. 测试数据

> 测试数据是判题用的隐藏数据，和题面里的"样例"是两套内容。
> 所有接口（包括查看）都**只有题目作者和管理员**可以调用：未登录返回 `401`，其他登录用户返回 `403`。

公共路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `problemId` | 整数 | 题目 id |

### 5.1 测试数据列表

`GET /problems/{problemId}/testcases` ｜ 作者/管理员

只返回每条测试数据的基本信息，不返回内容（避免大内容拖慢页面）。

```json
{
  "code": 0, "message": "ok",
  "data": [
    { "id": 101, "ordinal": 1, "inputBytes": 4, "outputBytes": 2, "createdAt": "2026-10-30T11:18:05" },
    { "id": 102, "ordinal": 2, "inputBytes": 8, "outputBytes": 3, "createdAt": "2026-10-30T11:18:05" }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 测试数据 id，查看/删除单条时使用 |
| `ordinal` | 编号，从 1 开始；评测结果的 `cases[].ordinal` 与它对应 |
| `inputBytes` / `outputBytes` | 输入/输出内容的**字节数**（中文等多字节字符会大于字数） |
| `createdAt` | 创建时间 |

### 5.2 查看单条测试数据

`GET /problems/{problemId}/testcases/{testcaseId}` ｜ 作者/管理员

| 路径参数 | 说明 |
| --- | --- |
| `testcaseId` | 测试数据 id，必须属于 `problemId`，否则返回 `3004` |

```json
{
  "code": 0, "message": "ok",
  "data": { "id": 101, "ordinal": 1, "input": "1 2\n", "output": "3\n" }
}
```

| 字段 | 说明 |
| --- | --- |
| `input` | 标准输入原文（保留换行） |
| `output` | 期望输出原文 |

### 5.3 追加一条测试数据

`POST /problems/{problemId}/testcases` ｜ 作者/管理员

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `input` | string | 是 | 标准输入，≤1MB（默认上限）；可以为空字符串，但不能为 `null` |
| `output` | string | 是 | 期望输出，≤1MB（默认上限）；可以为空字符串 |

```json
{ "input": "1 2\n", "output": "3\n" }
```

编号自动取"当前最大编号 + 1"；单题最多 100 条（默认上限），超出返回 `3005`。

**响应 `data`**：刚创建的这条数据的基本信息（结构同 5.1 的元素）。

### 5.4 覆盖全部测试数据

`PUT /problems/{problemId}/testcases` ｜ 作者/管理员

适合"本地准备一批数据，一次上传"的场景。请求体是数组，**数组顺序就是编号顺序**：

```json
[
  { "input": "1 2\n", "output": "3\n" },
  { "input": "100 -50\n", "output": "50\n" }
]
```

- 会替换该题原来的全部测试数据，编号从 1 重新排；
- 传 `[]` 表示清空；清空后该题无法提交/上架（会返回 `3003`）。

**响应 `data`**：替换后的列表（结构同 5.1）。

**常见错误**：`1001`（数组里有元素缺少 `input`/`output`）、`3005`（数量超过上限）。

### 5.5 删除一条测试数据

`DELETE /problems/{problemId}/testcases/{testcaseId}` ｜ 作者/管理员

**响应**：`{ "code": 0, "message": "ok", "data": null }`

删除后其他数据的编号保持不变（可能出现编号不连续，属于正常现象）。

**常见错误**：`3004`（测试数据不存在或不属于该题）。

## 6. 提交与评测

### 6.1 提交代码

`POST /submissions` ｜ 需要登录

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `problemId` | 整数 | 是 | 题目 id |
| `language` | string | 是 | 语言代码，必须取自"可选语言"接口返回的 `code` |
| `code` | string | 是 | 源代码，≤65536 字符（默认上限）；Java 请使用 `public class Main` |

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
| 400 | 4003 | 代码超过 65536 字符 |
| 400 | 4002 / 1000 | 语言不受支持（请使用语言列表里的 `code`） |
| 400 | 1001 | 字段缺失或为空 |

### 6.2 提交详情

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
| `timeUsedMs` | 已执行测试点中的**最大耗时**（毫秒）；编译错误时为 `null` |
| `memoryUsedKb` | 内存峰值（KB）；该次评测未提供该项统计时为 `null`，前端需判空 |
| `compileMessage` | 编译错误信息，只有 `COMPILATION_ERROR` 时有值 |
| `judgeMessage` | 系统级错误说明，只有 `SYSTEM_ERROR` 时有值 |
| `createdAt` / `judgedAt` | 提交时间 / 评测完成时间；未完成时 `judgedAt` 为 `null` |
| `cases` | 逐个测试点的结果；无权限时为 `[]` |

**`cases` 每一项**

| 字段 | 说明 |
| --- | --- |
| `ordinal` | 对应测试数据的编号（从 1 开始） |
| `status` / `statusText` | 该测试点的结果 |
| `timeUsedMs` | 该测试点耗时 |
| `memoryUsedKb` | 该测试点的内存峰值（KB）；未提供该项统计时为 `null` |
| `message` | 失败原因：超时会写"超过时间限制 xxx ms"；运行错误会给出程序错误输出；答案错误一般为 `null`。不会包含期望输出 |

> 评测遇到第一个未通过的测试点就会停止，所以 `cases` 可能少于测试数据总数。
> 例如答案错误时通常只有 1 条 `WRONG_ANSWER`。

### 6.3 提交列表

`GET /submissions` ｜ 公开

用于题目页的"最近提交"、个人主页的提交记录、评测排队查询。列表**不返回代码和逐点结果**。

**查询参数**（都可选，"且"关系；按提交时间倒序）

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `problemId` | 整数 | 只看某道题的提交 |
| `userId` | 整数 | 只看某个用户的提交 |
| `status` | string | 只看某个状态，例如 `ACCEPTED`、`PENDING`；非法值返回 `1000` |
| `page` / `size` | 整数 | 分页，见 1.4 |

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

### 6.4 评测状态

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
| `SYSTEM_ERROR` | 系统错误 | 评测环境异常，可提示用户稍后重试 | 是 |

建议配色：`ACCEPTED` 绿色；`WRONG_ANSWER` / `TIME_LIMIT_EXCEEDED` / `RUNTIME_ERROR` / `COMPILATION_ERROR` 红色；`PENDING` / `JUDGING` 灰色。

### 6.5 轮询评测结果（示例）

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

## 7. 参考数据

这两个接口都是公开的，前端启动时拉取一次即可。

### 7.1 可选语言

`GET /meta/languages`

```json
{
  "code": 0, "message": "ok",
  "data": [
    { "code": "JAVA", "displayName": "Java 21" },
    { "code": "CPP", "displayName": "C++17" },
    { "code": "C", "displayName": "C11" },
    { "code": "PYTHON3", "displayName": "Python 3" }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `code` | 提交时 `language` 必须使用这个值 |
| `displayName` | 下拉框展示名称 |

### 7.2 标签列表

`GET /meta/tags`

```json
{
  "code": 0, "message": "ok",
  "data": [
    { "id": 1, "name": "入门" },
    { "id": 3, "name": "数学" },
    { "id": 7, "name": "图论" }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 标签 id，用于 `GET /problems?tagId=` 筛选 |
| `name` | 标签名；上传题目时直接传名称，新建的标签会自动出现在这里 |

## 8. 管理后台

> 以下接口只有管理员（`role=ADMIN`）可以调用：未登录返回 `401`，普通用户返回 `403`。
> 管理员不能修改自己的角色、不能封禁自己。

### 8.1 用户列表

`GET /admin/users` ｜ 管理员

**查询参数**

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 用户名、昵称、邮箱任一模糊匹配；为空不筛选 |
| `page` / `size` | 整数 | 分页 |

**响应 `data`**（按注册时间倒序）

```json
{
  "items": [
    { "id": 13, "username": "bob", "nickname": "Bob", "role": "USER", "createdAt": "2026-10-30T11:20:05" },
    { "id": 12, "username": "alice", "nickname": "Alice", "role": "USER", "createdAt": "2026-10-30T11:18:04" }
  ],
  "total": 2, "page": 1, "size": 20
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 用户 id，用于改角色/封禁 |
| `username` / `nickname` | 登录名 / 展示名 |
| `role` | `USER` 或 `ADMIN` |
| `createdAt` | 注册时间 |

> 目前列表**不返回邮箱和封禁状态**。管理页如果只需要操作角色/封禁，可直接用这些字段；
> 如果需要展示"谁被封禁"，请与后端约定新增字段后再实现对应 UI。

### 8.2 修改用户角色

`PATCH /admin/users/{id}/role` ｜ 管理员

| 路径参数 | 说明 |
| --- | --- |
| `id` | 目标用户 id |

**请求体**

```json
{ "role": "ADMIN" }
```

`role` 取值：`USER` / `ADMIN`。

**响应**：`{ "code": 0, "message": "ok", "data": null }`

| 错误 | 场景 |
| --- | --- |
| `1000` | 修改自己的角色（提示"不能修改自己的角色"）；或 `role` 取值非法 |
| `2001` | 目标用户不存在 |

角色变更在对方下一次请求时生效，对方无需重新登录。

### 8.3 封禁 / 解封用户

`PATCH /admin/users/{id}/status` ｜ 管理员

**请求体**

```json
{ "status": "BANNED" }
```

`status` 取值：`ACTIVE`（正常）/ `BANNED`（封禁）。

**响应**：`{ "code": 0, "message": "ok", "data": null }`

**封禁后的表现**：

| 场景 | 结果 |
| --- | --- |
| 被封禁用户尝试登录 | 登录接口返回 `403` + `2004` |
| 被封禁用户已有的登录状态 | 立即失效，调用需要登录的接口返回 `401` |
| 被封禁用户浏览题库等公开页面 | 正常（按未登录处理） |
| 解封后 | 可用原密码正常登录 |

| 错误 | 场景 |
| --- | --- |
| `1000` | 封禁自己（提示"不能封禁自己"）；或 `status` 取值非法 |
| `2001` | 目标用户不存在 |

### 8.4 题目审核列表

`GET /admin/problems` ｜ 管理员

**查询参数**

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 标题模糊搜索 |
| `status` | string | 按状态筛选：`DRAFT` / `PENDING_REVIEW` / `PUBLISHED` / `HIDDEN`；不传表示全部状态 |
| `page` / `size` | 整数 | 分页 |

**响应 `data`**：结构同 4.1 的题目列表，每项都带 `status`：

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

审核入口：用 `status=PENDING_REVIEW` 拉取待审核列表，点击可进入 `GET /problems/{id}` 查看题面（管理员可查看未发布的题）。

### 8.5 上架 / 下架题目

`PATCH /admin/problems/{id}/status` ｜ 管理员

**请求体**

| 值 | 含义 |
| --- | --- |
| `{ "status": "PUBLISHED" }` | 通过审核 / 重新上架 |
| `{ "status": "HIDDEN" }` | 驳回 / 下架 |

**响应**：`{ "code": 0, "message": "ok", "data": null }`

**效果**：

- 上架后题目出现在公开题库，所有人可见、可提交；
- 下架后从公开题库消失，未登录和其他用户访问详情返回 `403`（`3002`），已有提交记录仍可查询。

| 错误 | 场景 |
| --- | --- |
| `3003` | 上架时题目还没有测试数据（提示"题目至少需要一个测试点才能发布"） |
| `1000` | 传了 `PUBLISHED` / `HIDDEN` 以外的状态 |
| `3001` | 题目不存在 |

## 9. 附录

### 9.1 枚举速查

| 枚举 | 取值 | 用在哪里 |
| --- | --- | --- |
| 用户角色 | `USER` / `ADMIN` | `user.role`、用户列表、权限判断 |
| 用户状态 | `ACTIVE` / `BANNED` | 管理后台封禁/解封 |
| 难度 | `EASY` / `MEDIUM` / `HARD` | 题目创建、筛选、展示 |
| 题目状态 | `DRAFT` / `PENDING_REVIEW` / `PUBLISHED` / `HIDDEN` | 我的题目、审核列表、题目详情 |
| 语言 | `JAVA` / `CPP` / `C` / `PYTHON3` | 提交、语言下拉框 |
| 评测状态 | 见 6.4 | 提交列表、提交详情 |

题目状态的含义：

| 状态 | 含义 | 公开可见 |
| --- | --- | --- |
| `DRAFT` | 草稿 | ❌ |
| `PENDING_REVIEW` | 待审核 | ❌ |
| `PUBLISHED` | 已发布 | ✅ |
| `HIDDEN` | 已下架 | ❌ |

### 9.2 可能为 null 的字段

| 位置 | 字段 | 说明 |
| --- | --- | --- |
| 题目详情 | `inputFormat`、`outputFormat`、`hint`、`source` | 可能未填写 |
| 题目详情 | `samples[].explanation` | 可能未填写 |
| 题目详情 | `tags`、`samples` | 空数组（`[]`），不会是 `null` |
| 提交详情 | `code` | 无权限查看 |
| 提交详情 | `compileMessage` | 只有编译错误时有值 |
| 提交详情 | `judgeMessage` | 只有系统错误时有值 |
| 提交详情 | `timeUsedMs` | 编译错误或尚未开始评测 |
| 提交详情 | `memoryUsedKb` | 该次评测未提供内存统计时为 `null` |
| 提交详情 | `judgedAt` | 尚未评测完成 |
| 提交详情 | `cases` | 无权限或编译错误时为 `[]` |
| 列表 | `items` | 无数据或超出页码时为 `[]`，此时 `total` 可能不为 0 |

> 数值字段（`timeUsedMs`、`memoryUsedKb`）为 `null` 时，建议统一显示为 `-`，不要直接渲染 `null`。

### 9.3 常见现象与处理

| 现象 | 原因 | 前端处理 |
| --- | --- | --- |
| 打开题目返回 `403` + `3002` | 题目未发布或已下架 | 提示"题目不可见"，不要显示"无权限" |
| 提交返回 `400` + `3003` | 题目还没有测试数据 | 提交按钮置灰，提示等待作者补充 |
| 上传题目后在题库看不到 | 题目进入待审核（`PENDING_REVIEW`） | 去"我的题目"查看，并展示状态标签 |
| 编辑题目后部分内容变空 | 编辑接口是整体替换 | 表单提交时带上所有字段（含标签、样例） |
| 提交后一直 `PENDING` / `JUDGING` | 评测较慢或排队 | 轮询 30-60 秒后提示"稍后到我的提交查看" |
| 未登录时 `solved` 全是 `false` | 未登录无法判断通过情况 | 不要显示"未通过"，改为显示"登录后查看" |
| token 过期后页面像"未登录" | 公开接口不会因 token 失效报错 | 请求 `/auth/me` 判断登录态，失败则引导登录 |
| 管理页看不到谁被封禁 | 用户列表不含封禁状态 | 见 8.1 的说明，必要时与后端约定扩展字段 |
| 测试数据编号不连续 | 删除中间数据后编号不重排 | 正常现象，按编号展示即可 |

### 9.4 页面与接口对照

| 页面 | 需要的接口 |
| --- | --- |
| 登录 / 注册 | `POST /auth/login`、`POST /auth/register`、`GET /auth/me` |
| 题库首页 | `GET /problems`、`GET /meta/tags` |
| 题目详情 | `GET /problems/{id}`、`GET /submissions?problemId={id}`、`POST /submissions` |
| 提交结果 | `GET /submissions/{id}`（轮询）、`GET /submissions?userId={id}` |
| 我的题目 | `GET /problems/mine`、`PUT /problems/{id}`、`DELETE /problems/{id}` |
| 上传/编辑题目 | `GET /meta/tags`、`GET /meta/languages`、`POST /problems`、`PUT /problems/{id}` |
| 测试数据管理 | `GET /problems/{problemId}/testcases`、`GET/POST/PUT/DELETE /problems/{problemId}/testcases/**` |
| 个人主页 | `GET /users/{id}`、`GET /submissions?userId={id}` |
| 账号设置 | `PATCH /users/me`、`PUT /users/me/password` |
| 后台 · 审核 | `GET /admin/problems?status=PENDING_REVIEW`、`GET /problems/{id}`、`PATCH /admin/problems/{id}/status` |
| 后台 · 用户 | `GET /admin/users`、`PATCH /admin/users/{id}/role`、`PATCH /admin/users/{id}/status` |







