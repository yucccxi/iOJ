# 前端 API 索引

本文面向前端与外部调用方，按业务领域列出 iOJ 的 HTTP API 契约：参数、权限、响应字段、错误码和副作用。通用约定（认证、统一响应、分页、错误码总表）见 [API 通用约定](../README.md)。

> 返回目录：[API 通用约定](../README.md) ｜ [文档总索引](../../README.md)

## 1. 阅读顺序

| 顺序 | 文档 | 覆盖资源 |
| --- | --- | --- |
| 1 | [账号与用户](01-auth.md) | 图形验证码、邮箱验证码、注册、登录、找回密码、个人资料、管理员用户管理 |
| 2 | [统一题库与参考数据](02-catalog.md) | 本地题 + 外部 OJ 的统一搜索、语言、标签、scope |
| 3 | [题目与测试数据](03-problems.md) | 题目详情/CRUD、判题器、隐藏测试点、我的题目 |
| 4 | [提交与评测](04-submissions.md) | 提交代码、提交列表/详情、评测状态与逐点结果 |
| 5 | [管理后台](05-admin.md) | 题目审核、用户角色/封禁、标签、scope、判题 key、判题节点 |
| 6 | [参考与页面映射](06-reference.md) | 枚举、可能为 `null` 的字段、常见现象、页面接口对照 |

## 2. 调用方与认证

| 调用方 | 凭据 | 使用范围 |
| --- | --- | --- |
| 匿名访客 | 无 | 公开题库、已发布题目详情、公开提交列表/详情（敏感字段被隐藏） |
| 登录用户 | `Authorization: Bearer <jwt>` | 提交、上传题目、维护自己的资料 |
| 管理员 | 同上，且 `role=ADMIN` | `/api/v1/admin/**` |
| 判题 Agent | `Authorization: Bearer <judge-key>` | `/internal/judge/**`，见 [内部判题 API](../judge/README.md) |

令牌缺失或过期时，公开接口按未登录处理；需要登录的接口返回 `401` + `1002`。修改密码、重置密码、`logout-all` 会让旧 token 立即失效。

## 3. 页面与接口对照

| 页面 | 需要的接口 |
| --- | --- |
| 登录 / 注册 | `POST /captcha`、`POST /auth/email-codes`、`POST /auth/register`、`POST /auth/login`、`GET /auth/me` |
| 找回密码 | `POST /captcha`、`POST /auth/password-reset-codes`、`POST /auth/password-resets` |
| 题库首页 | `GET /catalog?scopeId={LOCAL id}`、`GET /meta/tags`、`GET /meta/scopes` |
| 统一搜索（本地 + 外部） | `GET /catalog`、`GET /meta/tags`、`GET /meta/scopes` |
| 题目详情 | `GET /problems/{id}`、`GET /submissions?problemId={id}`、`POST /submissions` |
| 提交结果 | `GET /submissions/{id}`（轮询）、`GET /submissions?userId={id}` |
| 我的题目 | `GET /problems/mine`、`PUT /problems/{id}`、`DELETE /problems/{id}` |
| 上传 / 编辑题目 | `GET /meta/tags`、`GET /meta/languages`、`POST /problems`、`PUT /problems/{id}` |
| 测试数据管理 | `GET/POST/PUT/DELETE /problems/{problemId}/testcases`、`GET/DELETE .../{testcaseId}` |
| 判题器管理 | `GET /problems/{id}/checker`、`PUT /problems/{id}/checker`（作者/管理员） |
| 个人主页 | `GET /users/{id}`、`GET /submissions?userId={id}` |
| 账号设置 | `PATCH /users/me`、`PUT /users/me/password`、`POST /auth/logout-all` |
| 后台 · 审核 | `GET /admin/problems?status=PENDING_REVIEW`、`GET /problems/{id}`、`PATCH /admin/problems/{id}/status` |
| 后台 · 用户 | `GET /admin/users`、`PATCH /admin/users/{id}/role`、`PATCH /admin/users/{id}/status` |
| 后台 · 标签 | 列表 `GET /meta/tags`；写操作 `POST /admin/tags`、`PUT /admin/tags/{id}`、`DELETE /admin/tags/{id}` |
| 后台 · Scope | 列表 `GET /meta/scopes`；写操作 `POST /admin/scopes`、`PUT /admin/scopes/{id}`、`DELETE /admin/scopes/{id}` |
| 后台 · 判题机 | `GET/POST /admin/judge-keys`、`PATCH/DELETE /admin/judge-keys/{id}`、`GET /admin/judge-nodes` |

