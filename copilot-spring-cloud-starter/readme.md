# 一 接口幂等性@Idempotent

* 在需要做接口幂等性的接口方法上添加@Idempotent注解
* 调用方的feign要加上这个拦截器 com.awesomecopilot.cloud.feign.interceptor.IdempotentInterceptor 以填充Idempotent请求头到Header中
* 处理@Idempotent注解的是IdempotentAspect
* 加了@Idempotent注解的微服务要在application.yaml中配置 copilot.idemtotent.enabled: true, 否则不会生效, 这个配置项的作用是配置bean: IdempotentAspect

目前基于Redis的Hyperloglog来实现, 开启开关:

```properties
loser.idemtotent.enabled=true
```

在要做幂等性控制的方法上加@Idemtotent 注解, 如果被幂等性控制了, 返回调用成功, 但是返回的数据是null

1. 被调用方在resources下要放置redis.properties, 配置JedisUtils连接信息

   连接单实例Redis配置示例:

   ```properties
   redis.host=localhost
   redis.port=6379
   redis.password=123456
   ```

   连接Sentinel配置示例:

   ```properties
   redis.sentinels=192.168.100.101:26379,192.168.100.102:26379,192.168.100.103:26379
   redis.password=123456
   ```

   连接Redis集群配置示例

   ```properties
   redis.clusters=192.168.100.101:6379,192.168.100.101:6380,192.168.2.102:6379,192.168.2.102:6380,192.168.2.103:6379,192.168.2.103:6380
   redis.password=123456
   ```



# 二 Sentinel异常处理

开启开关, 这是默认就开启的, 要关闭设为false即可

```yaml
copilot:
  sentinel:
    rest-exception-enabled: true
```

作用: 注册一个Bean RestBlockExceptionHandler, 用来对Sentinel流控, 降级等异常进行处理

**原理解析:**

​	Sentinel注册了一个AbstractSentinelInterceptor, 这是实现了Spring MVC的HandlerInterceptor, 在其preHandle方法里面捕获流控异常, 然后交给DefaultBlockExceptionHandler去处理, 这个类就默认返回字符串 "Blocked by Sentinel (flow limiting)", 我这边提供的RestBlockExceptionHandler返回REST结果, 结果类似这样

```json
{"code":"42901","desc":"已被流控"}
```

配置了RestExceptionAdvice后, RestBlockExceptionHandler处理熔断规则将不生效, 流控还是OK的

所以在RestExceptionAdvice#handleThrowable方法里面特意检查了一下是否存在RestBlockExceptionHandler这个类, 如果存在就直接重新抛出异常



application.yaml配置项

```yaml
copilot.sentinel.rest-exception-enabled: true
```

控制是否要对流控异常做统一异常处理, 默认true



# 三 Sentinel授权规则以及根据调用方流控支持

1. application.yaml添加配置

   ```yaml
   copilot:
     sentinel:
       auth-rule:
         enabled: true
   ```

2. CopilotSpringCloudAutoConfiguration会自动配置bean: originParser, 默认取 Auth-Origin 这个请求头

   ```java
   public class CopilotOriginParser implements RequestOriginParser {
   
   	private static final Logger log = LoggerFactory.getLogger(CopilotOriginParser.class);
   
   	@Autowired
   	private SentinelProperties sentinelProperties;
   
   	@Override
   	public String parseOrigin(HttpServletRequest request) {
   		String header = sentinelProperties.getAuthRule().getHeader();
   		String headerValue = request.getHeader(header);
   		log.info("请求头 {} 的值为 {}", header, headerValue);
   		return headerValue;
   	}
   }
   ```
   
3. 还会自动配置AuthFlowInterceptor, 用来实现微服务A通过feign调微服务B时候传递Origin请求头

# 四 租户ID

CopilotSpringCloudAutoConfiguration里面配置了全局的TenantIdInterceptor, 不需要手工配置, 对所有Feign客户端生效, 从当前请求头中拿Tenant-Id, 如果有的话塞到Feign的请求头中传递下去, 因为这个只是检查一下有没有, 有的话传递下去, 所以不需要开关, 默认开启就是了

```java
/**
 * 用于在feign调用的时候传递请求头中的租户ID
 * @return
 */
@Bean
@ConditionalOnMissingBean(TenantIdInterceptor.class)
public TenantIdInterceptor tenantIdInterceptor() {
  return new TenantIdInterceptor();
}
```

配合

```yaml
copilot:
  filter:
    tenant:
      mandatory: true
```

达到强制传TenantId的效果
