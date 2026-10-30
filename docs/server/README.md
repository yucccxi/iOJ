# iOJ Server（后端）

iOJ 的后端服务：一个 Spring Boot 单体应用，同时提供 **REST API** 和 **异步评测**。
接口契约见 [../shared/api.md](../shared/api.md)；项目整体介绍见 [../../README.md](../../README.md)。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 21 |
| 框架 | Spring Boot 4.0.9（Spring Framework 7、Spring Security 7、Jackson 3） |
| 持久层 | MyBatis 4（XML mapper）+ MySQL 8 |
| 鉴权 | 无状态令牌（jjwt 0.12.6）+ BCrypt 密码哈希 |
| 参数校验 | Jakarta Validation |
| 测试 | JUnit 5 + AssertJ + H2（MySQL 兼容模式），含真实编译运行的端到端测试 |
| 评测 | Isolate 沙箱（默认引擎）；本机进程作为无沙箱环境的回退 |

## 代码结构

```
server/
├── pom.xml
├── mvnw / mvnw.cmd
└── src/
    ├── main/java/me/iynotuo/judge/
    │   ├── common/        统一响应、分页、错误码、全局异常、枚举
    │   ├── config/        安全配置、配置项、评测线程池、启动自检
    │   ├── security/      令牌签发与校验、登录身份
    │   ├── user/          注册登录、个人资料、管理员用户管理
    │   ├── problem/       题目、样例、测试数据、标签
    │   ├── submission/    提交记录
    │   └── judge/         评测调度（JudgeService/JudgeWorker）、评测模型、输出比对
    │       └── engine/    JudgeEngine 接口与实现（isolate / local）、isolate meta 解析
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

- JDK 21（评测 Java 代码也依赖 `javac`）
- MySQL 8
- 评测机工具链：`g++`（C++17）、`gcc`（C11）、`python3`——按需要安装
- Isolate 沙箱（默认引擎）：`isolate.service` 运行中，运行后端的用户能执行 `/usr/bin/isolate`（通常在 `isolate` 组）
- 不需要单独安装 Maven，仓库自带 `./mvnw`

安装/授权示例（Debian 13，详见 [06-isolate.md](./06-isolate.md)）：

```bash
sudo apt install isolate
sudo systemctl enable --now isolate.service
sudo usermod -aG isolate "$USER"     # 重新登录后生效
isolate --check-config               # 无输出且退出码为 0 即可
```

> 如果暂时没有 Isolate，可设置 `IOJ_JUDGE_ENGINE=local` 使用本机进程回退（仅适合可信用户）。

### 2. 建库并初始化

```bash
mysql -uroot -p <<'SQL'
CREATE DATABASE ioj DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'ioj'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON ioj.* TO 'ioj'@'localhost';
FLUSH PRIVILEGES;
SQL

# 在 server/ 目录下执行
mysql -uioj -pchange-me ioj < src/main/resources/db/schema.sql
mysql -uioj -pchange-me ioj < src/main/resources/db/seed.sql
```

`seed.sql` 会创建默认管理员 **admin / admin123456** 和 10 个常用标签，**首次登录后请立即修改密码**。

### 3. 配置环境变量

| 变量 | 是否必填 | 说明 |
| --- | --- | --- |
| `IOJ_DB_HOST` / `IOJ_DB_PORT` / `IOJ_DB_NAME` | 否 | 默认 `127.0.0.1` / `3306` / `ioj` |
| `IOJ_DB_USER` / `IOJ_DB_PASSWORD` | **是** | 数据库账号密码（默认值仅用于本地示例，生产必须覆盖） |
| `IOJ_JWT_SECRET` | **是** | 登录令牌签名密钥，≥32 字节；用 `openssl rand -base64 48` 生成 |
| `IOJ_CORS_ORIGINS` | 否 | 允许的前端来源，默认 `http://localhost:5173`，多个用逗号分隔 |
| `IOJ_PROBLEM_AUTO_PUBLISH` | 否 | 普通用户上传题目是否直接发布，默认 `true` |
| `IOJ_JUDGE_ENABLED` | 否 | 是否启用评测，默认 `true`；设为 `false` 后提交会一直排队 |
| `IOJ_JUDGE_CONCURRENCY` | 否 | 同时评测数，默认 `1`，建议 CPU 核数 - 1 |

启动时服务会先做一次数据库连通性检查，成功会打印：

