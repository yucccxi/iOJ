# iOJ Frontend Progress & Open TODOs (`client/`)

> 导航：返回 [`client/AGENTS.md`](../../client/AGENTS.md) ｜ 前端规则 [`RULES.md`](./RULES.md) ｜ 全局偏好 [`~/.agents/AGENTS.md`](file:///Users/fallingsakura/.agents/AGENTS.md)
> 维护要求：随每次阶段性任务完成实时销账（移入已完成或删除），新发现的遗留问题及时补入本文件。

---

## 1. 已完成并上线（Completed）

> 只留最近三项；更早的工程基建、设计系统与部署记录见 `git log`。

- **全局导航栏（[`Header.tsx`](../../client/src/components/Header.tsx), [`SectionSwitcher.tsx`](../../client/src/components/SectionSwitcher.tsx)）**：阶梯 `i` 站点 Logo、基于 `Popover` 的栏目切换菜单（首页/题库/讨论/提交记录/关于）、「贡献题目」入口、账号菜单入口。
- **题库页 `/problems/`（[`src/routes/problems/index.tsx`](../../client/src/routes/problems/index.tsx)）**：
  - 搜索框（300ms 防抖、`/` 快捷键聚焦）、来源筛选（`SourceFilter`）、难度筛选（`DifficultyFilter`，含 `UNKNOWN`）、标签筛选（`TagFilter`，草稿模式 + 搜索 + 任一/全部匹配 + 重置/应用）、排序菜单（`SortMenu`，含单来源 `RATING` 限制联动）。
  - 题目表格（`ProblemTable`，整行点击、`IOJ{id}` 与外部题号格式化、单行自适应标签、难度徽章 + 平台彩色 Rating Tooltip、通过率进度条 + 等宽计数 Tooltip、来源平台图标/作者头像）与分页器（`Pagination`）。
  - 右侧统计栏：难度分布卡（`DifficultyCard`）、热门标签卡（`PopularTagsCard`）、本站贡献卡（`ContributorsCard`）。
- **登录与注册（[`login.tsx`](../../client/src/routes/login.tsx)、[`register.tsx`](../../client/src/routes/register.tsx)）**：精简顶栏（Logo + 浏览题库）+ 居中 400px 卡片；登录含图形验证码、「7 天内自动登录」（不勾选只存 `sessionStorage`）；注册两步（邮箱 + 图形验证码 + 邮箱验证码 → 用户名、密码强度、确认密码、协议勾选），覆盖 `Sign Up · States` 11 种状态；注册成功即保存返回的 token（存 `localStorage`）并跳回主页 `/`，处于已登录态。GitHub 登录、忘记密码、协议链接为 UI 占位。

---

## 2. 最近完成的验证（Recent Verification）

- [x] **生产环境 API 契约测试（2026-11-07）**：对照 [`docs/api/`](../api/README.md) 完成公开接口 103 例 + 登录态 141 例，并专项验证自定义判题器、测试点 100/101 上限与单条 4 MiB 上限。
  - **发现**：`POST /problems` 非法难度/不存在标签返回 `1000`（文档写 `1001`）；未登录时未知路径/错误方法先返回 `401`（带 token 才是 `404/405`）；Python 语法错误落为 `RUNTIME_ERROR`。
  - **环境**：生产为人工审核，新题状态是 `PENDING_REVIEW`；旧 token 因缺少 `tv` 声明已失效，需重新登录。
  - **报告**：[`2026-11-07-api-contract-test.md`](../../client/.review/2026-11-07-api-contract-test.md)（本地，gitignored）。

---

## 3. 题库页遗留小项（Backlog · 按需开启）

- [ ] **窄屏下题目表格响应式列隐藏**：[`ProblemTable.tsx`](../../client/src/components/problems/ProblemTable.tsx) 为 `table-fixed w-full`，5 个固定列共 651px，窄屏下「题目」列会被挤没或溢出卡片（禁用 `overflow-x-auto` 以免裁切 Tooltip）；计划在 `md` 断点以下隐藏次要列（「标签」和「来源」）。
- [ ] **`SourceFilter.tsx` 的 `-translate-x-1` 视觉对齐确认**：`DifficultyFilter` 和 `SectionSwitcher` 均通过 `-translate-x-*` 左移对齐触发器文字/图标，确认 [`SourceFilter.tsx`](../../client/src/components/problems/SourceFilter.tsx) 是否需要补回 `-translate-x-1`（4px）。
- [ ] **题号与标题截断后的 `title` 悬浮提示**：[`ProblemTable.tsx`](../../client/src/components/problems/ProblemTable.tsx) 中题号 `<td title={item.code}>` 被标题 `<a>::after` 透明层遮挡导致悬停不触发原生提示，如需提示可合并挂载到标题 `<a>` 的 `title` 属性上。

---

## 4. 功能路线图（Roadmap）

> Figma 设计稿文件：`OnlineJudge`（fileKey: `iAAS0N8MEpuA730LPqWkP6`，Pages：`Screens · zh-CN`、`Components` `4:18`、`Explorations`）。
> **Figma MCP 注意**：导出的图片 URL 7 天后失效，必须用 `curl` 下载到 `src/assets/`；读取设计稿时按子 Frame 调用，不要对整页巨型节点直接调 `get_design_context` 以免连接断开。

- [x] **基础工程与题库页**
  - [x] React / Vite / TanStack Router / TanStack Query / Tailwind 基础工程
  - [x] 统一题库：关键词搜索、难度/标签/scope 筛选、排序、分页
  - [x] 通用 UI 组件与题库页响应式布局
- [ ] **账号与个人中心**
  - [x] 账号菜单：`useSession` 恢复登录态、登出、主题与语言切换
  - [x] 登录页（`/login`）与两步注册页（`/register`）
  - [ ] **下一步：找回密码页（`/forgot-password`）**：登录页「忘记密码？」目前是占位按钮，接口已就绪
  - [ ] 登录/注册页浏览器实测：对照 Figma `Login - Default`、`Sign Up · Step 1/2`、`Sign Up · States` 检查三主题与窄屏
  - [ ] 用户协议、隐私政策页（注册页协议标题目前不可点）
  - [ ] GitHub 登录（按钮目前是占位）
  - [ ] 个人主页、修改昵称、修改密码（`/profile`、`/settings` 目前是占位页）
  - [ ] 登出所有设备
- [ ] **题目与提交**
  - [ ] 题目详情、样例与题面渲染（`/problems/$problemId`）：
    - 面包屑第三段显示题号（如 `IOJ1`）。
    - 左栏：题面渲染（`marked` + `marked-katex-extension` + `dompurify`）、并排输入输出样例框与复制按钮、代码提交。
    - 右栏：题目信息卡片、操作卡片、题目标签卡片（带眼睛图标显隐切换）、最近 5 条我的提交卡片。
  - [ ] 代码编辑器与提交表单
  - [ ] 提交列表、提交详情、逐测试点结果轮询：
    - 提交记录页（`/submissions`）：用户/题号/评测状态筛选栏 + 提交列表表格（题号与题名采用 Baseline 对齐）。
    - 提交后对 `PENDING` / `JUDGING` 状态使用 TanStack Query `refetchInterval` 轮询 `GET /api/v1/submissions/{id}`。
  - [ ] 我的提交与我的题目
- [ ] **出题与测试数据**（贡献题目入口 `/problems/new`）
  - [ ] 创建/编辑题目
  - [ ] 样例、标签、时间/内存限制编辑
  - [ ] 测试数据上传、查看、删除
  - [ ] Special Judge 源码编辑与清空
- [ ] **管理后台**
  - [ ] 题目审核与上下架
  - [ ] 用户角色与封禁
  - [ ] 标签、scope 管理
  - [ ] 判题机 key 与节点状态
- [ ] **i18n**
  - [x] 运行时切换 zh-CN / en，`en` 词典用 `satisfies Widen<typeof zh>` 保证 key 一致
  - [ ] 存量硬编码文案迁移（见[第 6 节](#6-i18n-存量迁移backlog)）
- [ ] **前端质量**
  - [ ] 组件与路由测试
  - [ ] 关键流程端到端测试
  - [ ] 错误态、空态与可访问性检查

---

## 5. 账号菜单遗留问题（Backlog）

- [ ] **嵌套下拉按 Esc 会关掉整个账号菜单**：[`InPlaceSelect.tsx`](../../client/src/components/ui/InPlaceSelect.tsx) 与 [`Popover.tsx`](../../client/src/components/ui/Popover.tsx) 都在 `document` 上监听 `keydown`，Popover 先触发，整个菜单关闭、焦点落到 `<body>`。期望只收起下拉；可在 InPlaceSelect 里 `stopImmediatePropagation()`，或改为在自身根节点监听。
- [ ] **`/auth/me` 非 1002 错误没有错误态**：网络错误或 502 时 `user` 一直是 `undefined`，菜单表头永远显示「加载中」（[`AccountMenu.tsx`](../../client/src/components/AccountMenu.tsx)）。

---

## 6. i18n 存量迁移（Backlog）

> 规则见 [`RULES.md` 第 5 节](./RULES.md#5-ui-copy--i18n)：新增或改动的文案直接走 `src/i18n/`，下列文件随各自的下一次改动逐个销账，不要求一次全量替换。共 60 处。
> Header、SectionSwitcher、账号菜单与 TagFilter 已接入 `useCopy`；以下 12 个文件仍是硬编码。

- [ ] [`SortMenu.tsx`](../../client/src/components/problems/SortMenu.tsx#L7) — 13 处：排序项、升序/降序、禁用提示
- [ ] [`routes/problems/index.tsx`](../../client/src/routes/problems/index.tsx#L137) — 8 处：空态、重试、列表标题、搜索占位、计数单位
- [ ] [`SourceFilter.tsx`](../../client/src/components/problems/SourceFilter.tsx#L39) — 7 处：来源、本站、外部平台
- [ ] [`ProblemTable.tsx`](../../client/src/components/problems/ProblemTable.tsx#L15) — 7 处：六个表头、外链 `aria-label`
- [ ] [`Pagination.tsx`](../../client/src/components/ui/Pagination.tsx#L41) — 5 处：分页摘要、上一页/下一页
- [ ] [`DifficultyBadge.tsx`](../../client/src/components/ui/DifficultyBadge.tsx#L5) — 4 处：简单/中等/困难/未知
- [ ] [`PopularTagsCard.tsx`](../../client/src/components/problems/PopularTagsCard.tsx#L23) — 4 处：热门标签、全部标签、空态
- [ ] [`DifficultyFilter.tsx`](../../client/src/components/problems/DifficultyFilter.tsx#L46) — 3 处：按难度筛选、触发器、面板标题
- [ ] [`ContributorsCard.tsx`](../../client/src/components/problems/ContributorsCard.tsx#L39) — 3 处：本站贡献、题目数
- [ ] [`FilterPanel.tsx`](../../client/src/components/ui/FilterPanel.tsx#L27) — 2 处：清除、已选摘要
- [ ] [`DifficultyCard.tsx`](../../client/src/components/problems/DifficultyCard.tsx#L26) — 2 处：难度分布、全部来源
- [ ] [`PlatformMark.tsx`](../../client/src/components/problems/PlatformMark.tsx#L9) — 2 处：洛谷、力扣
