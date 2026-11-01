# 开发指南

## 1. 代码结构

```
me.iynotuo.judge
├── ServerApplication.java        @SpringBootApplication + @ConfigurationPropertiesScan + @EnableScheduling
├── common
│   ├── api        ApiResponse / PageQuery / PageResult / JsonResponses
│   ├── enums      UserRole / UserStatus / Difficulty / ProblemStatus / SubmissionStatus / Language
│   └── exception  ErrorCode / BusinessException / GlobalExceptionHandler
├── config         SecurityConfig / JudgeConfig / *Properties
├── security       JwtTokenProvider / JwtAuthenticationFilter / LoginUser / Rest*Handler
├── user           entity(User) / mapper / service / controller / dto
├── problem        entity(Problem, Sample, Testcase, Tag) / mapper / service / controller / dto
├── submission     entity(Submission, SubmissionCase) / mapper / service / controller / dto
└── judge           JudgeService / JudgeWorker / JudgeContext / JudgeOutcome / OutputComparator
    ├── sandbox     Sandbox（接口）/ IsolateSandbox / LocalSandbox / SandboxCommand / SandboxResult
    │               SandboxFiles / Limits / Commands / IsolateMeta / StreamCollector
    ├── answer      AnswerChecker（接口）/ TextAnswerChecker / ProgramAnswerChecker
    │               DisabledAnswerChecker / AnswerCheckerFactory
    └── engine      JudgeEngine（接口）/ IsolateJudgeEngine / LocalProcessJudgeEngine
```

分层职责：

| 层 | 做什么 | 不做什么 |
| --- | --- | --- |
| Controller | 参数绑定、`@Valid`、`@PreAuthorize`、把 `LoginUser` 传给 Service、包装 `ApiResponse` | 不写业务分支、不碰 Mapper |
| Service | 事务边界、权限与领域校验、组装 VO | 不处理 HTTP 细节 |
| Mapper | SQL（XML），入参是实体或 `Criteria`/`@Param` | 不含业务判断 |
| judge | 编排判题（编译 → 逐点运行 → 判定 → 聚合），返回 `JudgeOutcome` | 不直接写数据库（由 `JudgeService` 落库） |
| judge/sandbox | 起进程、加限额、读回输出（isolate / 本机进程各一个实现） | 不知道"答案""判定""提交" |
| judge/answer | 判定一个测试点是否正确（文本比对 / 作者判题器） | 不知道用的是哪个引擎 |

约定：

- **构造器注入**（不用 `@Autowired` 字段注入），便于单元测试。
- 对外模型用 `record`；MyBatis 结果行用 `@Data` POJO（`*Row`、`TestcaseSummaryVO`），因为 MyBatis 对 record 构造器映射需要额外配置。
- 数据库实体不直接序列化给前端；`UserVO` 里永远没有 `passwordHash`，`TestcaseDetailVO` 只给作者。
- 异常统一抛 `BusinessException(ErrorCode.XXX)`，不要返回 `null` 让上层猜。

## 2. 新增一个接口（示例：题目统计）

需求：`GET /api/v1/problems/{id}/stats` 返回提交数、通过数、通过率、不同通过人数。

1. **DTO**（`problem/dto/ProblemStatsVO.java`）

```java
package me.iynotuo.judge.problem.dto;

public record ProblemStatsVO(long submitCount, long acceptedCount, double acceptRate, long solvedUsers) {
}
```

2. **Mapper**（`ProblemMapper` + XML）

```java
ProblemStatsRow findStats(@Param("problemId") Long problemId);
```

```xml
<select id="findStats" resultType="me.iynotuo.judge.problem.mapper.ProblemStatsRow">
    SELECT p.submit_count, p.accepted_count,
           (SELECT COUNT(DISTINCT user_id) FROM submissions s
             WHERE s.problem_id = p.id AND s.status = 'ACCEPTED') AS solved_users
    FROM problems p WHERE p.id = #{problemId}
</select>
```

3. **Service**（`ProblemService#getStats`）：查不到题目抛 `PROBLEM_NOT_FOUND`，计算 `acceptRate` 时避免除零。

4. **Controller**：加到 `ProblemController`：

```java
@GetMapping("/{id}/stats")
public ApiResponse<ProblemStatsVO> stats(@PathVariable Long id) {
    return ApiResponse.ok(problemService.getStats(id));
}
```

5. **Security**：`/api/v1/problems/*` 的 GET 已放行；如果新接口需要登录，在 `SecurityConfig` 里加规则，注意**放在通配规则之前**。

6. **测试**：在 `ApiFlowIntegrationTest` 里加断言，或新建 `*Test` 类（见第 6 节）。

