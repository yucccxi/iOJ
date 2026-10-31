# iOJ

轻量级的在线评测系统（Online Judge）

## 项目特点

- **题库共建**：任何登录用户都能上传题目（题面、样例、隐藏测试数据），管理员可审核、上下架。
- **规模友好**：面向几十到几百人、每天几百次提交，单机即可运行，备份简单。
- **评测反馈明确**：AC / WA / TLE / OLE / RE / CE / SE，并显示每个测试点的耗时与失败原因。
- **对接友好**：统一响应结构、分页与错误码，并提供面向前端的接口文档。

## 使用流程

1. 注册 / 登录（用户名或邮箱）。
2. 上传题目：填写题面与样例 → 上传隐藏测试数据 → 题目发布（部分场景需管理员审核）。
3. 做题：浏览题库 → 打开题目 → 提交代码 → 等待评测 → 查看逐测试点结果。
4. 管理：管理员审核题目、上下架、修改用户角色、封禁用户。

## 角色与权限

| 角色 | 能做什么 |
| --- | --- |
| 访客（未登录） | 浏览题库与已发布题目、查看公开的提交记录（不含代码） |
| 普通用户 | 提交代码、上传题目、维护自己的题目与测试数据、查看自己的代码与结果 |
| 题目作者 | 管理自己题目的题面、测试数据，删除自己的题目 |
| 管理员 | 额外拥有题目审核/上下架、用户角色与封禁管理权限 |

## 功能一览

| 模块 | 内容 |
| --- | --- |
| 账号 | 注册、登录、个人主页、修改昵称、修改密码 |
| 题库 | 列表（标题搜索、难度、标签）、题目详情、题面样例 |
| 出题 | 上传 / 编辑 / 删除题目、标签、隐藏测试数据、草稿/待审核/已发布/已下架 |
| 评测 | Java、C++、C、Python 3；Isolate 沙箱异步评测；逐测试点结果 |
| 管理 | 题目审核与上下架、用户角色、封禁与解封 |

## 仓库结构

```
iOJ/
├── client/     # 前端（已预留，尚未开始）
├── server/     # 后端服务：接口 + 评测（文档在 docs/server/）
├── docs/       # 文档：接口文档 + 后端技术文档（索引见 docs/README.md）
├── LICENSE
└── README.md
```

## 快速开始

- **只想对接接口**：见 [docs/shared/api.md](docs/shared/api.md)，包含每个接口的参数含义、字段含义与登录/未登录差异。
- **想启动后端**：见 [docs/server/README.md](docs/server/README.md) 的「快速开始」（建库 + 环境变量 + 启动 + 测试）。
- **想了解设计**：见 [docs/server/01-architecture.md](docs/server/01-architecture.md)（架构）、[docs/server/02-database.md](docs/server/02-database.md)（数据库）、[docs/server/03-judge.md](docs/server/03-judge.md)（评测）。

## 文档

完整文档在 [`docs/`](docs/README.md)：

| 文档 | 内容 |
| --- | --- |
| [docs/README.md](docs/README.md) | 文档索引与阅读建议 |
| [docs/shared/api.md](docs/shared/api.md) | 面向前端的接口文档：参数/字段含义、权限可见性、错误码、页面接口对照 |
| [docs/server/README.md](docs/server/README.md) | 后端总览：启动、配置、数据库初始化、测试、常见问题 |
| [docs/server/01-architecture.md](docs/server/01-architecture.md) | 架构、模块划分、请求链路、评测流程、权限矩阵、设计取舍 |
| [docs/server/02-database.md](docs/server/02-database.md) | ER 图、表结构、枚举、索引、容量估算、备份 |
| [docs/server/03-judge.md](docs/server/03-judge.md) | 评测状态机、引擎选择、Isolate 沙箱与限额、语言配置、生产环境加固 |
| [docs/server/04-deployment.md](docs/server/04-deployment.md) | 建库、配置项、systemd/Nginx、备份、上线检查清单 |
| [docs/server/05-development.md](docs/server/05-development.md) | 分层规范、扩展方式、测试说明、踩坑记录 |
| [docs/server/06-isolate.md](docs/server/06-isolate.md) | Isolate 安装、cgroup 服务、手动验证、接入说明 |

## 项目状态

已完成：

- 后端接口：账号、题库、测试数据、提交、评测、管理后台
- 评测：Isolate 沙箱（默认）、四种语言、逐测试点结果、异步执行
- 文档：接口文档（前端视角）+ 后端技术文档（架构、数据库、评测、部署、开发）
- 评测机：Isolate 2.7 接入完成（编译/运行隔离、MLE 判定、内存统计）

TODO：

- [x] 预览版前端（`preview-client/`）By NuxusAurora and ChatGPT
- [ ] 正式版前端（`client/`）
- [ ] Special Judge、Interactive、Run-Twice 题型支持
- [ ] 分布式判题器（`judge/`）

## 部署提示

- 生产环境必须修改默认管理员密码与登录令牌密钥（见 [docs/server/README.md](docs/server/README.md)）。
- 判题默认使用 Isolate 沙箱，需要判题机安装 Isolate、保持 `isolate.service` 运行并给运行用户 `isolate` 组权限（见 [docs/server/06-isolate.md](docs/server/06-isolate.md)）。只有可信环境才建议用 `engine=local` 回退。

## License

见 [LICENSE](LICENSE)。
