# Nexary Sentinel 治理样例

这个样例演示 Spring Boot 3.3 主线如何把 Nexary 治理资源交给 Sentinel 执行。业务代码仍调用 `GovernanceRuntime`，不直接写 Sentinel API；Sentinel 负责 QPS 限流、线程数隔离、慢调用熔断和异常熔断，Nexary 负责 fallback、低基数诊断和只读 Console。

## 运行样例

```bash
./gradlew :nexary-samples:nexary-sample-governance-sentinel:run
```

样例默认打开：

- `nexary.governance.provider=sentinel`
- `nexary.governance.diagnostics.enabled=true`
- `nexary.console.enabled=true`
- Sentinel transport 关闭，不需要 Sentinel Dashboard

## 触发治理行为

QPS 限流：

```bash
curl -s http://localhost:8080/governance/sentinel/rate
curl -s http://localhost:8080/governance/sentinel/rate
```

并发隔离：

```bash
curl -s "http://localhost:8080/governance/sentinel/bulkhead?holdMillis=1000" &
curl -s "http://localhost:8080/governance/sentinel/bulkhead?holdMillis=1000"
wait
```

慢调用熔断：

```bash
curl -s "http://localhost:8080/governance/sentinel/slow?durationMillis=150"
curl -s "http://localhost:8080/governance/sentinel/slow?durationMillis=150"
curl -s "http://localhost:8080/governance/sentinel/slow?durationMillis=25"
```

异常熔断：

```bash
curl -s http://localhost:8080/governance/sentinel/failure || true
curl -s http://localhost:8080/governance/sentinel/failure || true
curl -s http://localhost:8080/governance/sentinel/failure || true
```

显式降级：

```bash
curl -s http://localhost:8080/governance/sentinel/fallback
```

停止重试传播：

```bash
curl -s http://localhost:8080/governance/sentinel/retry-stop
```

## 查看诊断和 Console

```bash
curl -s http://localhost:8080/nexary/governance/summary
curl -s http://localhost:8080/nexary/governance/resources
curl -s http://localhost:8080/nexary/governance/events
open http://localhost:8080/nexary/console
```

也可以直接跑 smoke：

```bash
NEXARY_GOVERNANCE_SENTINEL_BASE_URL=http://localhost:8080 ./scripts/governance-sentinel/smoke.sh
```

重点字段：

- `engine`: `SENTINEL`
- `blockReason`: `RATE_LIMITED`、`BULKHEAD_FULL` 或 `CIRCUIT_OPEN`
- `lastBlockReason`: 资源快照里的最近 Sentinel 拦截原因
- `retryStopReason`: 最近事件里的停止重试原因
- `lastRetryStopReason`: 资源快照里的最近停止重试原因
- `blockedCount`: 当前 JVM 内 Sentinel 拦截次数
- `retryStoppedCount`: 当前 JVM 内停止重试事件数量
- `sentinelResourceCount`: 当前 JVM 内 Sentinel 资源数量

诊断和 Console 不输出 Sentinel origin、cancellation id、userId、tenant、订单号、cache key、message id、payload、异常全文或堆栈。

## 边界

- 这个样例只声明 Spring Boot 3.3 / Java 17+ 主线。
- 它不替代 Sentinel Dashboard、集群限流或远程规则平台。
- Sentinel transport 默认关闭；只有显式配置 dashboard server 时才接入已有 Sentinel Dashboard。
- Boot2 / Boot4 Sentinel provider 要等各自样例和 gate 通过后再写入 README 支持矩阵。