## 3. 新增一门语言

1. `common/enums/Language.java` 加枚举值（如 `GO`）。数据库存的就是枚举名。
2. `application.yaml`：

```yaml
ioj:
  judge:
    languages:
      GO:
        display-name: Go 1.22
        source-file: main.go
        compile-command: go build -o main main.go
        run-command: ./main
        run-wrapper: ulimit -v {memoryKb}; exec {command}
        time-multiplier: 1.5
```

3. 判题机安装 `go`，重启服务。`GET /meta/languages` 会自动出现新语言，前端无需改代码。

## 4. 新增/替换判题引擎

见 `03-judge.md` 的「判题引擎选择」与 §2.1「代码分层」。要点：

- 优先只写一个 `Sandbox` 实现（`me.iynotuo.judge.judge.sandbox` 包）：把"起进程、加限额、读回输出"做完，然后复用现成的引擎流程。绝大多数新沙箱（Docker、nsjail）只需要替换这一层。
- 真要替换整条流程时，实现 `JudgeEngine`（放在 `me.iynotuo.judge.judge.engine` 包，与现有实现同级），用 `@ConditionalOnProperty(prefix = "ioj.judge", name = "engine", havingValue = "docker")` 限定生效条件，避免两个实现同时注册。
- 返回的 `JudgeOutcome` 语义要与现有引擎（`isolate` / `local`）一致：`status` 取第一个失败测试点的状态，`cases` 按执行顺序，失败即停。
- **不要自己判断"答案对不对"**：把选手输出交给 `AnswerChecker`（由 `AnswerCheckerFactory` 创建），这样文本比对与作者判题器对新引擎自动生效。
- `MEMORY_LIMIT_EXCEEDED` 通过沙箱的 `SandboxResult.outOfMemory()` 判定，同时写入 `memoryUsedKb`（`IsolateSandbox` 已提供参考实现）。
- `Sandbox.supportsAuthorCode()` 返回 `false` 的沙箱不会执行作者判题器，`AnswerCheckerFactory` 会自动给它 `DisabledAnswerChecker`（判 `CHECKER_ERROR`）。
- 在 `application.yaml` 中把 `ioj.judge.engine` 设为新实现对应的值，并确认启动日志/失败信息符合预期。

## 5. 数据库脚本规则

- `schema.sql` 必须**同时能在 MySQL 8 和 H2(MySQL 模式) 执行**：
  - 不用 `ENGINE/CHARSET/COMMENT`，索引用独立 `CREATE INDEX`；
  - 不用 `GROUP_CONCAT`、`JSON_*`、窗口函数等 MySQL 专有/较新的函数（SQL 里用得少，尽量用标准 SQL）；
  - 唯一约束用 `CONSTRAINT xxx UNIQUE (...)`。
- 改表时同步更新 `schema.sql` 与 `02-database.md`；当前没有迁移工具，生产环境手工执行 `ALTER TABLE`。
- 判题抢占用了 `FOR UPDATE`（H2 支持）。**不要**在 mapper 里写 `SKIP LOCKED`，否则集成测试会失败。

## 6. 测试

```bash
cd server
./mvnw test                                  # 全部测试（H2 内存库，无需 MySQL）
./mvnw test -Dtest=ApiFlowIntegrationTest    # 只跑端到端流程
```

现有测试：

| 测试 | 覆盖内容 |
| --- | --- |
| `ServerApplicationTests` | Spring 上下文可启动（含所有 Mapper XML 解析） |
| `JwtTokenProviderTest` | 签发/解析、篡改、换密钥、过期、弱密钥拒绝 |
| `OutputComparatorTest` | 行尾空白、CRLF、文末空行、空输出、行首空格 |
| `ApiFlowIntegrationTest` | 注册→登录→建题→传测试点→匿名列表→提交 Java 代码→worker 抢占→真实 javac 编译+运行→AC/WA→权限裁剪 |
| `AdminModerationIntegrationTest` | 测试点单个/批量增删查、题目审核上下架、后台用户搜索、封禁后旧 token 立即失效 |
| `CheckerIntegrationTest` | 判题器源码的读/写/清空、匿名与越权拒绝、题目详情只暴露 `hasChecker`、local 引擎返回 `CHECKER_ERROR` |
| `ProgramAnswerCheckerTest` | 判题器退出码 0/1/2/其他、超时、崩溃、内存超限、编译失败、缺少二进制（假 Sandbox，无需 isolate） |
| `IsolateSandboxCommandTest` | isolate 命令行（顺序与两位小数格式）与宿主机到 `/box` 的路径翻译 |

集成测试的关键配置（`src/test/resources/application-test.yaml`）：

