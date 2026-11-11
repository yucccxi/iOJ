# iOJ Server（后端）

iOJ 的后端服务：一个 Spring Boot 单体应用，同时提供 **REST API** 和 **异步评测**。
接口契约见 [../shared/api/README.md](../shared/api/README.md)；项目整体介绍见 [../../README.md](../../README.md)。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 21 |
| 框架 | Spring Boot 4.0.9（Spring Framework 7、Spring Security 7、Jackson 3） |
| 持久层 | MyBatis 4（XML mapper）+ MySQL 8 |
| 鉴权 | 无状态令牌（jjwt 0.12.6）+ BCrypt 密码哈希 |
| 邮件 | Spring Mail（Jakarta Mail / angus-mail），注册邮箱验证码 |
| 参数校验 | Jakarta Validation |
| 测试 | JUnit 5 + AssertJ + H2（MySQL 兼容模式），含真实编译运行的端到端测试 |
| 评测 | 独立 Judge Agent + Isolate 沙箱；主服务器只负责任务调度与结果落库 |

## 代码结构

```
iOJ/
├── pom.xml                 # 父 POM，聚合 judge-protocol / judge-core / server
├── judge-protocol/         # 主服务器与判题 Agent 共用的协议 DTO / 枚举
├── judge-core/             # 判题引擎、沙箱、输出比对、Special Judge
├── judge-agent/            # 独立判题进程：claim、Isolate 执行、结果回报
└── server/
    ├── pom.xml
    ├── mvnw / mvnw.cmd
    └── src/
        ├── main/java/me/iynotuo/judge/
        │   ├── common/        统一响应、分页、错误码、全局异常、枚举
        │   ├── config/        安全配置、配置项、评测线程池、启动自检
        │   ├── security/      令牌签发与校验、登录身份
        │   ├── user/          注册登录、邮箱验证码（`service` + `mail`）、个人资料、管理员用户管理
        │   ├── problem/       题目、样例、测试数据、标签、scope、外部题库元数据、统一搜索
        │   ├── submission/    提交记录
        │   └── judge/         任务调度（JudgeTaskService/JudgeResultService）、key/节点、租约回收
        ├── main/resources/
        │   ├── application.yaml
        │   ├── db/schema.sql  建表脚本（MySQL / H2 通用）
        │   ├── db/seed.sql    初始管理员与标签（可重复执行）
        │   └── mapper/*.xml   SQL 映射
        └── test/
            ├── java/...       单元测试与端到端测试
            └── resources/application-test.yaml
```

## 快速开始

### 1. 环境要求

**主服务器：**

- JDK 21
- MySQL 8
- 不需要安装 Isolate 或编译器（默认不本地判题）
- 不需要单独安装 Maven，仓库自带 `./mvnw`

**判题机（每台）：**

- Linux（Debian/Ubuntu），cgroup v2，内核 ≥ 5.19
- JDK 21（运行 Agent，也是 Java 评测工具链）
- `g++`（C++17）、`gcc`（C11）、`python3`——按需要安装
- Isolate 沙箱：`isolate.service` 运行中，运行 Agent 的用户能执行 `/usr/bin/isolate`
- 能出站访问主服务器的内部 API

安装/授权示例（Debian 13，详见 [06-isolate.md](./06-isolate.md)）：

```bash
sudo apt install isolate openjdk-21-jdk g++ gcc python3
sudo systemctl enable --now isolate.service
sudo usermod -aG isolate "$USER"     # 重新登录后生效
isolate --check-config               # 无输出且退出码为 0 即可
```

> 只在测试或本地开发时使用 `IOJ_JUDGE_MODE=local` + `IOJ_JUDGE_ENGINE=local`；生产环境由 Agent 判题。

### 2. 建库并初始化

```bash
mysql -uroot -p <<'SQL'
CREATE DATABASE ioj DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'ioj'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON ioj.* TO 'ioj'@'localhost';
FLUSH PRIVILEGES;
SQL

# 在仓库根目录执行
mysql -uioj -pchange-me ioj < server/src/main/resources/db/schema.sql
mysql -uioj -pchange-me ioj < server/src/main/resources/db/seed.sql
```

