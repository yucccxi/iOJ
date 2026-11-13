# iOJ 文档

本文档索引列出 iOJ 的全部文档、目标读者和阅读路径。项目简介与快速开始见 [根目录 README](../README.md)。

## 1. 文档结构

```text
docs/
├── README.md                 # 本文档索引
├── api/
│   ├── README.md             # API 通用约定（认证、响应、分页、错误码、权限）
│   ├── frontend/             # 面向前端 / 外部调用方的 HTTP API
│   │   ├── README.md
│   │   ├── 01-auth.md
│   │   ├── 02-catalog.md
│   │   ├── 03-problems.md
│   │   ├── 04-submissions.md
│   │   ├── 05-admin.md
│   │   └── 06-reference.md
│   └── judge/                # 面向判题 Agent 的内部 HTTP API
│       ├── README.md
│       └── 01-internal-api.md
└── server/                   # 后端技术文档
    ├── README.md
    ├── 01-architecture.md
    ├── 02-database.md
    ├── 03-judge.md
    ├── 04-deployment.md
    ├── 05-development.md
    ├── 06-isolate.md
    ├── 07-judge-agent.md
    ├── 08-special-judge.md
    └── 09-catalog-sync.md
```

## 2. API 文档

### 2.1 通用约定

| 文档 | 读者 | 内容 |
| --- | --- | --- |
| [api/README.md](api/README.md) | 全部调用方 | Base URL、认证、统一响应、分页、时间格式、枚举、错误码总表、权限矩阵、CORS、限流、版本策略 |

### 2.2 前端 / 外部调用方

| 文档 | 读者 | 内容 |
| --- | --- | --- |
| [api/frontend/README.md](api/frontend/README.md) | 前端开发、外部调用方 | 阅读顺序、调用方与认证、页面与接口对照、关键流程、契约易错点 |
| [api/frontend/01-auth.md](api/frontend/01-auth.md) | 前端 | 图形验证码、邮箱验证码、注册/登录、找回密码、个人资料 |
| [api/frontend/02-catalog.md](api/frontend/02-catalog.md) | 前端 | 统一题库搜索、语言/标签/scope 参考数据 |
| [api/frontend/03-problems.md](api/frontend/03-problems.md) | 前端、出题者 | 题目详情/创建/编辑/删除、判题器、隐藏测试点 |
| [api/frontend/04-submissions.md](api/frontend/04-submissions.md) | 前端 | 提交代码、提交列表/详情、评测状态、轮询示例 |
| [api/frontend/05-admin.md](api/frontend/05-admin.md) | 前端、管理员 | 题目审核、用户角色/封禁、标签、scope、判题 key、判题节点 |
| [api/frontend/06-reference.md](api/frontend/06-reference.md) | 前端 | 可能为 `null` 的字段、状态补充、常见现象与处理 |

### 2.3 判题 Agent（内部）

| 文档 | 读者 | 内容 |
| --- | --- | --- |
| [api/judge/README.md](api/judge/README.md) | Agent 开发、后端、运维 | 内部接口索引、与前端 API 的区别、生命周期、关键约定 |
| [api/judge/01-internal-api.md](api/judge/01-internal-api.md) | Agent 开发、后端 | 认证、协议版本、配置/节点/claim/心跳/bundle/result/release、数据模型、租约与重试 |

> **内部接口**：`/internal/judge/**` 仅供判题 Agent 使用，不得暴露到公网或写进公开调用示例。

## 3. 后端技术文档

| 文档 | 读者 | 内容 |
| --- | --- | --- |
| [server/README.md](server/README.md) | 后端、运维 | 技术栈、模块结构、快速启动、测试、配置入口、常见问题 |
| [server/01-architecture.md](server/01-architecture.md) | 后端 | 目标与约束、模块边界、请求链路、分布式评测、权限模型、安全与设计取舍 |
| [server/02-database.md](server/02-database.md) | 后端、运维 | ER 图、表结构、枚举、索引、事务、容量估算、初始化与备份 |
| [server/03-judge.md](server/03-judge.md) | 后端、运维、出题者 | 状态机、引擎选择、沙箱参数、语言配置、Special Judge 实现 |
| [server/04-deployment.md](server/04-deployment.md) | 运维 | 环境要求、建库、环境变量总表、systemd/Nginx、备份恢复、升级回滚、上线检查 |
| [server/05-development.md](server/05-development.md) | 开发 | 分层约定、新增接口/语言/引擎、数据库脚本规则、测试、框架注意事项、已知简化 |
| [server/06-isolate.md](server/06-isolate.md) | 运维 | Isolate 安装、cgroup、权限、实际命令、手工验证、容量与排障 |
| [server/07-judge-agent.md](server/07-judge-agent.md) | 运维、后端 | Agent 启动校验、构建、key、配置、systemd、多节点、排障、安全 |
| [server/08-special-judge.md](server/08-special-judge.md) | 出题者、后端 | Checker 适用场景、文件协议、退出码、限制、本地测试、上传 |
| [server/09-catalog-sync.md](server/09-catalog-sync.md) | 运维、后端 | clist.by 外部题库同步、字段映射、增量水位、全量重建、排障 |

## 4. 阅读路径

| 你的角色 | 建议顺序 |
| --- | --- |
| 前端开发 | [根 README](../README.md) 了解功能 → [API 通用约定](api/README.md) → [前端 API 索引](api/frontend/README.md) → 按页面跳转领域文档 |
| 后端开发 | [后端总览](server/README.md) 跑起来 → [架构设计](server/01-architecture.md) → [数据库设计](server/02-database.md) → [判题设计](server/03-judge.md) → [开发指南](server/05-development.md) |
| 判题机运维 | [部署与运维](server/04-deployment.md) → [Isolate 沙箱运维](server/06-isolate.md) → [判题 Agent 部署与运维](server/07-judge-agent.md) → [内部判题 API](api/judge/README.md) |
| 出题者 | [自定义判题器](server/08-special-judge.md) → [题目与测试数据 API](api/frontend/03-problems.md) |
| 外部题库维护 | [外部题库同步](server/09-catalog-sync.md) → [数据库设计](server/02-database.md) |

## 5. 维护约定

- API 行为有变化时，先更新 `docs/api/` 下对应的领域文档，再改实现；接口字段、枚举、错误码、权限变化必须同步。
- 表结构、索引或迁移脚本变化时，同时更新 [数据库设计](server/02-database.md) 与 `server/src/main/resources/db/schema.sql`。
- 配置项、默认值或环境变量变化时，更新 [部署与运维](server/04-deployment.md) 的环境变量总表；其他文档只链接，不复制默认值。
- 核心流程、状态机或协议变化时，更新 [判题设计](server/03-judge.md) 与 [内部判题 API](api/judge/01-internal-api.md)。
- 新增、重命名或删除文档时，在同一次修改中更新本索引和所有相对链接。
- 文档中不得出现真实密码、令牌、密钥、连接凭据、用户邮箱或个人信息；示例统一使用 `<token>`、`<password>`、`<host>` 等占位符。