```
数据库连接正常: MySQL 8.4.11，当前库=ioj，账号=ioj@localhost
```

失败则打印**实际生效的 JDBC URL 与用户名**（不打印密码）并终止启动，便于排查环境变量没生效的问题。

### 4. 启动

```bash
# 开发模式
IOJ_DB_USER=ioj IOJ_DB_PASSWORD='change-me' \
IOJ_JWT_SECRET="$(openssl rand -base64 48)" \
./mvnw spring-boot:run

# 或先打包再运行
./mvnw -DskipTests clean package
java -jar target/server-0.0.1-SNAPSHOT.jar
```

默认端口 `8080`。验证：

```bash
curl -s http://localhost:8080/api/v1/meta/languages
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"account":"admin","password":"admin123456"}'
```

### 5. 运行测试

```bash
./mvnw test                                # 全部测试，使用 H2 内存库，不需要 MySQL
./mvnw test -Dtest=ApiFlowIntegrationTest  # 只跑端到端流程（会真实调用 javac 编译并评测）
```

测试覆盖：

| 测试 | 内容 |
| --- | --- |
| `ServerApplicationTests` | Spring 上下文可启动、全部 Mapper 可加载 |
| `JwtTokenProviderTest` | 令牌签发/解析、篡改、过期、弱密钥拒绝 |
| `OutputComparatorTest` | 输出比对规则（行尾空白、换行、空输出等） |
| `IsolateMetaTest` | Isolate meta 解析（时间/内存/退出码/信号/OOM/缺省值） |
| `ApiFlowIntegrationTest` | 注册→登录→建题→传测试数据→提交→评测→AC/WA→字段裁剪 |
| `AdminModerationIntegrationTest` | 测试数据增删改查、题目上下架、用户搜索与封禁 |

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
| `ioj.problem.max-testcases` | — | `100` | 单题测试数据条数上限 |
| `ioj.problem.max-testcase-bytes` | — | `1048576` | 单条输入/输出内容上限（字节） |
| `ioj.submission.max-code-length` | — | `65536` | 单次提交代码字符数上限 |
| `ioj.judge.enabled` | `IOJ_JUDGE_ENABLED` | `true` | 是否启用评测；关闭后提交停留在排队状态 |
| `ioj.judge.engine` | `IOJ_JUDGE_ENGINE` | `isolate` | 评测引擎：`isolate`（默认）/ `local`（本机进程回退）/ `docker`（未实现） |
| `ioj.judge.isolate.*` | — | 见下 | Isolate 路径、box 编号、编译限制、额外目录等 |
| `ioj.judge.concurrency` | `IOJ_JUDGE_CONCURRENCY` | `1` | 同时评测的提交数 |
| `ioj.judge.poll-interval-ms` | — | `1000` | 拉取待评测提交的间隔 |
| `ioj.judge.max-output-bytes` | — | `16777216` | 单个测试点允许的最大输出 |
| `ioj.judge.max-compile-output-bytes` | — | `65536` | 编译输出保留上限 |
| `ioj.judge.compile-timeout-ms` | — | `30000` | 编译超时（本机进程引擎使用） |
| `ioj.judge.languages.*` | — | JAVA / CPP / C / PYTHON3 | 各语言源码、编译/运行命令、时间系数、`isolate-processes`、`isolate-memory-extra-mb` |

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

- `src/main/resources/db/schema.sql`：8 张表（`users`、`problems`、`problem_samples`、`problem_testcases`、`tags`、`problem_tags`、`submissions`、`submission_cases`）及外键、索引。
- `src/main/resources/db/seed.sql`：默认管理员与常用标签，可重复执行。

注意：

- `schema.sql` **不是幂等**的，重复执行会报"表已存在"。需要重建时：
  ```bash
  mysql -uroot -p -e "DROP DATABASE ioj;
                      CREATE DATABASE ioj DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
  mysql -uioj -p ioj < src/main/resources/db/schema.sql
  mysql -uioj -p ioj < src/main/resources/db/seed.sql
  ```
- 表结构与字段说明见 [./02-database.md](./02-database.md)。

## 评测说明

