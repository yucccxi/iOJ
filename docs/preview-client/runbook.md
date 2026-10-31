# 运行与验证

## 环境与入口

- Node.js 满足 package.json engines；本次使用 v24.18.0。源码沿用 Vue 3、TypeScript、Vite、Pinia、Vue Router。
- 在 preview-client/ 执行 npm ci，再执行 npm run dev；默认只监听 127.0.0.1:5173，并固定端口。端口冲突会直接报错，不静默切换。
- 页面入口 /problems。接口默认 /api/v1，由 Vite 将 /api 转发到 http://127.0.0.1:8080。
- .env.example 给出 VITE_API_BASE_URL 和 IOJ_API_PROXY_TARGET。后者仅控制开发代理；VITE_* 会进入客户端，不应存秘密。需要改配置时复制为 .env.local 并重启 Vite。
- 前端不自动启动后端、不连接数据库，也没有演示数据回退。后端离线会显示可重试错误。
- 生产使用同域反向代理转发 /api；history 路由回退 index.html。可用 npm run preview 在本地检查 dist，但该命令不配置开发 API 代理。

## 页面入口

| 路由                                              | 用途                                 |
| ------------------------------------------------- | ------------------------------------ |
| /problems                                         | 题库与筛选                           |
| /login、/register                                 | 登录和注册                           |
| /problems/:id                                     | 题面、样例、代码提交、最近提交       |
| /submissions、/submissions/:id                    | 提交记录、异步结果                   |
| /problems/mine、/problems/new、/problems/:id/edit | 我的题目、创建、完整编辑             |
| /problems/:id/testcases                           | 隐藏测试点查询、追加、批量替换与删除 |
| /users/:id、/settings                             | 个人主页、昵称与密码                 |
| /admin/problems、/admin/users                     | 审核、角色、封禁与解封               |

## 检查命令

- npm test：Node 原生测试，验证请求与错误、会话竞态、编辑契约、输入边界及安全回跳。
- npm run test:ui：Vitest + Vue Test Utils + jsdom，用测试专用 HTTP 替身验证应用路由与交互、源码权限、Markdown 清洗、离开页面停止轮询、资源销毁和管理员命令；含草稿跨登录恢复、按题目隔离、退出/会话失效清理，以及五个列表入口的查询与分页隔离。
- npm run build：执行 vue-tsc 类型检查与 Vite 生产构建。
- node tests/live-api.mjs --isolated：仅用于本任务本地 H2 的真实接口检查；默认 18080，可传入 http://127.0.0.1:5174/api/v1 验证代理。该命令会写入隔离库，不适用于用户数据库。
- npm audit --omit=dev：检查浏览器运行依赖。工具依赖的审计保留项见交接记录。
- 测试中的 dialog open/close 和 scrollTo 是 jsdom 兼容替身；这类测试不验证浏览器原生弹窗焦点、CSS 布局、浅深色实际观感或触摸交互。

## 真实环境验收

在桌面和手机宽度、系统浅色和深色下检查导航、表格横向滚动、题面与编辑区、表单校验、键盘焦点、弹窗及完整业务流程。后端需单独启动，按 docs/server/README.md 配置。

没有明确目标环境时，不向未知数据库创建测试账号或题目。可在本任务 worktree 用后端 test profile 和独立端口启动 H2 内存库，再临时配置开发代理；该环境不会保留数据，判题 worker 关闭，不能用于验收实际编译或沙箱。

## 作答与筛选行为

### 公告浮窗

- 全站入口为 src/App.vue 挂载的 src/components/AnnouncementNotice.vue，右下角显示“如果你第一次来这个网站，请看 公告”，链接为 https://blog.iynotuo.me/posts/3588dc9a，在新标签页打开。
- 关闭后通过 sessionStorage 的 ioj:announcement:3588dc9a:dismissed 标记在当前标签页会话内保持隐藏，刷新/切页不会重新显示；新的独立标签页会话重新显示。存储不可用时仍可关闭，但刷新会再次显示。
- 样式复用全站浅深色变量，保留安全区与左右边距，关闭按钮和链接触摸高度至少 44px。npm run test:ui 包含关闭持久性、链接和存储拒绝的回归；实际浏览器预览范围见 handoff.md。

### Markdown 与公式