已有数据库升级到分布式判题时，不要重跑 `schema.sql`，执行一次迁移脚本：

```bash
mysqldump -uioj -p --single-transaction ioj > ioj-before-distributed-judge.sql
mysql -uioj -p ioj < server/src/main/resources/db/migrate-distributed-judge.sql
```

`seed.sql` 会创建默认管理员 **admin / admin123456**、10 个常用标签和保留 scope `LOCAL`，**首次登录后请立即修改密码**。

### 3. 配置环境变量

主服务器：

| 变量 | 是否必填 | 说明 |
| --- | --- | --- |
| `IOJ_DB_HOST` / `IOJ_DB_PORT` / `IOJ_DB_NAME` | 否 | 默认 `127.0.0.1` / `3306` / `ioj` |
| `IOJ_DB_USER` / `IOJ_DB_PASSWORD` | **是** | 数据库账号密码（默认值仅用于本地示例，生产必须覆盖） |
| `IOJ_JWT_SECRET` | **是** | 登录令牌签名密钥，≥32 字节；用 `openssl rand -base64 48` 生成 |
| `IOJ_CORS_ORIGINS` | 否 | 允许的前端来源，默认 `http://localhost:5173`，多个用逗号分隔 |
| `IOJ_PROBLEM_AUTO_PUBLISH` | 否 | 普通用户上传题目是否直接发布，默认 `true` |
| `IOJ_JUDGE_MODE` | 否 | 默认 `remote`；`local` 仅用于测试/本地开发 |
| `IOJ_JUDGE_ENGINE` | 否 | 主服务器默认 `none`；Agent 配置为 `isolate` |
| `IOJ_JUDGE_LEASE_SECONDS` | 否 | 任务租约，默认 `300` 秒 |
| `IOJ_JUDGE_MAX_ATTEMPTS` | 否 | 任务最多领取次数，默认 `3` |
| `IOJ_JUDGE_MAX_CLAIM_BATCH` | 否 | 单次 claim 最多任务数，默认 `10` |
| `IOJ_MAIL_HOST` / `IOJ_MAIL_PORT` | **是**（开启邮箱验证时） | SMTP 服务器；默认 `smtp.localhost.invalid`（不可解析），不配就会发信失败 |
| `IOJ_MAIL_USERNAME` / `IOJ_MAIL_PASSWORD` | 视 SMTP 而定 | SMTP 账号；默认 `mail.smtp.auth=false`，需要认证时同时设 `IOJ_MAIL_SMTP_AUTH=true` |
| `IOJ_MAIL_SSL` / `IOJ_MAIL_STARTTLS` | 否 | 加密方式；465 端口用 `IOJ_MAIL_SSL=true`，587 端口用 `IOJ_MAIL_STARTTLS=true` |
| `IOJ_MAIL_FROM` | 否 | 发件人地址，必须是 SMTP 账号能发送的身份，默认 `no-reply@localhost` |
| `IOJ_EMAIL_CODE_ENABLED` | 否 | 注册是否必须通过邮箱验证码，默认 `true`；设 `false` 则注册不需要验证码、发码接口直接报错 |
| `IOJ_EMAIL_CODE_TTL_MINUTES` | 否 | 验证码有效期，默认 `10` 分钟 |
| `IOJ_EMAIL_CODE_RESEND_INTERVAL_SECONDS` | 否 | 同一邮箱的重发冷却，默认 `60` 秒 |
| `IOJ_EMAIL_CODE_MAX_PER_HOUR` | 否 | 同一邮箱每小时最多发几次，默认 `5` |
| `IOJ_EMAIL_CODE_MAX_ATTEMPTS` | 否 | 单个验证码最多输错几次，默认 `5`；达到上限立即作废 |
| `IOJ_CAPTCHA_ENABLED` | 否 | 是否要求登录/发邮箱验证码时提交图形验证码，默认 `true` |
| `IOJ_CAPTCHA_TTL_SECONDS` | 否 | 图形验证码有效期，默认 `120` 秒 |
| `IOJ_CAPTCHA_MAX_ATTEMPTS` | 否 | 单张图形验证码最多输错次数，默认 `3` |
| `IOJ_CAPTCHA_ISSUE_PER_MINUTE_PER_IP` / `IOJ_CAPTCHA_ISSUE_PER_HOUR_PER_IP` | 否 | 单 IP 申请图形验证码的频率上限，默认 `60/分钟`、`600/小时` |

