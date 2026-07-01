# Nexary Governance Platform Sample

这个样例演示治理平台 RC。它启动一个 Spring Boot 应用，写入一组脱敏云手机拓扑数据，并在 v0.21 起可以通过 Docker 中的 Redis、Postgres、RabbitMQ、Prometheus 和 SkyWalking 跑真实依赖探测与链路采集。Console 和平台 API 可以查看服务、依赖、请求链路、交易统计、主机水位、连接器状态、事故证据包、连接器配置和服务映射。

使用 Docker 启动完整验证环境：

```bash
./scripts/console/up.sh
./scripts/console/smoke.sh
open http://127.0.0.1:18090/nexary/console
```

Docker 环境包含：

- Console sample：`http://127.0.0.1:18090/nexary/console`
- Redis：`127.0.0.1:18091`
- Postgres：`127.0.0.1:18096`
- RabbitMQ：`127.0.0.1:18093`，管理页 `http://127.0.0.1:18094`
- Prometheus：`http://127.0.0.1:18095`
- SkyWalking：`http://127.0.0.1:18097`

运行：

```bash
./gradlew :nexary-samples:nexary-sample-governance-platform:bootRun
```

查看平台 API：

```bash
curl -s http://localhost:18092/api/platform/topology
curl -s http://localhost:18092/api/platform/services
curl -s http://localhost:18092/api/platform/incidents
curl -s http://localhost:18092/api/platform/request-flows
curl -s http://localhost:18092/api/platform/transactions
curl -s http://localhost:18092/api/platform/hosts
curl -s http://localhost:18092/api/platform/connectors
curl -s http://localhost:18092/api/platform/connector-configs
curl -s http://localhost:18092/api/platform/service-mappings
```

打开 Console：

```bash
open http://localhost:18092/nexary/console
```

重新写入 demo 数据：

```bash
curl -X POST http://localhost:18092/demo/platform/seed
```

触发真实依赖探测，并给 SkyWalking agent 产生请求链路：

```bash
curl -X POST 'http://localhost:18092/demo/platform/probe?iterations=50'
curl -s http://localhost:18092/demo/platform/prometheus | grep nexary_demo_probe_calls_total
```

边界：

- 只读查看，不写策略。
- 不修改 Sentinel、Gateway、APM、注册中心或生产通知渠道。
- 连接器配置中心只保存本地元数据、执行显式 TEST / DRY-RUN 探测，并保存服务映射。
- Docker probe 只做本地读写和轻量压力，不连接真实生产中间件。
- demo 数据只使用别名和低基数字段，不包含真实内网地址、token、密码、用户标识、业务 payload、完整异常文本或完整 stack trace。
