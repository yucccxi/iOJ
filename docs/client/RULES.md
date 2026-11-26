# iOJ Frontend Rules & Invariants (`client/`)

> 导航：返回 [`client/AGENTS.md`](../../client/AGENTS.md) ｜ 全局偏好 [`~/.agents/AGENTS.md`](file:///Users/fallingsakura/.agents/AGENTS.md) ｜ 待办清单 [`TODO.md`](./TODO.md) ｜ 后端接口契约 [`docs/api/`](../api/README.md)

---

## 1. Mechanically Checkable Gates（交付前门禁自检）

每次完成编码、准备写入 `.review/` 汇报前，在 `client/` 下至少执行 1a；涉及新增/重命名路由文件、构建配置、依赖或交付收口时加跑 1b。

```sh
# 1a. 快速类型检查 + Lint + 格式检查（改 TS/组件/逻辑必跑；Vite dev 与 eslint 不查 TS 类型）
pnpm exec tsc -b && pnpm lint && pnpm format:check

# 1b. 完整构建验证（若新增/重命名了 src/routes/ 文件，先跑一次 pnpm exec vite build 生成路由树）
pnpm build
```

---

## 2. Package Manager, TypeScript, ESLint & Formatting

- **Default package manager (`pnpm`)**: 默认且唯一包管理器为 `pnpm`（锁文件为 [`pnpm-lock.yaml`](../../client/pnpm-lock.yaml) 与 [`pnpm-workspace.yaml`](../../client/pnpm-workspace.yaml)）；**严禁使用 `npm` / `yarn` / `bun` 安装依赖或执行脚本**（防止污染锁文件或绕过 `pnpm-workspace.yaml` 中的 `allowBuilds` 原生构建拦截配置）。非交互式 shell 若找不到 `pnpm`，使用 `~/Library/pnpm/bin/pnpm`。
- **Type check workflow (`pnpm exec tsc -b`)**: Vite 开发服务器与基础 ESLint 均不执行 TypeScript 类型检查；每次修改 `.ts`/`.tsx` 后必须跑 `pnpm exec tsc -b`（基于 `tsconfig.app.tsbuildinfo` 增量检查，毫秒级完成）。若新增或重命名了 `src/routes/` 下的路由文件，需先跑一次 `pnpm exec vite build` 让 `@tanstack/router-plugin` 更新 `src/routeTree.gen.ts`，再跑 `tsc -b`。
- **Locked dependencies**: 锁死 `typescript@~6.0.3`（TS 7.0 移除旧 JS API 会导致 `typescript-eslint` 与 `openapi-typescript` 崩溃）和 `katex@~0.18.10`（`marked-katex-extension` 要求 `< 0.19`）。
- **`erasableSyntaxOnly: true` ([`tsconfig.app.json`](../../client/tsconfig.app.json))**: 禁止 `enum`、`namespace` 和构造函数参数属性（`constructor(readonly code: number)`）；枚举一律用 `as const` 数组 + `(typeof arr)[number]` 推导，类属性必须显式声明字段并在构造函数内赋值。
- **`verbatimModuleSyntax: true`**: 导入纯类型必须写 `import type { ... }`。
- **Path alias**: 统一使用 `@/` 指向 `./src/`；TS 6 已弃用 `baseUrl`，`tsconfig` 中只保留 `paths`。
- **Component-only exports in `src/components/`**: `react-refresh/only-export-components` 仅在 `src/routes/**/*.tsx` 豁免（[`eslint.config.js`](../../client/eslint.config.js)）；`src/components/` 下的文件**严禁同时导出普通工具函数或常量**（工具函数抽至 `src/lib/`，常量抽至 `src/api/`，或改用组件 prop 如 `PlatformMark` 的 `fallback`）。
- **React 19 `ref` prop**: `ref` 直接作为普通 prop 传递（如 [`Popover.tsx`](../../client/src/components/ui/Popover.tsx) 的 `TriggerProps`），不使用 `forwardRef`。
- **No arbitrary pixel classes**: 除 `border-[1.5px]` 外，禁止写 `w-[91px]`、`rounded-[6px]` 等任意值；一律换算为 Tailwind v4 `--spacing` 倍数（1 单位 = 4px，支持小数，如 `w-22.75`、`w-88.75`、`w-95`、`max-w-325`、`h-6.5`）或语义圆角 token（`rounded-control`、`rounded-layer`、`rounded-popover`、`rounded-sm`、`rounded-md`）。
- **Static complete class names**: 禁止动态拼接 Tailwind 类名（如 `` `text-rating-${c}` ``），必须写完整字面量（如 [`src/lib/rating.ts`](../../client/src/lib/rating.ts)）。
- **Prettier pipeline ([`prettier.config.js`](../../client/prettier.config.js))**: 80 列折行，通过 `prettier-plugin-tailwindcss` + `prettier-plugin-classnames` + `prettier-plugin-merge` 先排序类名再按 80 列折行（含 `labelClassName` 属性）；注意存储在普通 JS 变量里的类名字符串不会被插件折行。
- **Lucide icon defaults ([`src/main.tsx`](../../client/src/main.tsx))**: 顶层已包裹 `<LucideProvider size={16} nonScalingStroke>`，默认 16px、线宽固定 2px；严禁使用已废弃的 `absoluteStrokeWidth`，16px 图标不重复传 `size={16}`。

---

## 3. Design System & UI Invariants（[`src/index.css`](../../client/src/index.css)）

通用视觉准则见 [`~/.claude/skills/design-tweaks/SKILL.md`](file:///Users/fallingsakura/.claude/skills/design-tweaks/SKILL.md)，以下为 iOJ 特定实现红线：

- **Theme switching on `<html data-theme>`**: `@theme static` 定义默认浅色变量（`static` 防止未直接引用的变量被构建裁剪），`:root[data-theme="dark"]` 与 `:root[data-theme="glass"]` 覆盖同名变量；组件内直接用语义类（`bg-layer`、`text-fg`），不写 `dark:*` 前缀。
- **Theme store ([`src/lib/theme.ts`](../../client/src/lib/theme.ts))**: `setTheme` 在 `<html>` 上写 `data-theme`，`default` 主题则移除该属性。选择存在 `localStorage` 的 `ioj-theme` 键下；[`index.html`](../../client/index.html) 里的内联脚本在打包脚本加载前就应用主题（其键名和取值要与 `theme.ts` 保持一致），`<body>` 带 `bg-canvas`，深色主题不会闪白。
- **Glass-only backdrop blur (`glass:backdrop-blur-layer`)**: 严禁写无前缀的 `backdrop-blur-layer`！非 glass 主题下即使 `--blur-layer: 0px`，`backdrop-filter: blur(0px)` 也会触发 Chrome 合成层（compositing layer），导致窗口缩窄、侧栏下移时不重绘而显示空白。
- **Inset ring on `Card` ([`Card.tsx`](../../client/src/components/ui/Card.tsx))**: 卡片外框用 `ring-1 ring-line-glass ring-inset` 而非 `border`（`border` 占 1px 布局空间会导致表格各列偏 1px）；内边距遵循视觉留白规则 S1（顶部文字边 `pt-card-y-text` 16px，左右与底部实心边 `px-card-x` / `pb-card-y-edge` 20px）。
- **Bundled typography tokens**: 使用 `index.css` 预定义的 22 个 `text-*` token（自带行高与默认字重），同字号粗体叠加 `font-bold` / `font-medium`；等宽 token 需额外加 `font-mono`，且**严禁对中文字符设 `font-mono`**（规则 T2）。中文占位符的省略号写三个英文点 `...`，不用 `…`（`Noto Sans SC` 将单字符绘在垂直居中位置，不贴底）。
- **Popover spacing & content alignment ([`Popover.tsx`](../../client/src/components/ui/Popover.tsx))**: 弹层距触发器统一 `6px`（`mt-1.5`，规则 S6）。左对齐窄面板（`align="start"`）用 `-translate-x-*` 对齐**面板内部文字/图标与触发按钮文字/图标**（偏移量 = 面板内缩进 − 按钮内缩进）；右对齐宽面板（`align="end"`，如 380px 的 `TagFilter`）不设自身外层 `relative`，向上对齐工具栏右边缘防溢出。
- **Form fields ([`FormField.tsx`](../../client/src/components/ui/FormField.tsx))**: 表单统一用 `FormField` / `PasswordField`（40px 输入框，标签行右侧可放次要操作）和 `Button size="lg"`；帮助和错误文字有内容才占位（规则 F5）；密码有值时圆点才切成 `font-mono`，中文占位符保持无衬线字体。间距只用 `index.css` 的 `gap-form-*` token，数值必须和 Figma 的 `space/form/*` 变量一致，改一处要同步改另一处。
- **Stretched-link table rows & floating cursors ([`ProblemTable.tsx`](../../client/src/components/problems/ProblemTable.tsx))**: 标题链接用 `after:absolute after:inset-0` 铺满整行实现整行点击；浮在上面的悬停元素（难度 Rating Tooltip、`RateBar`）必须设 `z-10 cursor-default`，否则脱离 `<a>` 后退回 `cursor: auto` 会在文字上显示 I 形文本光标。

---

## 4. API & State Boundaries（[`docs/api/`](../api/README.md)）

接口完整参数与字段定义以 [`docs/api/`](../api/README.md) 为唯一权威来源（One home per fact）；本节只写前端必须遵守的调用与状态边界。

- **Envelope & success check ([`src/api/client.ts`](../../client/src/api/client.ts))**: 所有请求带 `/api/v1` 前缀；**只看 `body.code === 0` 判断成功**（同一业务错误码可能对应多个 HTTP 状态）；非 JSON 响应（如 502 HTML）统一抛 `ApiError(-1, ...)`。
- **Auth token ([`src/api/token.ts`](../../client/src/api/token.ts))**: token 存在 `ioj-token` 键下，勾选「7 天内自动登录」时存 `localStorage`，否则存 `sessionStorage`；注册响应直接带 token，注册成功按勾选处理（存 `localStorage`），不再调用登录接口；请求以 `Authorization: Bearer` 发送。`setToken` / `clearToken` 会通知 `useToken` 的订阅者，登录和登出后所有读取方都会重新渲染。组件通过 [`src/api/auth.ts`](../../client/src/api/auth.ts) 的 `useSession()` 读取登录态；其 `/auth/me` 查询以 token 为键，新登录不会复用上一个用户，收到 `1002` 时清除 token。
- **Array query serialization**: 多值参数拆为重复键 `?scopeId=1&scopeId=5`（禁写逗号分隔）；地址栏保持 TanStack Router 的 JSON 数组格式，在路由入口用 `validateSearch(searchSchema)`（Zod `.default().catch()`）+ `stripSearchParams(searchDefaults)` 集中归一化并剔除默认值。
- **Catalog identity**: 本地题与外部题分表存储、`id` 可能重复，列表 React `key` 必须写 `` `${item.scopeId}-${item.id}` ``；本地题判断走 [`isLocal(item)`](../../client/src/lib/problem.ts)，`LOCAL` 的 scope id 从 `/meta/scopes` 按名称查找，禁止写死。
- **Problem code display ([`problemCode`](../../client/src/lib/problem.ts))**: 本站题显示 `IOJ{item.id}`；外部题去掉平台前缀并把 `_` 换为空格（如 `CF_2278G → 2278G`）。
- **RATING sort is single-scope**: `sort=RATING` 的后端约束见 [`02-catalog.md`](../api/frontend/02-catalog.md)；前端在 `scopeId.length !== 1` 时禁用难度排序，取消或多选来源时把 `sort` 重置为 `"DEFAULT"`。
- **Image captcha ([`useCaptcha`](../../client/src/api/auth.ts))**: 生产环境登录和发送邮箱验证码都必须带图形验证码，输入值由 `useCaptcha` 持有，换图时自动清空。服务端先校验验证码再做其他检查，每次请求都会用掉它，所以请求失败都要 `refresh()`；发邮箱验证码成功后调用 `lock()`，验证码保持显示但不可修改，等重发倒计时结束再 `refresh()`。验证码按 `expiresInSeconds`（默认 2 分钟）到期自动换图，锁定期间不换。`POST /captcha` 返回 `1000` 表示该部署关闭了验证码，此时隐藏这一栏。
- **Runtime validation boundaries**: 运行期校验只在三处收口——URL search params（Zod）、API 响应信封、`localStorage` token；组件 props 与同进程内部调用信任 TypeScript，不加判空、fallback 或防御性测试。

---

## 5. UI Copy & i18n

- **Locale-owned copy**: 新增或改动的用户可见文案从 [`src/i18n/`](../../client/src/i18n/) 取；组件内用 `useCopy()`，非组件代码用 `getCopy()`，不要直接 import 静态字典。组件内不写中文字面量，`title`、`aria-label`、`placeholder` 同样走字典。
- **Locale and theme persistence**: 只通过 `useLocale` / `setLocale`、`useTheme` / `setTheme`、`useToken` / `setToken` / `clearToken` 读写，存储键分别为 `ioj-locale`、`ioj-theme`、`ioj-token`。
- **Sentences stay whole in the dictionary**: 需要插值的文案写成函数放在字典里（如 `serviceUnavailable: (status) => ...`），组件只传参数，不在组件里拼接中文语序。
- **Migrate on touch**: 存量硬编码文案随所在文件的下一次改动迁移，不做机械全量替换。

---

## 6. Troubleshooting Table

| 现象 / 报错                                                                 | 根因                                                                                   | 解法                                                                                                                                    |
| --------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| 新增路由文件后 `pnpm build` 报 TS 类型错误                                  | `tsc -b` 先于 `vite build` 执行，`routeTree.gen.ts` 尚未重新生成                       | 先跑 `pnpm exec vite build` 生成路由树，再跑 `pnpm build`                                                                               |
| 安装依赖报 `ERR_PNPM_IGNORED_BUILDS` (`node-pty`)                           | `code-inspector-plugin` 的可选原生依赖触发 pnpm 12 拦截                                | [`pnpm-workspace.yaml`](../../client/pnpm-workspace.yaml) 保持 `allowBuilds: { node-pty: false }`                                       |
| macOS 本机手动跑 `rsync` 部署被服务器拒绝                                   | 系统自带 `/usr/bin/rsync` 是 `openrsync`，参数格式被服务端 `rrsync` 拒绝               | 使用 Homebrew GNU rsync（`/opt/homebrew/bin/rsync`）                                                                                    |
| 窗口缩窄后右侧栏卡片空白，滚动才出现                                        | `backdrop-filter: blur(0px)` 触发 Chrome 合成层未重绘                                  | 只用 `glass:backdrop-blur-layer`，不在非 glass 主题输出 `backdrop-filter`                                                               |
| 表格长标签被截断半个词                                                      | 固定 `.slice(0, 2)` + 单行裁剪                                                         | 容器设单行高 `flex h-6.5 flex-wrap gap-1.5 overflow-hidden`，放不下的换到隐藏的第二行；`Tag` 加 `max-w-full truncate`                   |
| 表格窄屏溢出为何禁用 `overflow-x-auto`                                      | CSS 中 `overflow-x: auto` 会连带让 `overflow-y` 变成 `auto`，裁切向上弹出的 Tooltip    | 窄屏采用响应式隐藏次要列方案                                                                                                            |
| 洛谷图标在浅色悬停行露出白底方块                                            | 原始 PNG 带白底                                                                        | [`PlatformMark.tsx`](../../client/src/components/problems/PlatformMark.tsx) 对洛谷加 `mix-blend-multiply`（深色模式后续需换透明底图标） |
| 搜索框防抖跳转冲掉已选筛选条件                                              | `navigate` 直接覆盖了 `search` 对象                                                    | 函数式更新 `search: (prev) => ({ ...prev, keyword: next, page: 1 })`                                                                    |
| 局域网地址打开开发页，`POST`（验证码、登录）返回 `403 Invalid CORS request` | 代理原样转发浏览器的 `Origin`，后端 CORS 白名单只有 `localhost:5173`                   | [`vite.config.ts`](../../client/vite.config.ts) 代理在 `proxyReq` 里删掉 `origin` 头                                                    |
| 用 CSS mask 着色的 SVG 图标显示成实心方块                                   | Vite 把小于 4KB 的 SVG 内联成带单引号的 data URL，不加引号的 `url()` 无效，mask 被丢弃 | `maskImage` 写成 `` `url("${iconUrl}")` ``（同 [`Tooltip.tsx`](../../client/src/components/ui/Tooltip.tsx)）                            |
| VS Code 每次 stage 文件弹 CSS 解析警告                                      | `phoenisx.cssvar` 扩展扫描了 `dist/` 构建产物                                          | `.vscode/settings.json` 配置 `"cssvar.ignore": ["**/node_modules/**", "**/dist/**"]`                                                    |