判题 Agent（每台判题机）：

| 变量 | 是否必填 | 说明 |
| --- | --- | --- |
| `IOJ_JUDGE_SERVER_URL` | **是** | 主服务器内部地址，例如 `http://10.0.0.10:8080` |
| `IOJ_JUDGE_KEY` | **是** | 管理员在 `/api/v1/admin/judge-keys` 创建的 key |
| `IOJ_JUDGE_NODE_ID` | 否 | 节点 id，默认 `judge-1`；一台 Agent 一个唯一 id |
| `IOJ_JUDGE_CONCURRENCY` | 否 | 本机并发，默认 `1`，建议 CPU 核数 - 1 |
| `IOJ_JUDGE_POLL_INTERVAL_MS` / `IOJ_JUDGE_MAX_POLL_INTERVAL_MS` | 否 | 空闲轮询与最大退避，默认 `1000` / `5000` |
| `IOJ_JUDGE_LEASE_SECONDS` | 否 | 申请的任务租约，默认 `300` |
| `IOJ_JUDGE_HEARTBEAT_INTERVAL_MS` | 否 | 节点/任务心跳间隔，默认 `100000` |
| `IOJ_JUDGE_CONFIG_FETCH_RETRIES` / `IOJ_JUDGE_CONFIG_FETCH_RETRY_DELAY_MS` | 否 | 启动配置拉取重试次数/间隔，默认 `5` / `2000` |
| `IOJ_JUDGE_PROBE_TIMEOUT_MS` | 否 | 单条工具链探测超时，默认 `5000` |
| `IOJ_ISOLATE_BINARY` | 否 | Isolate 路径，默认 `/usr/bin/isolate` |
| `IOJ_ISOLATE_BOX_ID_START` / `IOJ_ISOLATE_BOX_ID_COUNT` | 否 | 本机 box 区间；同机多 Agent 必须分段 |

启动时服务会先做一次数据库连通性检查，成功会打印：

```
数据库连接正常: MySQL 8.4.11，当前库=ioj，账号=ioj@localhost
```

失败则打印**实际生效的 JDBC URL 与用户名**（不打印密码）并终止启动，便于排查环境变量没生效的问题。

### 4. 启动

以下命令在**仓库根目录**执行（多模块构建）：

```bash
# 先构建全部模块
./mvnw -DskipTests clean package

# 运行 server
IOJ_DB_USER=ioj IOJ_DB_PASSWORD='change-me' \
IOJ_JWT_SECRET="$(openssl rand -base64 48)" \
java -jar server/target/server-0.0.1-SNAPSHOT.jar

# 如果要用 spring-boot:run，先把 judge-protocol / judge-core / judge-agent 安装到本地仓库
./mvnw -DskipTests install
cd server && ./mvnw spring-boot:run
```

默认端口 `8080`。验证：

```bash
curl -s http://localhost:8080/api/v1/meta/languages
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"account":"admin","password":"admin123456"}'
```

创建判题机 key（管理员 token）：

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/judge-keys \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"judge-1"}'
```

响应中的 `data.key` 只出现一次，保存到判题机的环境变量 `IOJ_JUDGE_KEY`。

启动 Agent：

```bash
IOJ_JUDGE_SERVER_URL=http://127.0.0.1:8080 \
IOJ_JUDGE_KEY='jsk_...' \
IOJ_JUDGE_NODE_ID=judge-1 \
IOJ_JUDGE_CONCURRENCY=2 \
java -jar judge-agent/target/judge-agent-0.0.1-SNAPSHOT.jar
```

Agent 启动时会先拉取 `/internal/judge/config`，按主服务器的 `requiredLanguages` 逐项探测本机工具链。**缺少任意一种语言就拒绝启动**；全部通过后才发节点心跳并循环 claim。主服务器用 `GET /api/v1/admin/judge-nodes` 查看节点状态。

### 5. 运行测试

在仓库根目录执行：

```bash
./mvnw test                                # 全部模块，使用 H2 内存库，不需要 MySQL

