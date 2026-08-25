# Copilot Spring Boot RocketMQ 5.x Starter

RocketMQ 5.x 集成 Starter，提供消息生产和消费自动配置。

## 功能特性

- ✅ RocketMQ 5.x 客户端自动配置
- ✅ 消息生产者自动配置
- ✅ 消息消费者自动配置
- ✅ 事务消息支持
- ✅ 延迟消息支持

## 快速开始

### 1. 引入 Maven 依赖

```xml
<dependency>
    <groupId>com.awesomecopilot</groupId>
    <artifactId>copilot-spring-boot-rocketmq5x-starter</artifactId>
    <version>${copilot.version}</version>
</dependency>
```

### 2. 配置 RocketMQ

```yaml
copilot:
  rocketmq:
    # NameServer 地址
    name-server: localhost:9876
    
    # 生产者配置
    producer:
      group: producer-group
      send-message-timeout: 3000
      retry-times-when-send-failed: 2
    
    # 消费者配置
    consumer:
      group: consumer-group
      consume-thread-min: 5
      consume-thread-max: 20
```

## 使用示例

### 发送消息

```java
@Autowired
private RocketMQTemplate rocketMQTemplate;

// 同步发送
rocketMQTemplate.convertAndSend("topic-name", message);

// 异步发送
rocketMQTemplate.asyncSend("topic-name", message, new SendCallback() {
    @Override
    public void onSuccess(SendResult sendResult) {
        log.info("发送成功");
    }
    
    @Override
    public void onException(Throwable e) {
        log.error("发送失败", e);
    }
});

// 延迟消息
rocketMQTemplate.syncSend("topic-name", 
    MessageBuilder.withPayload(message).build(), 
    3000, 
    3);  // 延迟级别
```

### 消费消息

```java
@RocketMQMessageListener(
    topic = "topic-name",
    consumerGroup = "consumer-group"
)
@Component
public class MessageConsumer implements RocketMQListener<String> {
    
    @Override
    public void onMessage(String message) {
        log.info("收到消息: {}", message);
    }
}
```

### 事务消息

```java
@RocketMQTransactionListener
@Component
public class TransactionListenerImpl implements RocketMQLocalTransactionListener {
    
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        // 执行本地事务
        try {
            // 业务逻辑
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }
    
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        // 检查本地事务状态
        return RocketMQLocalTransactionState.COMMIT;
    }
}
```

## 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `copilot.rocketmq.name-server` | String | - | NameServer 地址 |
| `copilot.rocketmq.producer.group` | String | - | 生产者组名 |
| `copilot.rocketmq.producer.send-message-timeout` | int | 3000 | 发送超时时间(ms) |
| `copilot.rocketmq.producer.retry-times-when-send-failed` | int | 2 | 发送失败重试次数 |
| `copilot.rocketmq.consumer.group` | String | - | 消费者组名 |
| `copilot.rocketmq.consumer.consume-thread-min` | int | 5 | 最小消费线程数 |
| `copilot.rocketmq.consumer.consume-thread-max` | int | 20 | 最大消费线程数 |

## 依赖说明

本 Starter 依赖以下模块：
- `rocketmq-spring-boot-starter`：RocketMQ Spring Boot Starter
- `rocketmq-client-java`：RocketMQ 5.x 客户端
