# Nexary Messaging

- 中文入口：[../docs/zh/messaging.md](../docs/zh/messaging.md)
- English entry: [../docs/en/messaging.md](../docs/en/messaging.md)

本目录是 Nexary 的消息能力入口，不是全仓库总览。

当前关注：

- `nexary-messaging-api`
- Kafka / RocketMQ / Redis queue / Disruptor
- 重复消费保护抽象
- provider-neutral retry policy 和 terminal failure record
- Redis queue ready / processing / ack / retry / stale recovery 状态模型
- provider-neutral observation events for publish / consume / retry / dead-letter / dedup / provider boundaries

验收清单：

- 中文：[../docs/zh/messaging-acceptance.md](../docs/zh/messaging-acceptance.md)
- English: [../docs/en/messaging-acceptance.md](../docs/en/messaging-acceptance.md)