# 只跑 server 的端到端流程（会真实调用 javac 编译并评测）
./mvnw -pl server -am test -Dtest=ApiFlowIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

测试覆盖：

| 测试 | 内容 |
| --- | --- |
| `ServerApplicationTests` | Spring 上下文可启动、全部 Mapper 可加载 |
| `JwtTokenProviderTest` | 令牌签发/解析、token 版本 claim、篡改、过期、弱密钥拒绝 |
| `OutputComparatorTest` / `IsolateMetaTest` / `ProgramAnswerCheckerTest` / `IsolateSandboxCommandTest` | 位于 `judge-core`：输出比对、Isolate meta、判题器状态映射、isolate 命令行 |
| `AgentTaskMapperTest` | 位于 `judge-agent`：协议任务 → JudgeContext、JudgeOutcome → JudgeResult |
| `AgentBootstrapTest` | 位于 `judge-agent`：启动配置拉取、协议校验、工具链探测、缺失语言拒绝启动 |
| `HttpJudgeAgentClientTest` / `AgentRunnerTest` | 位于 `judge-agent`：HTTP 协议与鉴权、claim → 执行 → 回报循环 |
| `JudgeAgentApplicationTest` | 位于 `judge-agent`：Spring 上下文、配置绑定和 Agent Bean 装配 |
| `ApiFlowIntegrationTest` | 注册→登录→建题→传测试数据→提交→本地评测→AC/WA→字段裁剪 |
| `DistributedJudgeIntegrationTest` | 管理员 key、节点心跳、claim、任务心跳、结果回报、重复结果幂等、旧 token 拒绝、租约回收 |
| `AdminModerationIntegrationTest` | 测试数据增删改查、题目上下架、用户搜索与封禁 |
| `CheckerIntegrationTest` | 判题器源码的读/写/清空、越权访问、`hasChecker` 暴露面、`CHECKER_ERROR` 判定 |
| `ProgramAnswerCheckerTest` | 判题器结果 → 提交状态的映射（假 Sandbox，覆盖 AC/WA/PE/超时/崩溃/编译失败） |
| `IsolateSandboxCommandTest` | isolate 命令行与 `/box` 路径翻译，锁住重构不能悄悄改动 |
| `AdminMetaIntegrationTest` | 标签/scope 字典的增删改、重复处理、使用量、外部题 scope 重命名传播、`LOCAL` 保留 scope 保护、删除保护 |
| `CaptchaIntegrationTest` | 图形验证码签发、登录/发码接入、purpose 绑定、一次性消费 |
| `CaptchaRateLimitIntegrationTest` | 单 IP 申请图形验证码的分钟窗口限流 |
| `LimitConfigIntegrationTest` | 测试点字节上限与代码长度上限确实来自配置，而不是 DTO 硬编码 |
| `EmailVerificationIntegrationTest` | 发码冷却/每小时上限、无码/错码/正码注册、一次性消费、输错上限作废、过期拒绝 |
| `PasswordResetIntegrationTest` | 找回密码全链路、用途隔离、一次性消费、输错上限、旧密码失效/新密码生效、旧 token 失效 |
| `TokenVersionIntegrationTest` | 修改密码/登出所有设备使旧 token 失效，logout-all 返回的新 token 可继续使用 |

## 配置项

除环境变量外，其余配置都在 `src/main/resources/application.yaml`：

