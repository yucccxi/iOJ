# 3. 用户资料

> 返回目录：[iOJ 前端接口文档（v1）](README.md)

## 3.1 查看个人主页

`GET /users/{id}` ｜ 公开

| 路径参数 | 类型 | 说明 |
| --- | --- | --- |
| `id` | 整数 | 用户 id（来自注册/登录响应），不是用户名 |

**响应 `data`**

```json
{
  "id": 12, "username": "alice", "nickname": "Alice", "role": "USER",
  "createdAt": "2026-10-30T11:18:05", "solvedCount": 5, "contributedCount": 3
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
| `contributedCount` | 该用户**贡献的题目数量**（只统计已发布 `PUBLISHED` 的题）；题目被下架或删除后不再计入，不会是 `null` |

未登录与登录看到的资料一致（邮箱等隐私信息不返回）。

**常见错误**：`2001`（用户不存在）。

## 3.2 查看自己的主页

`GET /users/me` ｜ 需要登录

响应与 3.1 相同（含 `solvedCount` 与 `contributedCount`），`id` 为当前登录用户。

## 3.3 修改昵称

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

## 3.4 修改密码

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
