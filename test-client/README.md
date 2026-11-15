# iOJ 测试前端（test-client）

用于联调 / 验证 iOJ 后端 HTTP API 的 Vue 3 测试前端。「测试用」意味着：

- 只追求**完整覆盖** `docs/api/frontend` 中的接口，不追求样式与交互细节；
- 不接入 `/internal/judge/**`（判题 Agent 专用）；
- 不替代正式的 React 前端 `client/`。

## 技术栈

- Vue 3（`<script setup>` + TypeScript）
- Vue Router
- Vite
- 无 UI 组件库、无状态管理库

## 启动

```bash
cd test-client
npm install
npm run dev
```

默认地址：<http://localhost:5174>

Vite 会把 `/api` 代理到 `http://localhost:8080`，因此本地后端按默认端口启动即可。

## 环境变量

复制 `.env.example` 为 `.env.local` 可按需覆盖：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `/api/v1` | 浏览器请求的 API 前缀；默认走 Vite 代理 |

如果需要直连后端（例如前端不在 Vite dev server 下运行），可设为
`http://localhost:8080/api/v1`，并确认后端 CORS 允许当前来源。

## 构建

```bash
npm run build
```

会先执行 `vue-tsc --noEmit` 类型检查，再输出 `dist/`。

> `npm run preview` 不会应用 Vite 的 dev proxy。预览构建产物时需要设置
> `VITE_API_BASE_URL` 指向可访问的后端，或把 `dist/` 放到反向代理之后。

## 页面与接口覆盖

| 页面 | 路径 | 覆盖接口 |
| --- | --- | --- |
| 登录 | `/login` | `POST /captcha`（LOGIN）、`POST /auth/login`、`GET /auth/me`（启动时恢复会话） |
| 注册 | `/register` | `POST /auth/email-codes`、`POST /auth/register` |
| 找回密码 | `/password-reset` | `POST /auth/password-reset-codes`、`POST /auth/password-resets` |
| 统一题库 | `/catalog` | `GET /catalog`、`GET /meta/languages`、`GET /meta/tags`、`GET /meta/scopes` |
| 题目详情 | `/problems/:id` | `GET /problems/:id`、`GET /submissions?problemId=`、`POST /submissions`、`GET /submissions/:id`（轮询） |
| 我的题目 | `/problems/mine` | `GET /problems/mine` |
| 上传 / 编辑题目 | `/problems/new`、`/problems/:id/edit` | `POST /problems`、`PUT /problems/:id`、`DELETE /problems/:id`、`GET/PUT /problems/:id/checker` |
| 测试数据 | `/problems/:id/edit` 内 | `GET/POST/PUT /problems/:id/testcases`、`GET/DELETE /problems/:id/testcases/:testcaseId` |
| 提交列表 | `/submissions` | `GET /submissions` |
| 提交详情 | `/submissions/:id` | `GET /submissions/:id`（自动轮询 `PENDING`/`JUDGING`） |
| 用户主页 | `/users/:id`、`/users/me` | `GET /users/:id`、`GET /users/me`、`GET /submissions?userId=` |
| 账号设置 | `/settings` | `GET /auth/me`、`PATCH /users/me`、`PUT /users/me/password`、`POST /auth/logout-all` |
| 题目审核 | `/admin/problems` | `GET /admin/problems`、`PATCH /admin/problems/:id/status` |
| 用户管理 | `/admin/users` | `GET /admin/users`、`PATCH /admin/users/:id/role`、`PATCH /admin/users/:id/status` |
| 标签管理 | `/admin/tags` | `GET /meta/tags`、`POST/PUT/DELETE /admin/tags` |
| Scope 管理 | `/admin/scopes` | `GET /meta/scopes`、`POST/PUT/DELETE /admin/scopes` |
| 判题 Key | `/admin/judge-keys` | `GET/POST/PATCH/DELETE /admin/judge-keys` |
| 判题节点 | `/admin/judge-nodes` | `GET /admin/judge-nodes` |

## 实现约定

- 统一响应信封：只有 `code === 0` 视为成功，否则展示后端 `message`。
- 登录 token 存放在 `localStorage` 的 `ioj_test_client_token`，请求带
  `Authorization: Bearer <token>`；`401 + 1002` 会清理本地会话。
- 图形验证码为一次性使用：登录失败、发码失败或刷新页面后重新获取。
- 提交评测通过轮询提交详情完成：间隔 800ms，最多 60 秒。
- `PUT /problems/:id` 是整体替换，编辑表单会提交全部字段；判题器使用独立表单。
- 管理端列表的标签 / scope 数据来自公开的 `GET /meta/tags`、`GET /meta/scopes`。

## 已知限制

- 未包含后端启动、数据库初始化或端到端自动化测试；只保证前端可安装、类型检查与构建通过。
- 管理员用户列表不返回邮箱和封禁状态，页面也不会推断。
- 样式为最简布局，不保证移动端体验。
- 根目录 `.gitignore` 已将 `test-client` 忽略，本目录按本地测试工具使用。
