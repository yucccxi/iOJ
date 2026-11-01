# 2. 账号

> 返回目录：[iOJ 前端接口文档（v1）](README.md)

## 2.1 注册

`POST /auth/register` ｜ 公开

创建一个普通用户账号。**注册成功不会返回令牌**，需要再调用登录接口。

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | 登录名，3-32 位字母/数字/下划线，全局唯一，注册后不可修改 |
| `email` | string | 是 | 邮箱，≤128 字符，全局唯一，也可以用邮箱登录 |
| `password` | string | 是 | 8-64 位 |
| `nickname` | string | 否 | 展示昵称，≤32 字；不传或传空白时默认用 `username` |

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

## 2.2 登录

`POST /auth/login` ｜ 公开

**请求体**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `account` | string | 是 | 用户名或邮箱，二选一都可以；≤128 字符 |
| `password` | string | 是 | 登录密码，≤64 字符 |

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

## 2.3 当前登录用户

`GET /auth/me` ｜ 需要登录

返回当前账号信息，可用于刷新页面后恢复登录态。

**响应 `data`**：同 2.1 的响应结构（`id / username / nickname / role / createdAt`）。

**常见错误**：`1002`（未登录或登录已失效）→ 清本地令牌并跳转登录。
