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

## License

见 [LICENSE](LICENSE)。
