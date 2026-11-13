# 后端总览

本文是 iOJ 后端文档的入口，说明技术栈、模块划分、快速启动、测试和常见问题，读者为后端开发与运维人员。部署细节、完整环境变量表和判题机配置分别见 [部署与运维](04-deployment.md)、[Isolate 沙箱运维](06-isolate.md) 和 [判题 Agent 部署与运维](07-judge-agent.md)。

> 返回目录：[文档总索引](../README.md)

## 1. 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 21 |
| 框架 | Spring Boot 4.0.9（Spring Framework 7、Spring Security 7、Jackson 3） |
| 持久层 | MyBatis 4（XML mapper）+ MySQL 8 |
| 鉴权 | 无状态 JWT（jjwt 0.12.6）+ BCrypt 密码哈希 |
| 邮件 | Spring Mail（Jakarta Mail），注册与找回密码验证码 |
| 参数校验 | Jakarta Validation |
| 测试 | JUnit 5 + AssertJ + H2（MySQL 兼容模式），含真实编译运行的端到端测试 |
| 评测 | 独立 Judge Agent + Isolate 沙箱；主服务器只负责任务调度与结果落库 |

## 2. 模块与代码结构

```text
iOJ/
├── pom.xml                 # 父 POM，聚合 judge-protocol / judge-core / judge-agent / server
├── judge-protocol/         # 主服务器与判题 Agent 共用的协议 DTO / 枚举
├── judge-core/             # 判题引擎、沙箱、输出比对、Special Judge
├── judge-agent/            # 独立判题进程：claim、Isolate 执行、结果回报
├── server/                 # 主服务器：REST API、数据库、调度
├── client/                 # 正式前端（React，开发中）
├── scripts/                # clist.by 外部题库同步脚本
└── docs/                   # 项目文档
```

`server/src/main/java/me/iynotuo/judge/` 的包划分：

| 包 | 内容 |
| --- | --- |
| `common/` | 统一响应、分页、错误码、全局异常、业务枚举 |
| `config/` | 安全配置、配置项、启动自检 |
| `security/` | JWT 签发与校验、判题 key 过滤器、登录身份 |
| `user/` | 注册登录、邮箱验证码、个人资料、管理员用户管理 |
| `problem/` | 题目、样例、测试数据、标签、scope、外部题库元数据、统一搜索 |
| `submission/` | 提交记录与逐测试点结果 |
| `judge/` | 远程任务调度、结果落库、租约回收、key/节点管理；`local/` 为本地调试实现 |

分层约定：`Controller` 只做参数绑定、权限注解和响应包装；`Service` 持有事务与领域规则；`Mapper` 只写 SQL；数据库实体与对外 DTO 分离。详见 [开发指南](05-development.md)。

## 3. 快速开始

### 3.1 环境要求

主服务器：

- JDK 21
- MySQL 8
- 不需要安装 Isolate 或编译器（默认 `mode=remote`、`engine=none`）
- 不需要单独安装 Maven，仓库自带 `./mvnw`

判题机（每台）：

- Linux（Debian/Ubuntu），cgroup v2，内核 ≥ 5.19
- JDK 21（运行 Agent，也是 Java 评测工具链）
- `g++`（C++17）、`gcc`（C11）、`python3`（按启用的语言安装）
- Isolate 沙箱：`isolate.service` 运行中，运行 Agent 的用户能执行 `/usr/bin/isolate`
- 能出站访问主服务器的内部 API

安装与授权示例（Debian，详见 [Isolate 沙箱运维](06-isolate.md)）：

```bash
sudo apt install isolate openjdk-21-jdk g++ gcc python3
sudo systemctl enable --now isolate.service
sudo usermod -aG isolate "$USER"     # 重新登录后生效；官方 setuid 包可省略
isolate --check-config               # 无输出且退出码为 0 即可
```

> 只在测试或本地开发时使用 `IOJ_JUDGE_MODE=local` + `IOJ_JUDGE_ENGINE=local`；生产环境由 Agent 判题。

### 3.2 建库并初始化

```bash
mysql -uroot -p <<'SQL'
CREATE DATABASE ioj DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'ioj'@'localhost' IDENTIFIED BY '<db-password>';
GRANT ALL PRIVILEGES ON ioj.* TO 'ioj'@'localhost';
FLUSH PRIVILEGES;
SQL

# 在仓库根目录执行
mysql -uioj -p ioj < server/src/main/resources/db/schema.sql
mysql -uioj -p ioj < server/src/main/resources/db/seed.sql
```

