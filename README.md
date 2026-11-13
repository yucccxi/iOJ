# iOJ

轻量级的在线评测系统（Online Judge），面向校内俱乐部和几十到几百人的小规模场景：支持题库共建、Isolate 沙箱异步评测、逐测试点结果、自定义判题器（Special Judge）和独立 Judge Agent 分布式评测。

## 快速开始

- **对接接口**：见 [docs/api/README.md](docs/api/README.md) 与 [前端 API 索引](docs/api/frontend/README.md)。
- **启动后端**：见 [docs/server/README.md](docs/server/README.md) 的「快速开始」（建库 + 环境变量 + 启动 + 测试）。
- **了解设计**：见 [架构设计](docs/server/01-architecture.md)、[数据库设计](docs/server/02-database.md)、[判题设计](docs/server/03-judge.md)。
- **部署与判题机**：见 [部署与运维](docs/server/04-deployment.md)、[Isolate 沙箱运维](docs/server/06-isolate.md)、[判题 Agent 部署与运维](docs/server/07-judge-agent.md)。

最小启动命令（在仓库根目录执行，详细配置见 [部署文档](docs/server/04-deployment.md)）：

```bash
./mvnw -B -pl server -am clean package
IOJ_DB_USER=ioj IOJ_DB_PASSWORD='<db-password>' \
IOJ_JWT_SECRET="$(openssl rand -base64 48)" \
java -jar server/target/server-0.0.1-SNAPSHOT.jar
```

默认管理员为 `admin / admin123456`，**首次登录后请立即修改密码**。

## 文档

完整文档在 [`docs/`](docs/README.md)，包含：

- [文档总索引](docs/README.md)
- [API 通用约定](docs/api/README.md)
- [前端 API 索引](docs/api/frontend/README.md)
- [内部判题 API](docs/api/judge/README.md)
- [后端技术文档](docs/server/README.md)

## TODO List

### 前端

- [x] 基础工程与题库页
  - [x] React / Vite / TanStack Router / TanStack Query / Tailwind 基础工程
  - [x] 统一题库：关键词搜索、难度/标签/scope 筛选、排序、分页
  - [x] 通用 UI 组件与题库页响应式布局
- [ ] 账号与个人中心
  - [ ] 登录页
  - [ ] 注册页（邮箱验证码、图形验证码）
  - [ ] 找回密码页
  - [ ] 个人主页、修改昵称、修改密码
  - [ ] 登出所有设备
- [ ] 题目与提交
  - [ ] 题目详情、样例与题面渲染
  - [ ] 代码编辑器与提交表单
  - [ ] 提交列表、提交详情、逐测试点结果轮询
  - [ ] 我的提交与我的题目
- [ ] 出题与测试数据
  - [ ] 创建/编辑题目
  - [ ] 样例、标签、时间/内存限制编辑
  - [ ] 测试数据上传、查看、删除
  - [ ] Special Judge 源码编辑与清空
- [ ] 管理后台
  - [ ] 题目审核与上下架
  - [ ] 用户角色与封禁
  - [ ] 标签、scope 管理
  - [ ] 判题机 key 与节点状态
- [ ] 前端质量
  - [ ] 组件与路由测试
  - [ ] 关键流程端到端测试
  - [ ] 错误态、空态与可访问性检查

### 后端

- [x] 账号与权限
  - [x] 注册、登录、邮箱验证码、找回密码
  - [x] 图形验证码与按 IP 限流
  - [x] JWT、令牌版本失效、登出所有设备
  - [x] 个人资料与管理员用户管理
- [x] 题库与出题
  - [x] 统一题库与外部题库合并搜索
  - [x] 题目 CRUD、样例、标签、scope
  - [x] 隐藏测试点管理
  - [x] 题目审核与上下架
- [x] 评测引擎
  - [x] Isolate 沙箱编译与运行
  - [x] Java / C++ / C / Python 3
  - [x] 逐测试点结果与 TLE/MLE/OLE/RE/CE 判定
  - [x] Special Judge：C++ 判题器编译与执行
- [ ] 分布式判题
  - [x] Agent 启动配置拉取与工具链校验
  - [x] 节点心跳、claim、租约续期、结果回报
  - [x] 结果幂等、旧 token 拒绝、过期租约回收
  - [x] ZIP 测试数据 bundle 下载
  - [ ] 下载的测试用例本地缓存
  - [ ] 重判
    - [ ] 单条提交重判
    - [ ] 批量重判
- [x] 外部题库
  - [x] clist.by 同步 Codeforces / AtCoder 元数据
  - [x] 外部题标签与统一题库展示
- [ ] 运维与安全
  - [ ] 提交频率限制与防刷策略
  - [ ] 管理操作审计日志
- [x] 文档与 CI
  - [x] API、后端、部署和运维文档
  - [x] GitHub Actions 服务端/前端部署工作流

## License

见 [LICENSE](LICENSE)。
