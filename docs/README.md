# iOJ 文档

项目整体介绍见 [根目录 README](../README.md)。本文档目录分两部分：**接口文档**（`shared/`，前后端共用的接口契约）与
**后端技术文档**（`server/`）。

## 文档索引

### 接口文档（前后端共用）

| 文档 | 读者 | 内容 |
| --- | --- | --- |
| [shared/api/](shared/api/README.md) | 前端 / 对接方 | 按章节拆分的接口文档（入口 `README.md`）：通用约定、权限与可见性、每个接口的参数与字段含义、错误码、页面接口对照 |

### 后端技术文档

| 文档 | 内容 |
| --- | --- |
| [server/README.md](server/README.md) | 后端总览：技术栈、代码结构、启动、配置项、测试、常见问题 |
| [server/01-architecture.md](server/01-architecture.md) | 架构、模块划分、请求链路、评测流程、权限矩阵、设计取舍 |
| [server/02-database.md](server/02-database.md) | ER 图、表结构、枚举、索引、容量估算、初始化与备份 |
| [server/03-judge.md](server/03-judge.md) | 评测状态机、引擎选择、Isolate 沙箱与限额、语言配置、生产环境加固 |
| [server/04-deployment.md](server/04-deployment.md) | 环境要求、建库、配置项、systemd/Nginx、备份、上线检查清单 |
| [server/05-development.md](server/05-development.md) | 代码结构、分层规范、扩展方式、测试说明、踩坑记录 |
| [server/06-isolate.md](server/06-isolate.md) | Isolate 安装、cgroup 服务、手动验证命令、接入说明 |
| [shared/special-judge.md](shared/special-judge.md) | 出题者视角的自定义判题器（Special Judge）编写与上传指南 |

## 文档结构

```
docs/
├── README.md                 # 本文档索引
├── shared/
│   ├── api/                  # 接口文档（前端对接），按章节拆分
│   │   ├── README.md         # 目录与维护约定
│   │   ├── 01-conventions.md # 通用约定（认证、分页、错误码、权限、CORS）
│   │   ├── 02-accounts.md    # 账号
│   │   ├── 03-users.md       # 用户资料
│   │   ├── 04-problems.md    # 题库（含 4.6 判题器）
│   │   ├── 05-testcases.md   # 测试数据
│   │   ├── 06-submissions.md # 提交与评测
│   │   ├── 07-meta.md        # 参考数据
│   │   ├── 08-admin.md       # 管理后台
│   │   ├── 09-appendix.md    # 附录
│   │   └── 10-catalog.md     # 统一搜索（本地题库 + 外部 OJ）
│   └── special-judge.md      # 出题者：自定义判题器（Special Judge）指南
└── server/
    ├── README.md             # 后端总览（启动/配置/测试/排错）
    ├── 01-architecture.md
    ├── 02-database.md
    ├── 03-judge.md
    ├── 04-deployment.md
    ├── 05-development.md
    └── 06-isolate.md
```

## 阅读建议

| 你的角色 | 建议顺序 |
| --- | --- |
| 前端开发 | [根 README](../README.md) 了解功能 → [shared/api/README.md](shared/api/README.md) 对接接口（按章节跳转） |
| 出题者 | [shared/special-judge.md](shared/special-judge.md)（判题器编写与上传）→ [shared/api/04-problems.md](shared/api/04-problems.md) 4.6 节 |
| 后端开发 | [server/README.md](server/README.md) 跑起来 → [01-architecture](server/01-architecture.md) → [02-database](server/02-database.md) → [03-judge](server/03-judge.md) → [05-development](server/05-development.md) |
| 部署 / 运维 | [server/04-deployment.md](server/04-deployment.md)（含上线检查清单）→ [server/06-isolate.md](server/06-isolate.md)（判题沙箱） |

## 维护约定

- 接口行为有变化时，先更新 [shared/api/](shared/api/README.md) 下对应的章节文件，再改实现。
- 表结构有变化时，同步更新 [server/02-database.md](server/02-database.md) 与 `server/src/main/resources/db/schema.sql`。
- 文中的相对链接保持可点击；新增文档后请补充到本索引。