| 配置项 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `server.port` | — | `8080` | HTTP 端口 |
| `spring.datasource.*` | `IOJ_DB_*` | `127.0.0.1:3306/ioj` | 数据库连接 |
| `ioj.datasource-check.enabled` | — | `true` | 启动时检查数据库连通性，失败即终止启动 |
| `ioj.jwt.secret` | `IOJ_JWT_SECRET` | 开发用弱密钥 | 令牌签名密钥，启动时校验长度 ≥32 字节 |
| `ioj.jwt.expiration` | — | `7d` | 令牌有效期 |
| `ioj.jwt.issuer` | — | `iOJ` | 令牌签发方标识 |
| `ioj.cors.allowed-origins` | `IOJ_CORS_ORIGINS` | `http://localhost:5173` | 允许的前端来源，逗号分隔 |
| `ioj.problem.auto-publish` | `IOJ_PROBLEM_AUTO_PUBLISH` | `true` | 普通用户上传题目是否直接发布；`false` 时进入待审核 |
| `ioj.problem.max-testcases` | `IOJ_PROBLEM_MAX_TESTCASES` | `100` | 单题测试数据条数上限 |
| `ioj.problem.max-testcase-bytes` | `IOJ_PROBLEM_MAX_TESTCASE_BYTES` | `4194304` | 单条测试点 `input`/`output` 各自的上限（UTF-8 字节，默认 4 MiB）；请求 DTO 不再硬编码，**只认这个配置** |
| `ioj.submission.max-code-length` | `IOJ_SUBMISSION_MAX_CODE_LENGTH` | `65536` | 单次提交代码字符数上限；超限返回 `4003` |
| `ioj.captcha.enabled` | `IOJ_CAPTCHA_ENABLED` | `true` | 是否启用图形验证码；关闭后登录/发码不再要求验证码 |
| `ioj.captcha.ttl-seconds` | `IOJ_CAPTCHA_TTL_SECONDS` | `120` | 图形验证码有效期 |
| `ioj.captcha.max-attempts` | `IOJ_CAPTCHA_MAX_ATTEMPTS` | `3` | 单张图形验证码最大输错次数 |
| `ioj.captcha.code-length` | `IOJ_CAPTCHA_CODE_LENGTH` | `5` | 图片字符数 |
| `ioj.captcha.issue-per-minute-per-ip` / `issue-per-hour-per-ip` | `IOJ_CAPTCHA_ISSUE_PER_MINUTE_PER_IP` / `IOJ_CAPTCHA_ISSUE_PER_HOUR_PER_IP` | `60` / `600` | 单 IP 申请频率上限 |
| `ioj.captcha.cleanup-retention-hours` | `IOJ_CAPTCHA_CLEANUP_RETENTION_HOURS` | `24` | 过期图形验证码保留时间 |
| `ioj.judge.enabled` | `IOJ_JUDGE_ENABLED` | `true` | 仅 local 模式使用；关闭后本地 worker 不消费任务 |
| `ioj.judge.mode` | `IOJ_JUDGE_MODE` | `remote` | `remote` 主服务器调度；`local` 测试/本地开发 |
| `ioj.judge.engine` | `IOJ_JUDGE_ENGINE` | `none` | 主服务器默认 `none`；Agent 为 `isolate`；`local` 仅测试 |
| `ioj.judge.isolate.*` | — | 见下 | Isolate 路径、box 编号、编译限制、额外目录等 |
| `ioj.judge.dispatch.lease-seconds` | `IOJ_JUDGE_LEASE_SECONDS` | `300` | Agent 任务租约 |
| `ioj.judge.dispatch.max-attempts` | `IOJ_JUDGE_MAX_ATTEMPTS` | `3` | 任务最多领取次数 |
| `ioj.judge.dispatch.max-claim-batch` | `IOJ_JUDGE_MAX_CLAIM_BATCH` | `10` | 单次 claim 最多任务数 |
| `ioj.judge.dispatch.inline-max-bytes` | `IOJ_JUDGE_INLINE_MAX_BYTES` | `1048576` | 测试数据超过该字节数时改用 bundle 下载 |
| `ioj.judge.concurrency` | `IOJ_JUDGE_CONCURRENCY` | `1` | local 模式同时评测数；Agent 使用 `ioj.agent.concurrency` |
| `ioj.judge.poll-interval-ms` | — | `1000` | local 模式拉取间隔 |
| `ioj.agent.*` | `IOJ_AGENT_*` / `IOJ_JUDGE_*` | 见 07-judge-agent.md | Agent 连接、并发、语言、心跳 |
| `ioj.judge.max-output-bytes` | — | `16777216` | 单个测试点允许的最大输出 |
| `ioj.judge.max-compile-output-bytes` | — | `65536` | 编译输出保留上限 |
| `ioj.judge.compile-timeout-ms` | — | `30000` | 编译超时（本机进程引擎使用） |
| `ioj.judge.languages.*` | — | JAVA / CPP / C / PYTHON3 | 各语言源码、编译/运行命令、时间系数、`isolate-processes`、`isolate-memory-extra-mb`、`probe-commands` |
| `ioj.judge.checker.*` | `IOJ_JUDGE_CHECKER_*` | 见下 | 自定义判题器（Special Judge）的开关、运行/编译限制与编译命令 |

