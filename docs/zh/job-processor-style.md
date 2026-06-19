# Processor-style Job 集成

Processor-style 是面向生产任务执行进程的接入形态。它和 focused 接入 sample 不同：

- focused sample 帮助使用者理解本地调度和 bridge 触发
- processor-style 以非 Web 进程运行，更接近真实任务执行器

## 目标形态

Processor-style job 进程应满足：

- 使用 Spring Boot 启动，但 `WebApplicationType.NONE`
- job handler 使用 Spring `@Component` 注册
- handler 实现 Nexary 的 `NexaryJob`
- 执行入口使用 `JobContext` 接收分片和运行上下文

业务 handler 是稳定层。processor 启动、外部平台触发适配、executor registration 和回执上报不应写进业务 handler。

## 当前参考骨架

参考骨架位于：

```text
nexary-samples/nexary-sample-job/src/main/java/org/nexary/samples/job/processor/
```

关键类：

- `JobProcessorSampleApplication`：非 Web 启动入口
- `ProcessorBusinessJob`：component-scanned job handler

## 与 focused sample 的关系

`nexary-sample-job` 现在有两条清晰路径：

- starter selector focused sample：通过 `nexary.job.provider` 在本地调度和 XXL-JOB 触发映射 provider 间切换
- `processor`：非 Web 生产式任务进程 skeleton

两者共享 `NexaryJob` 抽象，但使用场景不同。不要把 starter selector sample 当成 processor 生产形态，也不要把 processor skeleton 当成完整外部平台集成证明。

focused sample 的 `business/SampleBusinessJob` 展示了更明确的 接入 形态：业务 handler 只实现 `NexaryJob`，切换本地调度和 XXL-JOB bridge 不修改业务代码。

## 运行方式

```bash
./gradlew :nexary-samples:nexary-sample-job:runProcessor
```

默认会用 `processor` profile 启动非 Web Spring Boot 进程。任务触发由 Nexary provider 或外部平台桥接进入业务 job。

## 与 XXL-JOB bridge 的关系

当前 processor skeleton 只展示独立任务进程的业务 job 写法和非 Web 启动形态。

它不声明已经完成：

- 真实 XXL-JOB executor 注册
- Admin 调度
- 平台回调完整生命周期

这些需要 Docker 环境和集成验证单独证明。

## 对外 API 边界

Nexary 的 processor-style 集成只暴露 Nexary 自己的 job API。第三方或私有实现里的 handler 类型、参数类型和命名不会进入 Nexary 公共 API。

关键设计映射为：

- job handler：实现 `NexaryJob`
- 运行参数：使用 `JobContext`

## 非目标

- 不新增第二套公开 job handler API
- 不把 RPC、MQ、cache 协作者变成 job API 的一部分
- 不把 PowerJob bridge 说成完整 PowerJob 平台生命周期托管
- 不把 processor skeleton 包装成完整外部平台集成证明