- 共用入口为 src/components/MarkdownView.vue 和 src/lib/markdown.ts，覆盖题目描述、输入/输出格式、提示、样例解释及编辑页预览。
- 使用 `$…$` 表示行内公式，`$$…$$` 表示独立公式；支持两端有空格、中文相邻文字和独立公式内换行。例如 `$ x \oplus y = a $` 与 `$$ x^2 + y^2 = z^2 $$`。代码块、行内代码和转义美元符号保持原文；未闭合公式不强行转换。
- 采用 KaTeX 支持的 LaTeX 子集，错误或不支持的表达式保留可读源文，不中断其他内容。宽公式横向滚动。CSS 与字体从本地包构建，不依赖 CDN；浅深色继承界面文字色。
- 当前兼容组合为 KaTeX 0.18.10、marked-katex-extension 5.1.13 和 Marked 18；扩展的 peer 范围要求 KaTeX <0.19，升级时同时核对兼容范围。npm run test:ui 包含公式、Markdown 混排、代码、错误、危险命令和宏隔离回归。

### 编辑器与列表

- 作答草稿入口为 src/stores/codeDrafts.ts 与 src/views/ProblemView.vue。代码和所选语言按题号保存在当前应用内存，切换题目或登录后返回时恢复；退出、已有令牌替换/清除、刷新页面或关闭标签页后清空。
- CodeMirror 编辑器位于 src/components/CodeEditor.vue，语言扩展位于 src/lib/editorLanguages.ts；Java、Python、C/C++ 分块按选择加载（C 使用 C++ 高亮扩展）。支持行号、高亮、括号匹配、Tab 缩进；按 Esc 再按 Tab 可移出编辑器。语言扩展加载失败时保留可编辑代码并提供重试。
- 桌面题面与代码分别滚动，拖动分隔条调整宽度；分隔条可用左右键微调、Shift 加速、Home/End 到达边界。800px 以下通过“题面／代码”按钮切换。最近提交收在代码面板底部，可展开；提交成功仍进入提交详情页。
- 顶栏账户菜单集中个人主页、账号设置和退出，支持外部点击或 Esc 关闭。列表与题库采用统一中性灰表面和墨绿操作色，浅深色跟随系统。
- 路由淡入 180ms、离场淡出 100ms；系统开启减少动态效果后关闭过渡。认证状态变化立即清除旧页面。主题与动效实际观感按人工预览验收，模拟 DOM 只验证契约和销毁路径。
- 题库、我的题目、提交记录、管理员题目和用户列表中，输入筛选条件后点击“查询”应用新条件并回到第一页；上一页/下一页沿用已应用的查询条件。
- 480px 以下保留登录/设置入口，小按钮与文本按钮的最小高度为 44px。注册用户名与密码提示分别位于对应字段下；统计、耗时、内存与测试数据字节数列使用 numeric 类右对齐。

## 本机连接与交付预览

日常源码在主工作区 preview-client/。用户要求局域网访问时，执行 npm run dev -- --host 0.0.0.0；当前服务已按此命令运行，监听 0.0.0.0:5173。浏览器使用 http://localhost:5173/ 或 http://<本机IPv4>:5173/，0.0.0.0 是监听地址。已从本机经 WLAN IPv4 地址验证首页 HTTP 200 及公开语言接口代理成功；没有从另一台设备完成访问验收。

不带 --host 参数的 npm run dev 仍使用配置中的 127.0.0.1 默认绑定。需要继续局域网访问时使用上面的完整命令。

用户指定的局域网后端已配置到主工作区 .env.local 的 IOJ_API_PROXY_TARGET，VITE_API_BASE_URL 保持 /api/v1。该文件由 Git 忽略，不在仓库中发布本地服务地址。Vite 已重新加载，语言、标签和题库公开接口经代理查询成功。未在该服务上执行写入验收。

实施时曾用独立 5174 预览及 18080 H2 进行接口契约验收；这两个临时服务现已停止。源码、测试配方与简短结论保留，临时构建和依赖副本不作交付。实际检查结果与未验收项统一见 [handoff.md](handoff.md)。

## 工作台交付

本轮实现计划见 [workbench-plan.md](workbench-plan.md)。独立 worktree 的 5174 用于候选验收，日常运行入口为主工作区 5173；切换包含新增依赖的版本时，在合并锁外执行 npm ci，再以 npm run dev -- --host 0.0.0.0 恢复主服务。本轮按用户要求禁止推送；验收与候选清理结果见 [handoff.md](handoff.md)。
