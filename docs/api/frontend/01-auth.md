# 账号与用户

本文说明图形验证码、邮箱验证码、注册登录、找回密码和个人资料接口，读者为前端与外部调用方。通用响应、分页和错误码见 [API 通用约定](../README.md)。

> 返回目录：[前端 API 索引](README.md)

> **部署开关**：邮箱验证码流程只在 `ioj.email-code.enabled=true` 时生效。关闭后，`POST /auth/email-codes`、`POST /auth/password-reset-codes`、`POST /auth/password-resets` 返回 `400` / `1000`「邮箱验证功能未启用」，注册不再需要 `emailCode`。
>
> 图形验证码由 `ioj.captcha.enabled` 单独控制。关闭后，登录和两个发码接口不再要求 `captchaId` / `captchaCode`，`POST /captcha` 返回 `1000`。

## 1. 图形验证码

### 1.1 创建图形验证码

`POST /api/v1/captcha` ｜ 公开

为登录或发送邮箱验证码申请一张一次性图片验证码。答案只存在于图片中，响应不会返回明文。

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `purpose` | string | 是 | — | `LOGIN` 或 `EMAIL_SENDING`，必须与后续业务接口匹配 |

```json
{
  "purpose": "LOGIN"
}
```

`purpose` 的使用场景：

| purpose | 使用接口 |
| --- | --- |
| `LOGIN` | `POST /api/v1/auth/login` |
| `EMAIL_SENDING` | `POST /api/v1/auth/email-codes`、`POST /api/v1/auth/password-reset-codes` |

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "captchaId": "7d5c9a2e4b8f4c1d9e6a3b7f2c8d1e5a",
    "image": "data:image/png;base64,<png-base64>",
    "expiresInSeconds": 120
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.captchaId` | string | 本次验证码 id，提交业务请求时原样带回 |
| `data.image` | string | PNG 图片的 data URL，可直接放进 `<img src>` |
| `data.expiresInSeconds` | integer | 有效期（秒），默认 120 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1000` | 图形验证码功能未启用；`purpose` 不是合法枚举；请求体不是合法 JSON |
| 400 | `1001` | `purpose` 缺失 |
| 429 | `1006` | 同一 IP 申请过于频繁 |

**行为说明**

- 验证码一次性使用，验证成功后立即失效；输错达到上限、过期或已使用后需要重新获取。
- `purpose` 与业务接口绑定：`LOGIN` 的验证码不得用于发邮件，反之亦然。
- 响应带 `Cache-Control: no-store`，前端不得缓存图片或复用旧 `captchaId`。
- 默认同一 IP 每分钟最多 60 次、每小时最多 600 次签发；共享 NAT 环境下可调大配置。

## 2. 邮箱验证码

### 2.1 发送注册验证码

`POST /api/v1/auth/email-codes` ｜ 公开

给尚未注册的邮箱发送 6 位注册验证码。验证码只通过邮件投递，不在响应中返回。

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `email` | string | 是 | — | 要验证的邮箱，≤128 字符；服务端 trim 并转小写 |
| `captchaId` | string | 启用图形验证码时是 | — | `purpose=EMAIL_SENDING` 的验证码 id |
| `captchaCode` | string | 启用图形验证码时是 | — | 图形验证码内容，忽略大小写，≤16 字符 |

```json
{
  "email": "alice@example.com",
  "captchaId": "7d5c9a2e4b8f4c1d9e6a3b7f2c8d1e5a",
  "captchaCode": "A7K2P"
}
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "expiresInSeconds": 600,
    "resendAfterSeconds": 60
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.expiresInSeconds` | integer | 验证码有效期，默认 600 秒 |
| `data.resendAfterSeconds` | integer | 还需等待多少秒才能再次发送，默认 60 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1000` | 邮箱验证功能未启用 |
| 400 | `1001` | 邮箱为空、格式不正确或超长 |
| 400 | `2010` | 图形验证码缺失、错误、过期、已使用或用途不匹配 |
| 409 | `2003` | 该邮箱已注册 |
| 429 | `2008` | 冷却中，或超出该邮箱每小时发送上限 |
| 503 | `2009` | SMTP 发送失败 |

**行为说明**

- 默认同一邮箱 60 秒内只能发一次、每小时最多 5 次；注册码与找回密码码共用这份配额。
- 验证码默认 10 分钟有效、最多输错 5 次；第 5 次输错会立即作废，之后即使输对也要重新获取。
- 只有最新一封邮件的验证码有效；重新获取会使之前的验证码作废。
- 发送失败时服务端删除刚写入的验证码记录，不占用每小时配额。

### 2.2 发送找回密码验证码

`POST /api/v1/auth/password-reset-codes` ｜ 公开

给已注册且未封禁的邮箱发送找回密码验证码。请求体和成功响应与 2.1 相同，图形验证码同样使用 `purpose=EMAIL_SENDING`。

```json
{
  "email": "alice@example.com",
  "captchaId": "7d5c9a2e4b8f4c1d9e6a3b7f2c8d1e5a",
  "captchaCode": "A7K2P"
}
```

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1000` | 邮箱验证功能未启用 |
| 400 | `1001` | 邮箱为空、格式不正确或超长 |
| 400 | `2010` | 图形验证码无效 |
| 404 | `2001` | 该邮箱尚未注册 |
| 403 | `2004` | 账号已被封禁 |
| 429 | `2008` | 冷却中，或超出每小时上限 |
| 503 | `2009` | SMTP 发送失败 |

