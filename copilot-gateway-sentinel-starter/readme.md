# 一 Maven依赖

负责SpringCloudGateway整合Sentinel

引入后网关那边需要引入依赖

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-alibaba-sentinel-gateway</artifactId>
</dependency>
<!-- sentinel规则持久化 -->
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>
```

开启网关限流

```properties
copilot.gateway.sentinel.enabled=true
```

