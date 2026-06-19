# nexary-sample-messaging-spi-activemq-classic

这是 Messaging 的 不用 starter 的依赖方式 引入样例：只引入 `nexary-messaging-api` 和 `nexary-messaging-activemq-classic`。

业务代码仍然只使用 `org.nexary.messaging.*`，不直接创建 JMS 或 ActiveMQ Classic 对象。切换到其它 provider 时，新建/使用对应 provider SPI 样例模块并调整依赖，不改 facade、controller、consumer。

样例业务包：`org.nexary.samples.messaging.spi.activemqclassic.*`。consumer 只实现 Nexary `NexaryMessageHandler` 并标注 `@NexaryMessageListener`；provider 加载、订阅注册和重复消费保护由 Nexary ActiveMQ Classic provider 自动配置完成。

失败处理同样由 Nexary 统一完成：`MessageRetryPolicy` 控制有界重试，耗尽后写入 `MessageDeadLetterRecord`。业务 consumer 不处理 ActiveMQ Classic 原生对象，也不声明 exactly-once。

发送侧核心用法：

```java
messageProducer.sendMessage(MessagingSampleTopics.APP_ERROR_LOG, message);
```

运行前需要一个本地 ActiveMQ Classic broker，默认地址是 `tcp://127.0.0.1:61616`。

```bash
./gradlew :nexary-samples:nexary-sample-messaging-spi-activemq-classic:run
```

端口：`8098`。

接口：

- `POST /app-error-logs`
- `GET /app-error-logs`