> 该接口会明确告知邮箱是否已注册。同样的信息也能从 `POST /auth/register` 的 `2003` 观察到，这是有意的产品选择。

### 2.3 通过验证码重置密码

`POST /api/v1/auth/password-resets` ｜ 公开

用 2.2 收到的验证码设置新密码。成功后旧密码立即失效，该账号之前签发的所有 JWT 也立即失效。

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `email` | string | 是 | — | 注册邮箱，≤128 字符 |
| `emailCode` | string | 是 | — | 6 位数字验证码 |
| `newPassword` | string | 是 | — | 8-64 位 |

```json
{
  "email": "alice@example.com",
  "emailCode": "482915",
  "newPassword": "newpassword456"
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
| 400 | `1000` | 邮箱验证功能未启用 |
| 400 | `1001` | 字段缺失、邮箱格式错误或新密码长度不符合要求 |
| 404 | `2001` | 该邮箱尚未注册 |
| 403 | `2004` | 账号已被封禁 |
| 400 | `2007` | 验证码错误、过期、已使用、输错超限，或使用了注册验证码 |

**行为说明**

- 验证码与用途绑定：注册验证码不得用于重置密码，反之亦然。
- 验证码一次性使用；修改密码会使所有旧 token 失效，需要重新登录。
- 重置成功后不会返回新 token。

## 3. 注册与登录

### 3.1 注册

`POST /api/v1/auth/register` ｜ 公开

创建普通用户账号。启用邮箱验证时，必须先通过 2.1 获取验证码并在请求中带上；注册成功不会自动登录。

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `username` | string | 是 | — | 3-32 位字母、数字或下划线，全局唯一，注册后不可修改 |
| `email` | string | 是 | — | ≤128 字符，全局唯一；trim 并转小写，可用于登录 |
| `password` | string | 是 | — | 8-64 位 |
| `nickname` | string | 否 | `null` | 展示昵称，≤32 字；不传或空白时使用 `username` |
| `emailCode` | string | 启用邮箱验证时是 | `null` | 6 位数字验证码；关闭邮箱验证时可省略 |

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "password123",
  "nickname": "Alice",
  "emailCode": "482915"
}
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 12,
    "username": "alice",
    "nickname": "Alice",
    "role": "USER",
    "createdAt": "2026-10-30T11:18:05"
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.id` | integer | 用户 id |
| `data.username` | string | 登录名 |
| `data.nickname` | string | 展示名 |
| `data.role` | string | 注册固定为 `USER` |
| `data.createdAt` | string | 注册时间 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1001` | 字段缺失、格式不合法；`emailCode` 为空字符串或非 6 位数字 |
| 409 | `2002` | 用户名已存在 |
| 409 | `2003` | 邮箱已注册 |
| 400 | `2007` | 验证码完全不传、错误、过期、已使用或输错超限 |
| 409 | `1005` | 并发注册同一用户名/邮箱，由唯一索引兜底 |

**行为说明**

- 角色固定为 `USER`、状态固定为 `ACTIVE`，请求体无法指定角色。
- 验证码校验在写入用户之前；账号插入与验证码消费在同一事务内，插入失败不会浪费验证码。
- 用户名/邮箱唯一性以数据库唯一索引为最终保证。

### 3.2 登录

`POST /api/v1/auth/login` ｜ 公开

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `account` | string | 是 | — | 用户名或邮箱，≤128 字符；服务端不 trim、不转换大小写 |
| `password` | string | 是 | — | ≤64 字符 |
| `captchaId` | string | 启用图形验证码时是 | — | `purpose=LOGIN` 的验证码 id |
| `captchaCode` | string | 启用图形验证码时是 | — | 图形验证码内容，忽略大小写 |

```json
{
  "account": "alice",
  "password": "password123",
  "captchaId": "7d5c9a2e4b8f4c1d9e6a3b7f2c8d1e5a",
  "captchaCode": "A7K2P"
}
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "<jwt>",
    "tokenType": "Bearer",
    "expiresIn": 604800,
    "user": {
      "id": 12,
      "username": "alice",
      "nickname": "Alice",
      "role": "USER",
      "createdAt": "2026-10-30T11:18:05"
    }
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.token` | string | 访问令牌 |
| `data.tokenType` | string | 固定 `Bearer` |
| `data.expiresIn` | integer | 有效期秒数，默认 604800（7 天） |
| `data.user` | object | 登录用户信息 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1001` | 字段缺失或超长 |
| 400 | `2010` | 图形验证码缺失、错误、过期、已使用或用途不匹配 |
| 401 | `2005` | 账号不存在或密码错误（不区分具体原因） |
| 403 | `2004` | 账号已被封禁（仅在密码正确时返回） |

