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

### 幂等消费（本 starter 增强）

```java
public class OrderConsumer {

    // 重复消息(broker 投递重试导致)第二次进入时直接确认跳过, 不执行业务逻辑;
    // 判重取值顺序: 注解 key 指定的用户属性 → 消息内置 UNIQ_KEY → msgId, 不使用业务 keys(可被多条消息有意共用)
    @RocketMQ5xIdempotent
    public void consume(MessageExt msg) {
        // 业务处理; 方法抛异常会释放幂等令牌, 重试消息可再次进入
    }

    @RocketMQ5xIdempotent(key = "bizNo")   // 或指定发送端 putUserProperty 塞进消息的业务唯一号属性
    public void consumeByBizNo(MessageExt msg) {
        // 业务处理
    }
}
```

要求: 方法第一参数必须是 MessageExt、返回 void（rocketmq-spring 的 RocketMQListener.onMessage 即 void, 容器按"有没有抛异常"决定重试、不读返回值; 其他返回类型首次调用即报错点名方法）。
注意:
- 业务号属性必须逐条消息唯一——不要用订单号这类会被多条消息共用的聚合 id 当幂等键(会把后到的合法消息误判重复丢弃); 也不要把 key 配成 "KEYS"(即 setKeys 的业务键), 切面会告警并忽略.
- 判重层级: UNIQ_KEY(发送端客户端生成、投递重试保持不变, 覆盖主场景) → msgId(重投会变, 只覆盖同位点重复拉取); 对唯一性有强要求的业务务必用 key 指定业务属性.

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
| `copilot.rocket5x.idempotent.enabled` | boolean | false | 幂等消费切面开关 |
| `copilot.rocket5x.idempotent.ttl-seconds` | long | 21600 | 幂等窗口秒数(令牌在 Redis 的存活时间; 默认配置下 16 次重试约 4h46m, 留余量) |
| `copilot.rocket5x.idempotent.fail-open-on-redis-error` | boolean | false | Redis 故障时 true=放行消费(幂等暂停), false=抛回使消息重试 |

## 升级注意事项（21.0.8 起）

- **令牌存储迁移**: 旧版把所有幂等标识装进一个永不过期的全局 Redis SET（`rocketmq:idempotent`），现改为每个标识一个独立 key（`rocketmq:idempotent:{uniqueId}`，带 TTL）。升级后旧 SET 不再被读取也不会自动清理，需手工执行一次 `DEL rocketmq:idempotent`；且切换瞬间旧 SET 中"已消费"的标识对新 key 空间不可见，存在一个 TTL 周期的重复消费暴露窗口，建议低峰发布。
- **判重不再使用业务 keys**: 旧版把 `msg.getKeys()` 当判重标识，多条消息共用同一 keys 时后到的合法消息会被误判重复丢弃（真丢数据）。现层级为 UNIQ_KEY → msgId；业务号判重必须显式 `@RocketMQ5xIdempotent(key="属性名")`。
- **返回类型收紧为 void**: 非 void 消费方法（含返回 ConsumeConcurrentlyStatus 的误用形态）首次调用即抛 IllegalStateException。此前文档宣称支持状态返回方法，系误记——rocketmq-spring 容器不读监听方法返回值。
- **已知窗口（未修复，见切面类注释）**: 消费失败后释放令牌又遇 Redis 故障时，该消息重投会被按重复确认丢弃且不进死信队列，只能按 error 日志中的 uniqueValue 人工补处理。

## 依赖说明

本 Starter 依赖以下模块：
- `rocketmq-spring-boot-starter`：RocketMQ Spring Boot Starter
- `rocketmq-client-java`：RocketMQ 5.x 客户端