## 4. 关键流程

### 4.1 注册与登录

1. 注册页按需获取图形验证码（`purpose=EMAIL_SENDING`），再请求邮箱验证码。
2. 提交 `username`、`email`、`password`、`emailCode` 注册；成功后不会自动登录。
3. 登录页获取 `purpose=LOGIN` 的图形验证码，提交账号/密码/验证码。
4. 保存 `token` 和 `user`；后续请求带 `Authorization: Bearer <token>`。

图形验证码一次性使用：密码错误、发码失败、验证码过期后都需要重新获取。部署关闭 `IOJ_CAPTCHA_ENABLED` 时，`captchaId` / `captchaCode` 可以省略，`POST /captcha` 返回 `1000`。

### 4.2 出题与测试数据

1. 从 `GET /meta/tags` 取已有标签 id，`POST /problems` 创建题目。
2. 新题状态由服务端决定：管理员或 `auto-publish=true` 时为 `PUBLISHED`，否则为 `PENDING_REVIEW`。
3. 用 `PUT /problems/{problemId}/testcases` 一次性上传全部隐藏测试点；空数组会清空。
4. 题目没有测试点时可以打开题面，但提交返回 `3003`；管理员上架时也会被 `3003` 拒绝。
5. 需要 Special Judge 时，单独调用 `PUT /problems/{id}/checker`，不得塞进题目整体更新。

### 4.3 提交与轮询

1. `POST /submissions` 创建提交，响应 `status=PENDING`。
2. 轮询 `GET /submissions/{id}`，直到状态不再是 `PENDING` / `JUDGING`。
3. 只有提交者本人、题目作者、管理员能看到 `code`、`compileMessage`、`judgeMessage` 和 `cases`；其他调用方看到 `null` / `[]`。
4. 评测失败即停，`cases` 可能少于测试点总数；`CHECKER_ERROR` / `SYSTEM_ERROR` 是系统侧问题，必须提示稍后重试或联系出题者。

## 5. 容易踩的契约点

- 旧版 `GET /api/v1/problems` 列表接口已废弃：登录状态下调用返回 `405` + `1000`，未登录时可能先被安全过滤器拦截为 `401`；公开列表改用 `GET /api/v1/catalog`。
- `POST/PUT /problems` 不接受 `scopeId`；本地题一律使用保留 scope `LOCAL`，传了也会被忽略。
- `PUT /problems/{id}` 是**整体替换**：未传的 `rating`、样例、标签会被清空，时限/内存恢复默认值；它不会改变发布状态，也不会改变判题器。
- `rating` 未评分时接口返回 `-1`（数据库为 `NULL`），按 `rating` 精确筛选不会命中未评分题。
- `GET /catalog` 不再返回 `kind`；用 `url != null` 判断外部题，或 `scopeName == "LOCAL"` 判断本地题。
- 提交列表不返回代码和逐点结果；提交详情对无权限调用方也不返回这些字段。
- 管理员用户列表不含邮箱和封禁状态；需要展示封禁状态时先与后端约定扩展字段。
- 管理后台的标签/scope 列表使用公开的 `GET /meta/tags` / `GET /meta/scopes`，没有单独的 `GET /admin/tags` 或 `GET /admin/scopes`。

## 6. 相关文档

- [API 通用约定](../README.md)
- [内部判题 API](../judge/README.md)
- [后端总览](../../server/README.md)
- [自定义判题器](../../server/08-special-judge.md)
