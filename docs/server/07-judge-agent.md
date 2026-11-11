# 判题 Agent 部署与运维

主服务器默认不再执行用户代码。每台判题机运行一个 `judge-agent` 进程，通过主服务器的内部 API 领取任务、在 Isolate 中判题并回报结果。

## 1. 拓扑

```text
[ Nginx ] -> [ 主服务器：Web + 调度 + MySQL ]
                    ^
                    | 内网 HTTP + API key
          +---------+---------+
          |                   |
   [ Judge Agent 1 ]   [ Judge Agent N ]
        Isolate             Isolate
```

- 主服务器：`ioj.judge.mode=remote`，`ioj.judge.engine=none`
- Agent：`ioj.judge.engine=isolate`
- Agent 只需要出站访问主服务器，不需要公网入站端口，也不连接 MySQL

## 2. 启动流程

Agent 启动时先向主服务器拉取配置，再决定是否进入 claim 循环：

```text
读取本地配置（server-url / key / node-id / isolate 路径）
        ↓
GET /internal/judge/config（key 认证）
        ↓
校验 protocolVersion / 配置完整性
        ↓
按 probeCommands 探测所有 requiredLanguages
        ↓
探测 Isolate 与 checker 编译器
        ↓
全部通过 → 节点心跳（上报已验证语言）
        ↓
开始 claim
```

- 如果主服务器支持的语言在本机探测失败，Agent **拒绝启动**并打印缺失语言和失败的探测命令。
- 不会先发心跳，避免主服务器在工具链校验完成前分配任务。
- 判题机是同构的：`requiredLanguages` 默认等于主服务器 `ioj.judge.languages` 的全部 key。

## 3. 构建

在仓库根目录：

```bash
./mvnw -DskipTests clean package
ls judge-agent/target/judge-agent-0.0.1-SNAPSHOT.jar
```

## 4. 创建 key

管理员登录后调用：

```bash
curl -s -X POST https://oj.example.com/api/v1/admin/judge-keys \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"judge-1"}'
```

响应中的 `data.key` 只出现一次。把它保存到判题机的 `IOJ_JUDGE_KEY`，不要写进 Git。

一个 Agent 一个 key；更换节点或怀疑泄露时，创建新 key、更新 Agent、禁用旧 key。

## 5. 配置

Agent 的配置在 `judge-agent/src/main/resources/application.yaml`，生产环境建议用环境变量覆盖：

```yaml
spring:
  main:
    web-application-type: none

ioj:
  agent:
    server-url: ${IOJ_JUDGE_SERVER_URL:http://127.0.0.1:8080}
    key: ${IOJ_JUDGE_KEY:}
    node-id: ${IOJ_JUDGE_NODE_ID:judge-1}
    concurrency: ${IOJ_JUDGE_CONCURRENCY:1}
    poll-interval-ms: 1000
    max-poll-interval-ms: 5000
    max-claim-batch: 1
    lease-seconds: 300
    heartbeat-interval-ms: 100000
    request-timeout-seconds: 30
    config-fetch-retries: 5
    config-fetch-retry-delay-ms: 2000
    probe-timeout-ms: 5000

  judge:
    mode: agent
    engine: isolate
    isolate:
      binary: /usr/bin/isolate
      box-id-start: 1
      box-id-count: 1
      check-on-startup: true
      compile-processes: 64
      compile-time-seconds: 10
      compile-wall-time-seconds: 30
      compile-cg-mem-kb: 524288
      compile-fsize-kb: 16384
      wall-time-extra-seconds: 3
      setup-timeout-ms: 10000
      extra-dirs:
        - /etc/java-21-openjdk
      resolve-executables: true
    checker:
      enabled: true
      time-limit-ms: 2000
      memory-limit-mb: 512
      compile-timeout-ms: 30000
      compile-memory-mb: 512
      source-file: checker.cpp
      compile-command: g++ -O2 -std=c++17 -pipe -o checker checker.cpp
      message-limit-bytes: 1024
```

### 语言命令由谁配置

- **源码文件名、编译命令、运行命令、时间系数、进程数、额外内存**：主服务器 `ioj.judge.languages`，随 claim 响应的 `languageConfig` 下发。
- **Isolate 路径、box id、`extra-dirs`、编译限制、checker 编译命令**：Agent 本地配置。
- Agent 不再维护本地语言列表：启动时从主服务器拉取 `requiredLanguages` 和 `probeCommands`，全部探测通过才启动。
- 主服务器为每种语言配置探测命令，例如 Java `javac -version` / `java -version`、C++ `g++ --version`、Python `python3 --version`；checker 单独配置 `g++ --version`。

Agent 启动失败时不会先发心跳或 claim；缺少工具链会打印具体语言和失败命令，并以退出码 `78` 结束。

这样所有 Agent 使用同一份语言策略，不会出现“同代码不同机器结果不同”。

## 6. systemd