> `ioj.problem.auto-publish=true` 时，`POST /problems` 会直接返回 `PUBLISHED`，**即使题目还没有测试点**；此时题面可访问，但提交会返回 `3003`。只有管理员通过 `PATCH /admin/problems/{id}/status` 上架时才强制要求至少一个测试点。若希望"有测试点才可见"，应关闭自动发布或在上传后补测试点。

判题器相关默认值（`application.yaml` 中可改）：

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `checker.enabled` | `true` | 关闭后，带判题器的题目一律判 `CHECKER_ERROR` |
| `checker.time-limit-ms` | `2000` | 单次判题器运行的 CPU 时间（独立于题目限制） |
| `checker.memory-limit-mb` | `512` | 单次判题器运行的内存 |
| `checker.compile-timeout-ms` | `30000` | 判题器编译的墙钟超时 |
| `checker.compile-memory-mb` | `512` | 判题器编译的内存 |
| `checker.source-file` | `checker.cpp` | 判题器源码写入的文件名 |
| `checker.compile-command` | `g++ -O2 -std=c++17 -pipe -o checker checker.cpp` | 必须产出名为 `checker` 的可执行文件 |
| `checker.message-limit-bytes` | `1024` | 判题器输出保留上限，对齐 `submission_cases.message` |

Isolate 相关默认值（`application.yaml` 中可改）：

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `isolate.binary` | `/usr/bin/isolate` | 可执行文件路径；启动时检查可执行 |
| `isolate.box-id-start` / `box-id-count` | `1` / `8` | box 编号区间，避免与 root 手工测试的 box 0 冲突 |
| `isolate.compile-processes` | `64` | 编译进程/线程上限（javac 需要较多线程） |
| `isolate.compile-time-seconds` / `compile-wall-time-seconds` | `10` / `30` | 编译 CPU / 墙钟限制 |
| `isolate.compile-cg-mem-kb` / `compile-fsize-kb` | `524288` / `16384` | 编译内存 / 文件大小限制 |
| `isolate.wall-time-extra-seconds` | `3` | 运行墙钟 = CPU 限制 + 该值 |
| `isolate.extra-dirs` | `/etc/java-21-openjdk` | 只读绑定进沙箱的宿主机目录（Debian JDK 的 `java.security`） |
| `isolate.resolve-executables` | `true` | 把命令第一个词解析成真实路径，避免 `/etc/alternatives` 符号链接 |

## 数据库

初始化脚本：

- `src/main/resources/db/schema.sql`：15 张表（`users`、`scopes`、`judge_keys`、`judge_nodes`、`problems`、`problem_samples`、`problem_testcases`、`tags`、`problem_tags`、`external_problems`、`external_problem_tags`、`submissions`、`submission_cases`、`email_verification_codes`、`captcha_challenges`）及外键、索引。
- `src/main/resources/db/migrate-distributed-judge.sql`：已有数据库升级脚本（新增 key/节点表和 submissions 租约字段）。
- `src/main/resources/db/seed.sql`：默认管理员、常用标签与保留 scope `LOCAL`，可重复执行。

注意：

- `schema.sql` **不是幂等**的，重复执行会报"表已存在"。需要重建时：
  ```bash
  mysql -uroot -p -e "DROP DATABASE ioj;
                      CREATE DATABASE ioj DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
  mysql -uioj -p ioj < server/src/main/resources/db/schema.sql
  mysql -uioj -p ioj < src/main/resources/db/seed.sql
  ```