- **引擎**：默认 `isolate`，编译与运行都在 Isolate 沙箱内；没有 Isolate 时可回退到 `local`（本机进程 + `ulimit`）。
- **流程**：提交入库为"排队中" → worker 取任务并标记"评测中" → 沙箱编译 → 逐测试点新建沙箱运行 → 写回逐点结果与最终状态。
- **资源限制**：`--time`（CPU）、`--wall-time`、`--cg-mem`、`--fsize`、`--processes`；JVM 需要更多线程与额外内存，已在语言配置中单独设置。
- **统计**：Isolate 返回 `time` 与 `cg-mem`，会写入逐点结果，因此 `memoryUsedKb` 有值，OOM 可判为 `MEMORY_LIMIT_EXCEEDED`。
- **输出比对**：忽略每行行尾空格/Tab 与文末多余空行，其余逐字符比较。
- **失败即停**：某个测试点未通过后不再执行后续测试点，所以结果里的 `cases` 可能少于测试数据总数。
- **清理**：每个阶段结束都会 `isolate --cleanup`，工作目录在 `finally` 中删除；残留会在日志中告警。

沙箱前置条件、参数映射与常见错误见 [./03-judge.md](./03-judge.md) 与 [./06-isolate.md](./06-isolate.md)。

新增一门语言：在 `common/enums/Language.java` 增加枚举值，在 `application.yaml` 的 `ioj.judge.languages` 下补配置（含 `isolate-processes` / `isolate-memory-extra-mb`），评测机安装对应工具链即可，无需改评测代码。

## 接口

- 统一前缀 `/api/v1`，所有响应为 `{ code, message, data }`。
- 完整接口（参数含义、字段含义、登录/未登录差异）见 [../shared/api.md](../shared/api.md)。
- 常用自测：

```bash
curl -s localhost:8080/api/v1/meta/languages | jq
curl -s "localhost:8080/api/v1/problems?page=1&size=5" | jq
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
| [../shared/api.md](../shared/api.md) | 接口文档（面向前端） |
| [./03-judge.md](./03-judge.md) | 评测状态机、引擎选择、Isolate 沙箱与限额、语言配置、生产加固 |
| [./04-deployment.md](./04-deployment.md) | 建库、配置、systemd/Nginx、备份、上线检查清单 |
| [./05-development.md](./05-development.md) | 开发规范、扩展方式、测试说明、踩坑记录 |
| [./06-isolate.md](./06-isolate.md) | Isolate 安装、cgroup 服务、手动验证命令、接入说明 |

## 常见问题

| 现象 | 处理 |
| --- | --- |
| 启动报 `CannotCreateTransactionException` | 看启动日志里的"实际使用的 JDBC URL / 用户名"：说明账号密码没生效。检查 `IOJ_DB_*` 是否设置在**当前进程**上；bash 中要一行一个 `export`，不要用分号串成一行；IDEA 中在 Run Configuration 的 Environment variables 里配置 |
| 启动报端口占用 | 换端口：`java -jar ... --server.port=8081`，并同步调整前端与 Nginx |
| 提交一直"排队中" | 检查 `IOJ_JUDGE_ENABLED` 是否为 `true`；查看日志是否有评测异常；确认评测机装有对应编译器 |
| 启动报 `isolate binary is not executable` | 运行用户没有执行权限：`sudo usermod -aG isolate <user>` 后重新登录，确认 `isolate --version` 可用 |
| `isolate --init` 报 `This box belongs to a different user` | box 编号被其他用户（常见 root）占用：换 `box-id-start`，或让原用户 `isolate --box-id=N --cleanup` |
| 编译报 `javac: No such file or directory` | 沙箱内无法跟随 `/etc/alternatives`：保持 `resolve-executables: true`，或改用绝对路径 |
| JVM 报 `Error loading java.security file` | `extra-dirs` 缺少 `/etc/java-21-openjdk`（Debian） |
| JVM 报 `pthread_create failed (EAGAIN)` | `isolate-processes` 太小：Java 建议 64 |
| 评测报编译错误但代码没问题 | 评测机缺少工具链：`apt install -y g++ gcc python3`；Java 代码需使用 `public class Main` |
| `schema.sql` 执行报"表已存在" | 该脚本不是幂等的，按上文"数据库"小节重建库，或手动删除旧表 |
| 多个实例间登录互相 401 | 各实例的 `IOJ_JWT_SECRET` 必须一致 |
| 上传较大的测试数据失败 | 调大 Nginx 的 `client_max_body_size`，以及 `ioj.problem.max-testcase-bytes` |
| 测试环境与线上行为不一致 | 测试使用 H2（MySQL 模式），请避免使用 MySQL 专有语法（详见 `05-development.md`） |


