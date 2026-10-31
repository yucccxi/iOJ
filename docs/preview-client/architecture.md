# 实施与架构

## 目标与约束

在现有 Vue 3 / TypeScript / Vite / Pinia / Vue Router 模板上完成接口文档第 9.4 节的页面。不修改 server/、docs/shared/api.md 或根 README.md。界面跟随系统浅深色，采用表格与工作区布局、系统中文字体及代码等宽字体，少装饰、短文案、完整焦点和错误状态。

## 解耦约定

- types/api.ts 描述 API 输入输出；api/client.ts 统一响应解析、请求头、取消、错误；api/resources.ts 定义资源接口。网络层不导入页面、路由和 store。
- stores/auth.ts 独占会话，负责恢复、登录、退出与失效清理。令牌存 sessionStorage，不保存密码；每次恢复通过 /auth/me 校验。旧请求的 401 不能清除新登录。
- stores/codeDrafts.ts 独占按题号区分的作答代码与语言内存草稿，页面通过 read/save 交换副本。它单向依赖 auth store，匿名登录保留草稿，已有令牌被替换或清除时同步清空；监听随 Pinia 作用域销毁，不持久化代码，不依赖路由或 API 实现。
- 页面依赖资源接口与组合函数。useRequestScope 负责离开页面取消写请求，取消不等于服务端回滚。composables/useResource.ts 负责请求取消和旧响应隔离；页面卸载清理。评测页面仅在排队或评测中轮询，离开停止。
- components/ 只通过 props 和事件协作，不读取具体业务页面。MarkdownView 通过 lib/markdown.ts 解析与渲染：公式先占位，普通 Markdown HTML 仍由 DOMPurify 禁止脚本、style/iframe/form 和样式属性；仅将受控 KaTeX 输出填回本次渲染的随机占位节点，保留公式 MathML、SVG 和布局样式。代码始终按文本展示。
- 公式处理只依赖 marked、marked-katex-extension、KaTeX 和 DOMPurify，不读取 API、认证或路由。每次渲染独立创建解析器、宏表和占位映射；trust=false、maxExpand=1000、maxSize=10。代码节点不替换为公式，错误公式回退显示；组件销毁无监听器、请求或宏状态遗留。
- 列表页面分别处理查询与翻页：查询读取表单并回到第一页，翻页复制当前 route.query，仅变更页码。Pagination 继续只接收分页数据并发出页码，不读取筛选表单。
- api 基址与本地代理可配置。会话切换时清除页面实例，避免上一个账号的私密内容残留。
- 路由切换由 App 的 Transition 与单个页面包装节点负责；认证 revision 是其外层宿主的 key，认证变化立即销毁完整过渡树，不等待离场动画。页面不保活，原有请求取消与轮询清理仍在卸载时执行。
- CodeEditor 通过 modelValue/language/disabled 与文本更新事件协作，管理 CodeMirror 实例、系统主题监听及 65536 字符边界。lib/editorLanguages 按语言加载扩展；序号隔离迟到结果，加载失败可重试且保留文本，卸载后不回写。它不读取草稿 store、认证或资源接口。
- ResizablePanels 独占宽度与指针捕获，支持键盘调整，取消/卸载时释放；手机面板选择归 ProblemView。AccountMenu 只接收用户展示数据并发送退出事件，外部点击监听随组件销毁移除。
- 验收需检查请求层可替换、取消/卸载、权限裁剪、无循环页面依赖。功能验证与边界审查分别记录。

## 实施步骤

1. 建立类型、请求层、登录状态与行为测试；完成应用导航、主题及路由权限。
2. 实现题库、题目详情、提交、结果轮询与个人资料。
3. 实现出题表单、我的题目、隐藏测试数据管理、账号设置和管理员页面。
4. 类型检查、构建、关键交互与响应式检查；同步接口覆盖、运行说明及验收限制。
5. 审查边界与最终差异，本地提交、合并队列、必要主线检查与清理。远端同步遵从用户指令，本轮禁止推送。

## 接口与页面

| 页面          | 接口                                                                                                                                   |
| ------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| 登录/注册     | POST /auth/login、POST /auth/register、GET /auth/me                                                                                    |
| 题库          | GET /problems、GET /meta/tags                                                                                                          |
| 题目/提交     | GET /problems/{id}、GET /meta/languages、POST /submissions                                                                             |
| 提交列表/详情 | GET /submissions、GET /submissions/{id}                                                                                                |
| 我的题目/编辑 | GET /problems/mine、POST/PUT/DELETE /problems                                                                                          |
| 隐藏测试数据  | GET/POST/PUT/DELETE /problems/{id}/testcases                                                                                           |
| 个人主页/设置 | GET /users/{id}、GET /users/me、PATCH /users/me、PUT /users/me/password                                                                |
| 管理员        | GET /admin/problems、PATCH /admin/problems/{id}/status、GET /admin/users、PATCH /admin/users/{id}/role、PATCH /admin/users/{id}/status |

## 契约细节

- 注册后单独登录；退出和改密码成功清理会话。公开接口不会因无效 token 自动报错，恢复身份使用 /auth/me。
- 编辑题目是整体替换，必须保留完整可选字段、样例和标签。没有接口支持保存题目 DRAFT，不展示题目草稿保存操作；作答代码的内存草稿由前端独立管理。
- 题目作者、提交者、管理员可见的提交私密字段仍以后端返回为准；无权时显示短状态，不推断隐藏内容。
- 管理用户列表没有 email/status，不展示猜测的封禁状态；提供独立封禁、解封命令，禁止对自己操作。
- 逐点评测遇首个失败停止；null 耗时/内存显示短横线。语言从接口获取，状态含 MLE。
- 测试数据以 JSON 文本提交；单题 100 条，单条 input/output 各最多 1 MiB（UTF-8），清空和覆盖要求确认。
- 所有正式页面使用真实接口。测试替身只能出现在 tests/，不提供生产演示数据。
