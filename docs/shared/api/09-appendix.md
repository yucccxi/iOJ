# 9. 附录

> 返回目录：[iOJ 前端接口文档（v1）](README.md)

## 9.1 枚举速查

| 枚举 | 取值 | 用在哪里 |
| --- | --- | --- |
| 用户角色 | `USER` / `ADMIN` | `user.role`、用户列表、权限判断 |
| 用户状态 | `ACTIVE` / `BANNED` | 管理后台封禁/解封 |
| 难度 | `EASY` / `MEDIUM` / `HARD` | 题目创建、筛选、展示 |
| 题目状态 | `DRAFT` / `PENDING_REVIEW` / `PUBLISHED` / `HIDDEN` | 我的题目、审核列表、题目详情 |
| 语言 | `JAVA` / `CPP` / `C` / `PYTHON3` | 提交、语言下拉框 |
| 评测状态 | 见 [6.4 评测状态](06-submissions.md#64-评测状态) | 提交列表、提交详情 |

题目状态的含义：

| 状态 | 含义 | 公开可见 | 说明 |
| --- | --- | --- | --- |
| `DRAFT` | 草稿 | ❌ | 预留值，当前版本不会产生 |
| `PENDING_REVIEW` | 待审核 | ❌ | 平台改为人工审核时，普通用户上传后进入 |
| `PUBLISHED` | 已发布 | ✅ | 自动发布的平台上，上传即为此状态 |
| `HIDDEN` | 已下架 | ❌ | 管理员驳回或下架 |

## 9.2 可能为 null 的字段

| 位置 | 字段 | 说明 |
| --- | --- | --- |
| 题目详情 | `inputFormat`、`outputFormat`、`hint`、`source` | 可能未填写 |
| 题目详情 | `samples[].explanation` | 可能未填写 |
| 题目详情 | `hasChecker` | 布尔值，不会是 `null` |
| 题目详情 | `tags`、`samples` | 空数组（`[]`），不会是 `null` |
| 提交详情 | `code` | 无权限查看 |
| 提交详情 | `compileMessage` | 只有编译错误时有值 |
| 提交详情 | `judgeMessage` | `SYSTEM_ERROR`、以及判题器编译/启动失败（`CHECKER_ERROR`）时有值；判题器运行期失败时该字段为 `null`，原因在 `cases[].message` |
| 提交详情 | `timeUsedMs` | 尚未开始评测、编译错误、`SYSTEM_ERROR`、判题器编译/启动失败（`CHECKER_ERROR`） |
| 提交详情 | `memoryUsedKb` | 该次评测未提供内存统计时为 `null` |
| 提交详情 | `judgedAt` | 尚未评测完成 |
| 提交详情 | `cases` | 无权限、编译错误、`SYSTEM_ERROR`、判题器编译/启动失败时为 `[]` |
| 提交详情 | `cases[].memoryUsedKb` | 该测试点没有内存统计时为 `null` |
| 提交详情 | `cases[].message` | 通过/答案错误时一般为 `null` |
| 列表 | `items` | 无数据或超出页码时为 `[]`，此时 `total` 可能不为 0 |

> 数值字段（`timeUsedMs`、`memoryUsedKb`）为 `null` 时，建议统一显示为 `-`，不要直接渲染 `null`。

## 9.3 常见现象与处理

| 现象 | 原因 | 前端处理 |
| --- | --- | --- |
| 打开题目返回 `403` + `3002` | 题目未发布或已下架 | 提示"题目不可见"，不要显示"无权限" |
| 提交返回 `400` + `3003` | 题目还没有测试数据 | 提交按钮置灰，提示等待作者补充 |
| 上传题目后在题库看不到 | 该平台开启了人工审核，普通用户的题目进入待审核（`PENDING_REVIEW`）；自动发布的部署不会出现该现象 | 读响应里的 `status`；未发布时去"我的题目"查看并展示状态标签 |
| 上传后立刻就能看到、并没有"待审核" | 该平台是自动发布，普通用户上传即 `PUBLISHED` | 属正常行为，不要写死"上传 → 待审核" |
| 编辑题目后部分内容变空 | 编辑接口是整体替换 | 表单提交时带上所有字段（含标签、样例） |
| 提交后一直 `PENDING` / `JUDGING` | 评测较慢或排队 | 轮询 30-60 秒后提示"稍后到我的提交查看" |
| 未登录时 `solved` 全是 `false` | 未登录无法判断通过情况 | 不要显示"未通过"，改为显示"登录后查看" |
| token 过期后页面像"未登录" | 公开接口不会因 token 失效报错 | 请求 `/auth/me` 判断登录态，失败则引导登录 |
| 管理页看不到谁被封禁 | 用户列表不含封禁状态 | 见 8.1 的说明，必要时与后端约定扩展字段 |
| 测试数据编号不连续 | 删除中间数据后编号不重排 | 正常现象，按编号展示即可 |
| 提交显示 `CHECKER_ERROR` | 该题的自定义判题器编译失败/超时/崩溃 | 提示"评测机处理中，请稍后重试"，同时通知出题者；不要提示"你的代码有错"。判题器编译/启动失败时看 `judgeMessage`，运行期失败时看 `cases[].message` |
| 怀疑"编辑题目把判题器弄丢了" | `PUT /problems/{id}` 不会改判题器（它走独立接口），`hasChecker` 也不会被影响 | 用 `GET /problems/{id}/checker` 复核源码；前端把判题器编辑放进单独的表单与按钮 |
| 提交列表里出现了未发布题目的记录 | `GET /submissions` 不按题目可见性过滤（见 [1.6 谁可以调用、能看到什么](01-conventions.md#16-谁可以调用能看到什么)） | 不要据此认为题目已公开；如需严格隔离，请后端补过滤 |

## 9.4 页面与接口对照

| 页面 | 需要的接口 |
| --- | --- |
| 登录 / 注册 | `POST /auth/login`、`POST /auth/register`、`GET /auth/me` |
| 题库首页 | `GET /problems`、`GET /meta/tags` |
| 题目详情 | `GET /problems/{id}`、`GET /submissions?problemId={id}`、`POST /submissions` |
| 提交结果 | `GET /submissions/{id}`（轮询）、`GET /submissions?userId={id}` |
| 我的题目 | `GET /problems/mine`、`PUT /problems/{id}`、`DELETE /problems/{id}` |
| 上传/编辑题目 | `GET /meta/tags`、`GET /meta/languages`、`POST /problems`、`PUT /problems/{id}` |
| 测试数据管理 | `GET /problems/{problemId}/testcases`、`GET/POST/PUT/DELETE /problems/{problemId}/testcases/**` |
| 判题器管理 | `GET /problems/{id}/checker`、`PUT /problems/{id}/checker`（作者/管理员） |
| 个人主页 | `GET /users/{id}`、`GET /submissions?userId={id}` |
| 账号设置 | `PATCH /users/me`、`PUT /users/me/password` |
| 后台 · 审核 | `GET /admin/problems?status=PENDING_REVIEW`、`GET /problems/{id}`、`PATCH /admin/problems/{id}/status` |
| 后台 · 用户 | `GET /admin/users`、`PATCH /admin/users/{id}/role`、`PATCH /admin/users/{id}/status` |