`seed.sql` 会创建默认管理员 **admin / admin123456**、10 个常用标签和保留 scope `LOCAL`，**首次登录后请立即修改密码**。

已有数据库升级到分布式判题时，不得重跑 `schema.sql`，在仓库根目录先备份再执行一次迁移脚本：

```bash
mysqldump -uioj -p --single-transaction ioj > ioj-before-distributed-judge.sql
mysql -uioj -p ioj < server/src/main/resources/db/migrate-distributed-judge.sql
```

### 3.3 配置与启动

完整环境变量表在 [部署与运维 §3](04-deployment.md#3-配置项)，这里只列启动主服务器必须覆盖的项：

- `IOJ_DB_USER`、`IOJ_DB_PASSWORD`：数据库账号密码。
- `IOJ_JWT_SECRET`：至少 32 字节的随机串，例如 `openssl rand -base64 48`。
- 启用邮箱验证时：`IOJ_MAIL_HOST`、`IOJ_MAIL_FROM`，以及 SMTP 认证信息。

以下命令在**仓库根目录**执行：

```bash
# 构建 server 及其依赖模块
./mvnw -B -pl server -am clean package

# 启动主服务器（默认 8080 端口）
IOJ_DB_USER=ioj IOJ_DB_PASSWORD='<db-password>' \
IOJ_JWT_SECRET="$(openssl rand -base64 48)" \
java -jar server/target/server-0.0.1-SNAPSHOT.jar
```

默认端口为 `8080`。需要改端口时使用 `SERVER_PORT` 或 `--server.port`，并同步调整前端代理与 Nginx。

启动时会先做一次数据库连通性检查，成功打印 `数据库连接正常: ...`；失败会打印实际生效的 JDBC URL 与用户名（不打印密码）并终止启动。

验证：

```bash
curl -s http://localhost:8080/api/v1/meta/languages
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"account":"admin","password":"admin123456"}'
```

创建判题机 key（需要管理员 token）：

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/judge-keys \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"judge-1"}'
```

响应中的 `data.key` 只出现一次，保存到判题机的 `IOJ_JUDGE_KEY`。Agent 的启动与 systemd 配置见 [判题 Agent 部署与运维](07-judge-agent.md)。

### 3.4 运行测试

```bash
# 全部模块，使用 H2 内存库，不需要 MySQL
./mvnw test

# 只跑 server 的端到端流程（会真实调用 javac 编译并评测）
./mvnw -pl server -am test -Dtest=ApiFlowIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

测试 profile 的关键行为：

- 每个 Spring 上下文使用独立 H2 内存库，启动时执行 `db/schema.sql`。
- `ioj.email-code.enabled=false`、`ioj.captcha.enabled=false`，注册与登录流程不需要邮件/图形验证码；相关测试类单独打开开关并注入假发送器。
- `ioj.judge.mode=local`、`ioj.judge.enabled=false`，测试显式调用 `JudgeService`；分布式测试用 `remote` 覆盖。
- 真实编译依赖本机 JDK；Java 源码必须使用 `public class Main`。

主要测试类：

| 测试 | 覆盖内容 |
| --- | --- |
| `ServerApplicationTests` | Spring 上下文与全部 Mapper XML 可加载 |
| `JwtTokenProviderTest` | 令牌签发/解析、token 版本、篡改、过期、弱密钥拒绝 |
| `ApiFlowIntegrationTest` | 注册→登录→建题→传测试点→提交→本地评测→AC/WA→字段裁剪 |
| `DistributedJudgeIntegrationTest` | key、节点心跳、claim、ZIP bundle、任务心跳、结果回报、幂等、旧 token 拒绝、租约回收 |
| `AdminModerationIntegrationTest` | 测试点增删改查、题目上下架、用户搜索与封禁 |
| `AdminMetaIntegrationTest` | 标签/scope 字典、重复处理、使用量、`LOCAL` 保护、删除保护 |
| `CheckerIntegrationTest` | 判题器读写/清空、越权访问、`hasChecker`、`CHECKER_ERROR` |
| `EmailVerificationIntegrationTest` | 发码冷却/每小时上限、注册验证码、一次性消费、输错上限、过期拒绝 |
| `PasswordResetIntegrationTest` | 找回密码全链路、用途隔离、旧密码/旧 token 失效 |
| `TokenVersionIntegrationTest` | 修改密码/登出所有设备使旧 token 失效 |
| `CaptchaIntegrationTest` / `CaptchaRateLimitIntegrationTest` | 图形验证码签发、purpose 绑定、一次性消费、单 IP 限流 |
| `LimitConfigIntegrationTest` | 测试点字节上限与代码长度上限来自配置 |