- 外部题库元数据由 `scripts/sync_clist_problems.py` 从 clist.by 导入 Codeforces 与 AtCoder；`sync --clear` 会先清空 `external_problems` 再全量导入。脚本同时按平台阈值计算 `difficulty`（EASY / MEDIUM / HARD，rating 为空为 UNKNOWN）、写入 clist 的 `time`（UTC 转中国时区 UTC+8），并从 Codeforces 官方 API 同步 `tags` / `external_problem_tags`（AtCoder 没有主题标签来源）。`code`（如 `CF_2278G`）既是查重标识，也会由 `GET /catalog` 返回给前端（本地题为 `null`）。脚本用法见文件头注释。
- 表结构与字段说明见 [./02-database.md](./02-database.md)。

## 评测说明

- **架构**：主服务器默认 `mode=remote`，不执行用户代码；判题 Agent 通过 `/internal/judge/**` 领取任务并在 Isolate 中编译/运行。
- **流程**：提交入库为"排队中" → Agent claim 并写入租约 → 沙箱编译 → 逐测试点新建沙箱运行 → Agent 回报结果 → 主服务器条件落库。
- **租约**：`claim_token` + `lease_expires_at`；Agent 通过心跳续租，超时由 `JudgeLeaseReaper` 重新排队，超过最大尝试次数判 `SYSTEM_ERROR`。
- **资源限制**：`--time`（CPU）、`--wall-time`、`--cg-mem`、`--fsize`、`--processes`；JVM 需要更多线程与额外内存，已在语言配置中单独设置。
- **统计**：Isolate 返回 `time` 与 `cg-mem`，会写入逐点结果，因此 `memoryUsedKb` 有值，OOM 可判为 `MEMORY_LIMIT_EXCEEDED`。
- **输出比对**：默认忽略每行行尾空格/Tab 与文末多余空行，其余逐字符比较；题目配置了判题器（`problems.checker_code`）时改为在沙箱里编译并运行该 C++ 判题器，按其退出码判定 0=AC / 1=WA / 2=PE / 其他=`CHECKER_ERROR`。
- **判题器**：源码由出题者通过 `PUT /problems/{id}/checker` 上传，只对作者与管理员可见；判题器与选手程序跑在两个不同目录，选手目录里没有 `answer.txt`。**只有 `engine=isolate` 会真正执行判题器**，`local` 会判 `CHECKER_ERROR`。
- **失败即停**：某个测试点未通过后不再执行后续测试点，所以结果里的 `cases` 可能少于测试数据总数。
- **清理**：每个阶段结束都会 `isolate --cleanup`，工作目录在 `finally` 中删除；残留会在日志中告警。

沙箱前置条件、参数映射与常见错误见 [./03-judge.md](./03-judge.md) 与 [./06-isolate.md](./06-isolate.md)。

新增一门语言：在 `judge-protocol/src/main/java/me/iynotuo/judge/protocol/Language.java` 增加枚举值，在主服务器 `application.yaml` 的 `ioj.judge.languages` 下补配置（含 `probe-commands`、`isolate-processes` / `isolate-memory-extra-mb`），在每台判题机安装对应工具链。Agent 启动时会从主服务器拉取新语言并探测，无需改 Agent 代码。

## 接口

- 统一前缀 `/api/v1`，所有响应为 `{ code, message, data }`。
- 完整接口（参数含义、字段含义、登录/未登录差异）见 [../shared/api/README.md](../shared/api/README.md)。
- 常用自测：

```bash
curl -s localhost:8080/api/v1/meta/languages | jq
curl -s "localhost:8080/api/v1/catalog?page=1&size=5" | jq
```

## 开发约定

- 分层：`Controller`（参数绑定、权限注解）→ `Service`（事务、业务校验、组装返回模型）→ `Mapper`（SQL，XML 在 `resources/mapper`）。
- 对外模型与数据库实体分离：`dto` 包只放接口返回/接收的结构，实体中的敏感字段（如密码哈希）不会出现在响应里。
- 统一用构造器注入；异常统一抛业务异常并由全局异常处理器转成 `{ code, message, data }`。
- 新增接口 / 语言 / 评测引擎的步骤见 [./05-development.md](./05-development.md)。
- 改表结构时同步更新 `db/schema.sql` 与 [./02-database.md](./02-database.md)；`schema.sql` 需要同时能在 MySQL 8 与 H2（MySQL 模式）执行（测试依赖这一点）。
- 提交前跑 `./mvnw test`。

