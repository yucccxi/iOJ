# AGENTS.md (`iOJ/client`)

iOJ 前端：React 19 + Vite + TypeScript `~6.0.3` + Tailwind CSS v4 + TanStack Router / Query。改动范围仅限 `client/`。

**必读导航：**

- **前端编码、设计系统、API 边界与踩坑速查表**：[`docs/client/RULES.md`](../docs/client/RULES.md)（改代码前必读）
- **当前进度、遗留问题与后续页面路线图**：[`docs/client/TODO.md`](../docs/client/TODO.md)（任务完成后同步更新）
- **前后端接口契约（唯一字段权威来源）**：[`docs/api/README.md`](../docs/api/README.md)
- **通用 UI 视觉与 Figma 插件规范**：[`~/.claude/skills/design-tweaks/SKILL.md`](file:///Users/fallingsakura/.claude/skills/design-tweaks/SKILL.md)

---

## Repository layout

```
client/
  .review/              本地审查汇报，已 gitignore
  src/api/              请求封装、类型、TanStack Query 定义
  src/components/       通用 UI 与题库业务组件
  src/i18n/             UI 文案字典（当前只有 zh）
  src/lib/              纯工具函数
  src/routes/           TanStack Router 文件路由
  src/index.css         Tailwind v4 三主题设计变量
docs/
  client/               前端规则 RULES.md 与进度 TODO.md
  api/                  后端接口契约（唯一字段权威）
.github/workflows/
  deploy-client.yml     client 自动部署
```

---

## Commands (`cd client`)

```sh
# 默认且唯一包管理器为 pnpm（非交互式 shell 若找不到 pnpm，用 ~/Library/pnpm/bin/pnpm）
pnpm dev                                  # 用户常驻占用 5173，AI 勿抢占；host: true，局域网可访问
pnpm exec vite --port 5199 --strictPort   # AI 临时自测专用端口（同样对局域网开放），测完立即 pkill -f "vite --port 5199"
pnpm exec tsc -b                          # 快速增量类型检查（改代码必跑；Vite dev 与 eslint 不查 TS 类型）
pnpm exec vite build                      # 新增/重命名 src/routes/ 文件后先跑一次，以生成 src/routeTree.gen.ts
pnpm build                                # 完整构建：tsc -b && vite build
pnpm lint                                 # eslint .
pnpm format                               # prettier --write .（类名排序 + 80 列折行）
pnpm format:check                         # prettier --check .

# 交付前机械门禁自检（详见 docs/client/RULES.md#1-mechanically-checkable-gates交付前门禁自检）
pnpm exec tsc -b && pnpm lint && pnpm format:check
```

---

## Core invariants

- **Direct hosted backend testing**: `client/.env.development` 已代理至 `https://oj.iynotuo.me/`；测试前端或验证 API 时直接请求线上 `https://oj.iynotuo.me/api/v1`，**严禁在本地安装或拉起 MySQL / JDK / Spring Boot**。
- **`pnpm` only & locked versions**: 禁用 `npm`/`yarn`/`bun`；锁死 `typescript@~6.0.3` 与 `katex@~0.18.10`；TS 编译与 ESLint 约束见[细则](../docs/client/RULES.md#2-package-manager-typescript-eslint--formatting)。
- **Tailwind v4 & Prettier**: 禁止拼接动态类名与 `-[...px]` 任意像素值，统一用 `--spacing` 倍数与 `index.css` 的语义 token；提交前跑 `pnpm format`（[细则](../docs/client/RULES.md#2-package-manager-typescript-eslint--formatting)）。
- **Glass-only blur & inset Card ring**: 毛玻璃必须加 `glass:` 前缀；`Card` 用 `ring-inset` 替代 `border` 防 1px 偏移（[细则](../docs/client/RULES.md#3-design-system--ui-invariantssrcindexcss)）。
- **API envelope & `scope` model**: 只看 `body.code === 0` 判成功；多值查询拆为重复键 `a=1&a=2`；来源、题号、分页与 RATING 排序的前端规则见[细则](../docs/client/RULES.md#4-api--state-boundariesdocssharedapi)。
- **Locale-owned copy**: 新增或改动的用户可见文案从 `src/i18n/` 取，组件不写中文字面量（含 `title` / `aria-label` / `placeholder`），见[细则](../docs/client/RULES.md#5-ui-copy--i18n)。