```ini
# /etc/systemd/system/ioj-judge-agent.service
[Unit]
Description=iOJ judge agent
After=network.target isolate.service
Requires=isolate.service

[Service]
Type=simple
User=judge
WorkingDirectory=/opt/ioj-agent
Environment=IOJ_JUDGE_SERVER_URL=http://10.0.0.10:8080
Environment=IOJ_JUDGE_KEY=jsk_...
Environment=IOJ_JUDGE_NODE_ID=judge-1
Environment=IOJ_JUDGE_CONCURRENCY=2
Environment=IOJ_ISOLATE_BOX_ID_START=1
Environment=IOJ_ISOLATE_BOX_ID_COUNT=2
ExecStart=/usr/bin/java -Xms128m -Xmx512m -jar /opt/ioj-agent/judge-agent.jar
Restart=on-failure
RestartPreventExitStatus=78
StartLimitIntervalSec=300
StartLimitBurst=5
RestartSec=5
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now ioj-judge-agent
sudo journalctl -u ioj-judge-agent -f
```

Agent 启动失败时的退出码：

| 退出码 | 含义 |
| --- | --- |
| `78` | 配置/工具链/协议不兼容；systemd 不自动重启（`RestartPreventExitStatus=78`） |
| `75` | 主服务器暂时不可达；可由 systemd 重试 |

如果缺少语言工具链，日志会列出缺失的语言和失败的探测命令，安装后重启 Agent 即可。

## 7. 多 Agent

同一台机器可以跑多个 Agent，但必须：

- 每个 Agent 一个唯一 `IOJ_JUDGE_NODE_ID`
- 每个 Agent 一个独立 key
- `box-id-start` / `box-id-count` 不重叠，例如 A `1..2`、B `3..4`
- 每个 Agent 的 `concurrency` 不超过自己的 box 数量

不同机器之间不需要分段。

## 8. 任务协议

| 接口 | 说明 |
| --- | --- |
| `GET /internal/judge/config` | 启动时拉取语言、探测命令、checker 和协议版本 |
| `POST /internal/judge/nodes/heartbeat` | 节点注册/心跳，上报语言、并发、版本 |
| `POST /internal/judge/tasks/claim` | 领取任务；返回 `claimToken`、测试数据、语言配置 |
| `GET /internal/judge/tasks/{id}/testcases?claimToken=...` | 大数据任务下载完整测试数据 |
| `POST /internal/judge/tasks/{id}/heartbeat` | 续租；`cancel=true` 表示任务已被重领/重测 |
| `POST /internal/judge/tasks/{id}/result` | 回报终态结果 |
| `POST /internal/judge/tasks/{id}/release` | 优雅停机时释放未开始任务 |

- 结果写入会校验 `claimToken` 和 `JUDGING` 状态
- 同一 token 重复回报幂等
- 旧 token 的迟到结果返回 `409`
- 租约过期后主服务器重新排队任务；超过 `max-attempts` 判 `SYSTEM_ERROR`

## 9. 排障

| 现象 | 处理 |
| --- | --- |
| `ioj.agent.key 未配置` | 设置 `IOJ_JUDGE_KEY` |
| claim 返回 `401` | key 错误/禁用/过期；重新创建或启用 |
| claim 返回 `409` | `nodeId` 已绑定其他 key；换 nodeId 或删除旧节点 |
| 启动失败 `判题机能力校验失败` | 本机缺少主服务器要求的语言工具链；按日志中的探测命令安装后重启 |
| 启动失败 `主服务器协议版本不匹配` | 主服务器与 Agent 版本不兼容；先升级 Agent 或回滚主服务器 |
| 启动失败 `无法从主服务器获取判题配置` | 检查 `IOJ_JUDGE_SERVER_URL`、网络和主服务器状态；Agent 会按 `config-fetch-retries` 重试 |
| 一直领取不到任务 | 检查 `GET /api/v1/admin/judge-nodes`、节点是否 ONLINE、主服务器是否有 PENDING |
| 任务一直 JUDGING | Agent 崩溃/失联；等租约过期回收，检查 Agent 日志 |
| Python 运行失败 | 确认主服务器 Python 配置同时有 `compile-command: python3 -m py_compile main.py` 和 `copy-source-to-run: true`，见 03-judge.md §4 |
| `isolate binary is not executable` | 给运行用户执行权限，确认 `isolate --version` |
| `This box belongs to a different user` | 换 `box-id-start` 或清理旧 box |
| JVM `Error loading java.security file` | `extra-dirs` 加 `/etc/java-21-openjdk` |
| 节点显示 OFFLINE | 心跳超时；检查网络、进程、`IOJ_JUDGE_HEARTBEAT_INTERVAL_MS` |

## 10. 安全

- Agent 不需要 root；Isolate 自身 setuid
- Agent 不连接 MySQL，不持有 JWT/SMTP 凭据
- key 放在 `EnvironmentFile`，权限 0600
- 公网 Nginx 不要代理 `/internal/judge/**`
- 判题机与主服务器之间使用内网/VPN；有条件的上 mTLS
- 定期更新 Isolate 与系统，判题机不要存放数据库密码或证书