## 相关文档

| 文档 | 内容 |
| --- | --- |
| [./01-architecture.md](./01-architecture.md) | 架构、模块、请求链路、评测时序、权限矩阵、设计取舍 |
| [./02-database.md](./02-database.md) | ER 图、表结构、枚举、索引、容量估算、备份 |
| [../shared/api/README.md](../shared/api/README.md) | 接口文档（面向前端） |
| [./03-judge.md](./03-judge.md) | 评测状态机、引擎选择、Isolate 沙箱与限额、语言配置、生产加固 |
| [./04-deployment.md](./04-deployment.md) | 建库、配置、systemd/Nginx、备份、上线检查清单 |
| [./05-development.md](./05-development.md) | 开发规范、扩展方式、测试说明、踩坑记录 |
| [./06-isolate.md](./06-isolate.md) | Isolate 安装、权限模型、参数映射、手动验证、容量与排障 |
| [./07-judge-agent.md](./07-judge-agent.md) | 判题 Agent 构建、key、配置、systemd、多节点与排障 |

## 常见问题

| 现象 | 处理 |
| --- | --- |
| 启动报 `CannotCreateTransactionException` | 看启动日志里的"实际使用的 JDBC URL / 用户名"：说明账号密码没生效。检查 `IOJ_DB_*` 是否设置在**当前进程**上；bash 中要一行一个 `export`，不要用分号串成一行；IDEA 中在 Run Configuration 的 Environment variables 里配置 |
| 启动报端口占用 | 换端口：`java -jar ... --server.port=8081`，并同步调整前端与 Nginx |
| 提交一直"排队中" | 没有在线 Agent：检查 `GET /api/v1/admin/judge-nodes`、Agent 日志、`IOJ_JUDGE_KEY` 是否正确；确认 Agent 支持该语言 |
| 提交一直"评测中" | Agent 崩溃或失联；等 `IOJ_JUDGE_LEASE_SECONDS` 后由租约回收任务，检查 Agent 日志和节点心跳 |
| Agent 启动报 `ioj.agent.key 未配置` | 没有设置管理员创建的 `IOJ_JUDGE_KEY` |
| Agent claim 返回 `401` | key 错误、被禁用或已过期；重新创建/启用 key |
| Agent claim 返回 `409` | 同一 `nodeId` 已绑定其他 key；换一个 `IOJ_JUDGE_NODE_ID` 或删除旧节点 |
| 注册时提示 `2009` / 收不到验证码邮件 | 看日志里的 `验证码邮件发送失败`：多半是 `IOJ_MAIL_HOST` 没覆盖（默认 `smtp.localhost.invalid` 不可解析）、端口/加密方式不匹配（465 要 `IOJ_MAIL_SSL=true`，587 要 `IOJ_MAIL_STARTTLS=true`）、或 `IOJ_MAIL_FROM` 与 SMTP 账号不同域被拒收；也检查垃圾箱 |
| 注册提示 `2008` 验证码太频繁 | 同邮箱 60 秒冷却 / 每小时上限默认 5 次；调试时可用 `IOJ_EMAIL_CODE_RESEND_INTERVAL_SECONDS` / `IOJ_EMAIL_CODE_MAX_PER_HOUR` 放宽 |
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
| `schema.sql` 执行报"表已存在" | 该脚本不是幂等的；新库重建，已有库执行 `db/migrate-distributed-judge.sql` |
| 多个实例间登录互相 401 | 各实例的 `IOJ_JWT_SECRET` 必须一致 |
| 上传较大的测试数据失败 | 先看错误码：`1000` 是单条超限 → 用 `IOJ_PROBLEM_MAX_TESTCASE_BYTES` 调大（默认 4 MiB）；`413` 是请求体过大 → 调大 Nginx 的 `client_max_body_size`；`500` 且日志提示 packet → 调大 MySQL 的 `max_allowed_packet` |
| 测试环境与线上行为不一致 | 测试使用 H2（MySQL 模式），请避免使用 MySQL 专有语法（详见 `05-development.md`） |


