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

**响应 `data`**：结构与 `/catalog` 的 `LOCAL` 项相近（但这里是完整题目摘要，见 [10.1 搜索题目](10-catalog.md#101-搜索题目)），且每项都带 `status`：

```json
{
  "items": [
    {
      "id": 13, "title": "待审核的题", "difficulty": "EASY", "rating": 800, "status": "PENDING_REVIEW",
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

## 8.6 标签管理

`/admin/tags` ｜ 管理员

标签是**共享字典**：本地题与外部 OJ 链接都用它，出题时只能选，不能新建。列表（含使用量）走公开的 [`GET /meta/tags`](07-meta.md#72-标签列表)，这里只负责写操作。

| 接口 | 说明 |
| --- | --- |
| `POST /admin/tags` | 新建，请求体 `{ "name": "图论" }` |
| `PUT /admin/tags/{id}` | 改名，请求体同上 |
| `DELETE /admin/tags/{id}` | 删除；在用默认拒绝，`?force=true` 先解除关联再删 |

**响应 `data`（POST / PUT，单个标签）**

```json
{ "id": 3, "name": "数学", "problemCount": 12 }
```

| 字段 | 说明 |
| --- | --- |
| `id` | 标签 id，出题时作为 `tagIds` 的元素 |
| `name` | 标签名，1-32 位字母/数字/中文/`_+#.-` |
| `problemCount` | 使用该标签的题目数（本地题 + 外部 OJ 链接）；新建时为 `0` |

**规则**

- 重名（大小写不敏感）→ `409`（`1005` "标签已存在"）；
- 删除仍在使用的标签 → `409`（`1005`，提示"标签仍被 N 道题使用"）；
- `DELETE ...?force=true`：先在 `problem_tags` 与 `external_problem_tags` 里解除关联，再删除标签，单事务；
- 改名后所有题目的 `tags[].name` 立即变化（题目只存 `tag_id`）。

| 错误 | 场景 |
| --- | --- |
| `1001` | 名称为空 / 超过 32 字符 / 含非法字符 |
| `1004` | 标签不存在 |
| `1005` | 重名，或标签仍在使用（且没传 `force=true`） |
| `1003` | 非管理员 |

## 8.7 来源管理

`/admin/sources` ｜ 管理员

来源同样是共享字典（比赛、教材、OJ 平台都在这张表里），本地题与外部题都用它。列表（含使用量）走公开的 [`GET /meta/sources`](07-meta.md#73-来源列表)，这里只负责写操作。

| 接口 | 说明 |
| --- | --- |
| `POST /admin/sources` | 新建，请求体 `{ "name": "Codeforces" }` |
| `PUT /admin/sources/{id}` | 改名，请求体同上 |
| `DELETE /admin/sources/{id}` | 删除；被任何题目引用时拒绝 |

**响应 `data`（POST / PUT，单个来源）**

```json
{ "id": 7, "name": "Codeforces", "createdAt": "2026-11-01T09:00:00", "problemCount": 15 }
```

| 字段 | 说明 |
| --- | --- |
| `id` | 来源 id，出题时作为 `sourceId` |
| `name` | 来源名，1-128 字符 |
| `createdAt` | 创建时间 |
| `problemCount` | 引用该来源的题目数（本地题 + 外部 OJ 链接）；新建时为 `0` |

**规则**

- 重名（大小写不敏感）→ `409`（`1005` "来源已存在"）；
- 删除仍被引用的来源 → `409`（`1005` "来源仍被 N 道题使用"）；**不支持** `force`，需要先把相关题目改到别的来源（或删掉）；
- 改名后所有题目的 `sourceName` 立即变化（题目只存 `source_id`）。

| 错误 | 场景 |
| --- | --- |
| `1001` | 名称为空 / 超过 128 字符 |
| `1004` | 来源不存在 |
| `1005` | 重名，或来源仍被使用 |
| `1003` | 非管理员 |