- 每个 Spring 上下文一个独立 H2 内存库（`jdbc:h2:mem:ioj-${random.uuid};MODE=MySQL`），启动时执行 `db/schema.sql`；
- `ioj.judge.enabled=false`：关掉定时任务，测试里显式调用 `judgeService.judge(id)`，结果确定且快；
- 语言配置直接复用主配置，所以测试会真实调用本机 `javac`。CI 需要 JDK（构建本来就需要）；
- 两个集成测试类各自用 `@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:...")` 覆盖数据源，从而拥有独立的 H2 内存库，断言总数时不会互相干扰（同时也让 Spring 缓存两个独立的上下文）。

写新的集成测试时：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyFeatureTest {
    @LocalServerPort int port;
    // 用 JDK HttpClient + JsonMapper 发请求，避免依赖测试专用 HTTP 客户端 API
}
```

## 7. 框架注意事项

| 主题 | 说明 |
| --- | --- |
| Spring Boot 4 测试注解 | `@WebMvcTest`、`@AutoConfigureMockMvc` 已移到 `org.springframework.boot.webmvc.test.autoconfigure`；`TestRestTemplate` 在 `org.springframework.boot.resttestclient`。本项目集成测试直接用 JDK `HttpClient`，不受影响 |
| Jackson 3 | Boot 4 默认使用 Jackson 3（包名 `tools.jackson.*`）。DTO 用 record 即可；`JsonNode.asString()` 替代了 `asText()` |
| jjwt | `jjwt-jackson` 依赖 Jackson 2（`com.fasterxml.*`），与 Jackson 3 共存无冲突，只用于 JWT 内部序列化 |
| Spring Security 7 | 无 `WebSecurityConfigurerAdapter`；用 `SecurityFilterChain` Bean。没有配 `UserDetailsService`，因此在 `ServerApplication` 里排除了 `UserDetailsServiceAutoConfiguration`，避免启动时打印随机密码 |
| 枚举写入数据库 | MyBatis 默认 `EnumTypeHandler` 存 `name()`，读取按名字映射，所以枚举改名等于数据迁移，**不要随意重命名** |
| LocalDateTime | MyBatis 自动映射 `DATETIME`；Jackson 3 序列化为 ISO-8601，前端按字符串处理即可 |
| 自增主键 | `<insert useGeneratedKeys="true" keyProperty="id">`，插入后实体 id 会被回填；但 **`insertBatch`（`<foreach>`）不会回填**，批量插入后需要按唯一键再查一次（参考 `TestcaseService#add`） |
| H2 差异 | `OCTET_LENGTH`、`CONCAT`、`LIMIT ? OFFSET ?`、`FOR UPDATE` 均可用；`ON UPDATE CURRENT_TIMESTAMP`、`GROUP_CONCAT`、`SKIP LOCKED` 不要用 |
| 事务 + 长任务 | 判题在事务外执行，`persist` 才开事务；不要在 `claimNext()` 事务里调用引擎。`judge()` 内部调用 `persist()` 属于自调用，`@Transactional` 不生效，所以那里改用 `TransactionTemplate` 显式控制事务 |

## 8. 代码风格与协作

- 4 空格缩进；类/方法命名用英文，注释与用户可见文案用中文。
- 一个文件一个公开类；Controller 按资源聚合，不要一个接口一个类。
- 新增错误码加到 `ErrorCode`（业务号段：用户 2xxx、题目 3xxx、提交 4xxx）。
- 提交前跑 `./mvnw test`；改 SQL 时同时跑集成测试（它会真的执行所有 mapper）。
- Git 提交信息建议：`feat(problem): 支持按标签筛选`、`fix(judge): 修复 Java 超时判定`。
- 前端字段变更属于破坏性变更，需同步更新 `../shared/api.md`。

## 9. 已知简化与后续方向

| 简化 | 影响 | 改进 |
| --- | --- | --- |
| 无 refresh token | 7 天后需重新登录 | 增加 refresh token + 轮换 |
| 无提交频率限制 | 恶意用户可以刷提交 | 每用户每分钟限 N 次（Redis 或 DB 计数） |
| 无比赛/榜单 | 只能练题 | 新增 contest 相关表与聚合查询 |
| 判题默认使用 Isolate 沙箱 | 需要安装与授权（不在 `isolate` 组则无法执行） | 按 06-isolate.md 配置；开发/CI 可用 `engine=local` 回退 |
| 计数冗余字段 | 极端并发下可能略有偏差 | 定时对账任务修正 `submit_count` / `accepted_count` |
| 无审计日志 | 管理员操作不可追溯 | 增加 `admin_audit_logs` |