## 4. 数据库与配置

- 建表脚本：`server/src/main/resources/db/schema.sql`（MySQL 8 与 H2 MySQL 模式通用）。
- 初始数据：`server/src/main/resources/db/seed.sql`（幂等）。
- 分布式判题迁移：`server/src/main/resources/db/migrate-distributed-judge.sql`（只用于已有数据库，`ALTER TABLE` 部分不可重复执行）。
- 表结构、索引、容量估算与备份说明见 [数据库设计](02-database.md)。
- 完整环境变量与默认值见 [部署与运维 §3](04-deployment.md#3-配置项)；受版本控制的配置文件是 `server/src/main/resources/application.yaml`。

> `ioj.problem.auto-publish=true` 时，`POST /problems` 会直接返回 `PUBLISHED`，**即使题目还没有测试点**；此时题面可访问，但提交会返回 `3003`。只有管理员通过 `PATCH /admin/problems/{id}/status` 上架时才强制要求至少一个测试点。若希望“有测试点才可见”，建议关闭自动发布或在上传后补测试点。

## 5. 评测概览

- 主服务器默认 `mode=remote`、`engine=none`，不执行用户代码。
- Agent 启动时拉取语言与探测命令，校验全部工具链后开始 claim；缺少任意语言会拒绝启动。
- 任务通过 `claim_token` + `lease_expires_at` 租约归属；心跳续租，过期由 `JudgeLeaseReaper` 回收，超过最大尝试次数判 `SYSTEM_ERROR`。
- 测试数据总量不超过 `inline-max-bytes` 时内联在 claim 响应；超过时通过 ZIP bundle 下载（唯一入口 `testcases.json`）。
- 默认输出比对忽略行尾空格/Tab 与文末空行；题目带自定义 C++ 判题器时按退出码 0/1/2/其他判定。
- 失败即停：某个测试点未通过后不再执行后续测试点，所以 `cases` 可能少于测试点总数。

状态机、沙箱参数、语言配置和 Special Judge 细节见 [判题设计](03-judge.md)；出题者编写判题器的说明见 [自定义判题器](08-special-judge.md)。

## 6. 接口

- 前端接口统一前缀 `/api/v1`，响应为 `{ code, message, data }`，完整契约见 [前端 API 文档](../api/frontend/README.md)。
- 判题 Agent 接口前缀 `/internal/judge`，使用独立 Bearer key，见 [内部判题 API](../api/judge/README.md)。
- 常用自测：

```bash
curl -s localhost:8080/api/v1/meta/languages | jq
curl -s "localhost:8080/api/v1/catalog?page=1&size=5" | jq
```

## 7. 开发约定

- 分层：`Controller` → `Service` → `Mapper`；对外 DTO 与数据库实体分离，敏感字段不进入响应。
- 统一用构造器注入；业务异常抛 `BusinessException(ErrorCode.XXX)`，由全局异常处理器转成统一响应。
- 新增接口 / 语言 / 评测引擎的步骤见 [开发指南](05-development.md)。
- 改表结构时同步更新 `db/schema.sql` 与 [数据库设计](02-database.md)；`schema.sql` 必须同时能在 MySQL 8 与 H2（MySQL 模式）执行。
- 文档与代码同一次提交更新：接口行为、字段、错误码、配置项和状态机变化都必须同步更新对应文档。

## 8. 常见问题

| 现象 | 处理 |
| --- | --- |
| 启动报 `CannotCreateTransactionException` | 看启动日志里的“实际使用的 JDBC URL / 用户名”：多是 `IOJ_DB_*` 没设置在**当前进程**上。bash 中一行一个 `export`；systemd 用 `EnvironmentFile`；IDEA 在 Run Configuration 的环境变量里配置 |
| 启动报端口占用 | 换端口：`--server.port=8081`，并同步调整前端代理与 Nginx |
| 提交一直“排队中” | 没有在线 Agent：检查 `GET /api/v1/admin/judge-nodes`、Agent 日志、`IOJ_JUDGE_KEY` 是否正确；确认 Agent 支持该语言 |
| 提交一直“评测中” | Agent 崩溃或失联；等 `IOJ_JUDGE_LEASE_SECONDS` 后由租约回收任务，检查 Agent 日志和节点心跳 |
| Agent 启动报 `ioj.agent.key 未配置` | 没有设置管理员创建的 `IOJ_JUDGE_KEY` |
| Agent claim 返回 `401` | key 错误、被禁用或已过期；重新创建/启用 key |
| Agent claim 返回 `409` | 同一 `nodeId` 已绑定其他 key；换一个 `IOJ_JUDGE_NODE_ID`，或删除旧 key（会级联删除旧节点）后重建 |
| 注册返回 `2009` / 收不到验证码邮件 | 看日志中的 `验证码邮件发送失败`：检查 `IOJ_MAIL_HOST`、端口/加密方式（465 用 SSL，587 用 STARTTLS）以及 `IOJ_MAIL_FROM` 是否与 SMTP 账号匹配 |
| 注册返回 `2008` | 同邮箱 60 秒冷却 / 每小时上限默认 5 次；按 `message` 提示用户等待 |
| 本地开发不想配 SMTP | 设 `IOJ_EMAIL_CODE_ENABLED=false`，注册不再校验验证码 |
| 登录/发码返回 `2010` | 图形验证码错误、过期、已使用或 purpose 不匹配；重新获取验证码 |
| 获取图形验证码返回 `1006` | 单 IP 申请过于频繁；可调大 `IOJ_CAPTCHA_ISSUE_PER_MINUTE_PER_IP` / `IOJ_CAPTCHA_ISSUE_PER_HOUR_PER_IP` |
| 本地开发不想输入图形验证码 | 设 `IOJ_CAPTCHA_ENABLED=false` |
| 启动报 `isolate binary is not executable` | 运行用户没有执行权限：`sudo usermod -aG isolate <user>` 后重新登录，确认 `isolate --version` 可用 |
| `isolate --init` 报 `This box belongs to a different user` | box 编号被其他用户（常见 root）占用：换 `box-id-start`，或让原用户 `isolate --box-id=N --cleanup` |
| 编译报 `javac: No such file or directory` | 沙箱内无法跟随 `/etc/alternatives`：保持 `resolve-executables: true`，或改用绝对路径 |
| JVM 报 `Error loading java.security file` | `extra-dirs` 缺少 `/etc/java-21-openjdk`（Debian） |
| JVM 报 `pthread_create failed (EAGAIN)` | `isolate-processes` 太小：Java 建议 64 |
| 评测报编译错误但代码没问题 | 评测机缺少工具链：`apt install -y g++ gcc python3`；Java 代码需使用 `public class Main` |
| `schema.sql` 执行报“表已存在” | 该脚本不是幂等的；新库重建，已有库执行 `db/migrate-distributed-judge.sql` |
| 多个实例间登录互相 `401` | 各实例的 `IOJ_JWT_SECRET` 必须一致 |
| 上传较大的测试数据失败 | `1000` 是单条超限，调大 `IOJ_PROBLEM_MAX_TESTCASE_BYTES`；`413` 是请求体过大，调大 Nginx 的 `client_max_body_size`；`500` 且日志提示 packet，调大 MySQL 的 `max_allowed_packet` |
| 测试环境与线上行为不一致 | 测试使用 H2（MySQL 模式），避免 MySQL 专有语法，详见 [开发指南 §5](05-development.md#5-数据库脚本规则) |

## 相关文档

- [架构设计](01-architecture.md)
- [数据库设计](02-database.md)
- [判题设计](03-judge.md)
- [部署与运维](04-deployment.md)
- [开发指南](05-development.md)
- [Isolate 沙箱运维](06-isolate.md)
- [判题 Agent 部署与运维](07-judge-agent.md)
- [自定义判题器](08-special-judge.md)
- [外部题库同步](09-catalog-sync.md)
- [前端 API 文档](../api/frontend/README.md)
- [内部判题 API](../api/judge/README.md)