**行为说明**

- 图形验证码在查询账号之前校验并消费；密码错误后验证码也已被消费，需要重新获取。
- 登录成功更新 `last_login_at`，返回新 token；服务端不保存 session。
- 账号或密码错误统一返回 `2005`，不暴露账号是否存在。

### 3.3 当前登录账号

`GET /api/v1/auth/me` ｜ 需要登录

返回当前账号信息，用于刷新页面后恢复登录态。

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 12,
    "username": "alice",
    "nickname": "Alice",
    "role": "USER",
    "createdAt": "2026-10-30T11:18:05"
  }
}
```

字段同 3.1 的 `UserVO`；`role` 是当前数据库中的实际角色。

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 401 | `1002` | 未登录、token 过期/失效或账号被封禁 |

### 3.4 登出所有设备

`POST /api/v1/auth/logout-all` ｜ 需要登录

让该账号之前签发的所有 JWT 立即失效，并为当前设备返回一个新 token。

**请求体**：无。

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "<jwt>",
    "tokenType": "Bearer",
    "expiresIn": 604800,
    "user": {
      "id": 12,
      "username": "alice",
      "nickname": "Alice",
      "role": "USER",
      "createdAt": "2026-10-30T11:18:05"
    }
  }
}
```

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 401 | `1002` | 当前 token 无效或已过期 |

**行为说明**

- 服务端使该账号之前签发的所有 token 失效；旧 token 在下一次请求时被拒绝。
- 响应里的新 token 可以继续使用；其他设备需要重新登录。
- 不返回“已登出设备数量”。

## 4. 用户资料

### 4.1 查看个人主页

`GET /api/v1/users/{id}` ｜ 公开

| 路径参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | integer | 用户 id，不是用户名 |

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 12,
    "username": "alice",
    "nickname": "Alice",
    "role": "USER",
    "createdAt": "2026-10-30T11:18:05",
    "solvedCount": 5,
    "contributedCount": 3
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.id` | integer | 用户 id |
| `data.username` | string | 登录名 |
| `data.nickname` | string | 展示名 |
| `data.role` | string | `USER` / `ADMIN` |
| `data.createdAt` | string | 注册时间 |
| `data.solvedCount` | integer | 通过过的不同题目数；同一题多次通过只算 1 次 |
| `data.contributedCount` | integer | 已发布 `PUBLISHED` 的贡献题目数；下架或删除后不再计入 |

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 404 | `2001` | 用户不存在 |

**行为说明**

- 不返回邮箱、密码哈希、封禁状态等敏感信息；匿名与登录看到的资料相同。
- `contributedCount` 只统计已发布题目，不是“上传过的所有题”。

### 4.2 查看自己的主页

`GET /api/v1/users/me` ｜ 需要登录

响应与 4.1 相同，`id` 为当前登录用户。

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 401 | `1002` | 未登录或 token 失效 |

### 4.3 修改昵称

`PATCH /api/v1/users/me` ｜ 需要登录

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `nickname` | string | 是 | — | 1-32 个字符，首尾空白会被去掉 |

```json
{
  "nickname": "新的昵称"
}
```

**成功响应**

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 12,
    "username": "alice",
    "nickname": "新的昵称",
    "role": "USER",
    "createdAt": "2026-10-30T11:18:05"
  }
}
```

**失败情况**

| HTTP | 错误码 | 触发条件 |
| --- | --- | --- |
| 400 | `1001` | 昵称为空或超过 32 字 |
| 401 | `1002` | 未登录或 token 失效 |

**行为说明**

- 昵称会同步出现在题库作者名、提交列表提交者名中；刷新列表即可看到。
- 不修改用户名、邮箱或角色。

### 4.4 修改密码

`PUT /api/v1/users/me/password` ｜ 需要登录

**请求体**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `oldPassword` | string | 是 | — | 当前密码 |
| `newPassword` | string | 是 | — | 新密码，8-64 位 |

```json
{
  "oldPassword": "password123",
  "newPassword": "password456"
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
| 400 | `2006` | 原密码不正确 |
| 400 | `1001` | 新密码长度不符合要求 |
| 401 | `1002` | 未登录或 token 失效 |

**行为说明**

- 修改成功后**当前 token 和该账号之前签发的所有 token 立即失效**；前端必须清除本地登录态并用新密码重新登录。
- 忘记旧密码时使用 2.2 + 2.3 的邮箱验证码流程。
- 只想退出其他设备、保留当前设备登录时，使用 3.4 的 `logout-all`。

## 相关文档

- [前端 API 索引](README.md)
- [统一题库与参考数据](02-catalog.md)
- [管理后台](05-admin.md)（管理员用户管理）
- [API 通用约定](../README.md)
