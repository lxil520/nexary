# Nexary Governance Platform Sample

这个样例演示 v0.17 的只读治理平台地基。它启动一个 Spring Boot 应用，在内存中写入一组抽象云手机拓扑数据，然后通过平台 API 和 Console Platform Mode 查看服务、依赖、连接器状态和事故候选。

运行：

```bash
./gradlew :nexary-samples:nexary-sample-governance-platform:bootRun
```

查看平台 API：

```bash
curl -s http://localhost:18092/api/platform/topology
curl -s http://localhost:18092/api/platform/services
curl -s http://localhost:18092/api/platform/incidents
curl -s http://localhost:18092/api/platform/connectors
```

打开 Console：

```bash
open http://localhost:18092/nexary/console/platform
```

重新写入 demo 数据：

```bash
curl -X POST http://localhost:18092/demo/platform/seed
```

边界：

- 只读查看，不写策略。
- 不修改 Sentinel、Gateway、APM、注册中心或通知渠道。
- demo 数据只使用抽象服务名和低基数字段，不包含真实内网地址、token、密码、用户标识或业务 payload。
