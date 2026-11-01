# 8. 管理后台

> 返回目录：[iOJ 前端接口文档（v1）](README.md)

> 以下接口只有管理员（`role=ADMIN`）可以调用：未登录返回 `401`，普通用户返回 `403`。
> 管理员不能修改自己的角色、不能封禁自己。

## 8.1 用户列表

`GET /admin/users` ｜ 管理员

**查询参数**

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 用户名、昵称、邮箱任一模糊匹配；为空不筛选 |
| `page` / `size` | 整数 | 分页 |

**响应 `data`**（按 id 倒序，等价于注册时间倒序）

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

## 8.2 修改用户角色

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

## 8.3 封禁 / 解封用户

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

## 8.4 题目审核列表

`GET /admin/problems` ｜ 管理员

**查询参数**

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 标题模糊搜索 |
| `status` | string | 按状态筛选：`DRAFT` / `PENDING_REVIEW` / `PUBLISHED` / `HIDDEN`；不传表示全部状态；传其他值返回 `1000` |
| `page` / `size` | 整数 | 分页 |

**响应 `data`**：结构同 [4.1 题目列表](04-problems.md#41-题目列表)，每项都带 `status`：

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

## 8.5 上架 / 下架题目

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
