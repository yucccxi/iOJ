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

## 2. 构建

在仓库根目录：

```bash
./mvnw -DskipTests clean package
ls judge-agent/target/judge-agent-0.0.1-SNAPSHOT.jar
```

## 3. 创建 key

管理员登录后调用：

```bash
curl -s -X POST https://oj.example.com/api/v1/admin/judge-keys \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"judge-1"}'
```

响应中的 `data.key` 只出现一次。把它保存到判题机的 `IOJ_JUDGE_KEY`，不要写进 Git。

一个 Agent 一个 key；更换节点或怀疑泄露时，创建新 key、更新 Agent、禁用旧 key。

## 4. 配置

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
    languages:
      - JAVA
      - CPP
      - C
      - PYTHON3
    poll-interval-ms: 1000
    max-poll-interval-ms: 5000
    max-claim-batch: 1
    lease-seconds: 300
    heartbeat-interval-ms: 100000
    request-timeout-seconds: 30

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
- Agent 的 `ioj.agent.languages` 是本机实际装好的语言；主服务器只分配匹配的题。

这样所有 Agent 使用同一份语言策略，不会出现“同代码不同机器结果不同”。

## 5. systemd

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
Restart=always
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

## 6. 多 Agent

同一台机器可以跑多个 Agent，但必须：

- 每个 Agent 一个唯一 `IOJ_JUDGE_NODE_ID`
- 每个 Agent 一个独立 key
- `box-id-start` / `box-id-count` 不重叠，例如 A `1..2`、B `3..4`
- 每个 Agent 的 `concurrency` 不超过自己的 box 数量

不同机器之间不需要分段。

## 7. 任务协议

| 接口 | 说明 |
| --- | --- |
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

## 8. 排障

| 现象 | 处理 |
| --- | --- |
| `ioj.agent.key 未配置` | 设置 `IOJ_JUDGE_KEY` |
| claim 返回 `401` | key 错误/禁用/过期；重新创建或启用 |
| claim 返回 `409` | `nodeId` 已绑定其他 key；换 nodeId 或删除旧节点 |
| 一直领取不到任务 | 检查 `GET /api/v1/admin/judge-nodes`、Agent `languages`、主服务器是否有 PENDING |
| 任务一直 JUDGING | Agent 崩溃/失联；等租约过期回收，检查 Agent 日志 |
| Python 运行失败 | 确认主服务器 Python 配置同时有 `compile-command: python3 -m py_compile main.py` 和 `copy-source-to-run: true`，见 03-judge.md §4 |
| `isolate binary is not executable` | 给运行用户执行权限，确认 `isolate --version` |
| `This box belongs to a different user` | 换 `box-id-start` 或清理旧 box |
| JVM `Error loading java.security file` | `extra-dirs` 加 `/etc/java-21-openjdk` |
| 节点显示 OFFLINE | 心跳超时；检查网络、进程、`IOJ_JUDGE_HEARTBEAT_INTERVAL_MS` |

## 9. 安全

- Agent 不需要 root；Isolate 自身 setuid
- Agent 不连接 MySQL，不持有 JWT/SMTP 凭据
- key 放在 `EnvironmentFile`，权限 0600
- 公网 Nginx 不要代理 `/internal/judge/**`
- 判题机与主服务器之间使用内网/VPN；有条件的上 mTLS
- 定期更新 Isolate 与系统，判题机不要存放数据库密码或证书
