# Copilot Spring Boot ES Starter

Elasticsearch 集成 Starter，提供 ES 客户端自动配置和模板管理。

## 功能特性

- ✅ Elasticsearch 客户端自动配置
- ✅ 索引模板自动初始化
- ✅ REST 和 Transport 双协议支持
- ✅ 自定义分析器配置

## 快速开始

### 1. 引入 Maven 依赖

```xml
<dependency>
    <groupId>com.awesomecopilot</groupId>
    <artifactId>copilot-spring-boot-es-starter</artifactId>
    <version>${copilot.version}</version>
</dependency>
```

### 2. 配置 Elasticsearch

**src/main/resources/elastic.properties：**

```properties
cluster.name=nta
elastic.hosts=192.168.100.101:9300,192.168.100.102:9300,192.168.100.103:9300
elastic.rest.hosts=192.168.100.101:9200,192.168.100.102:9200,192.168.100.103:9200
```

### 3. 配置索引模板

```yaml
copilot:
  es:
    init: true                          # 是否初始化模板
    templates:
      - netlog_template.json            # 模板文件路径
```

**模板文件路径规则：**
- `classpath:xxx.json` - 从 classpath 读取
- `/absolute/path/xxx.json` - 从文件系统读取
- `xxx.json` - 按优先级查找：./config/ → ./ → classpath

## 索引模板示例

```json
{
  "index_patterns": ["netlog_*"],
  "settings": {
    "index": {
      "refresh_interval": "60s",
      "number_of_shards": 1,
      "number_of_replicas": 0,
      "max_result_window": 100000000
    },
    "analysis": {
      "tokenizer": {
        "domain": {
          "type": "char_group",
          "tokenize_on_chars": [".", "/", "whitespace"]
        }
      },
      "analyzer": {
        "domain": {
          "tokenizer": "domain",
          "filter": ["lowercase"]
        }
      }
    }
  },
  "mappings": {
    "dynamic": false,
    "properties": {
      "id": {"type": "keyword", "norms": false},
      "create_time": {"type": "date", "format": "epoch_millis"},
      "src_ip": {"type": "ip"},
      "data.domain": {
        "type": "text",
        "analyzer": "domain",
        "fields": {
          "keyword": {"type": "keyword", "ignore_above": 256}
        }
      }
    }
  }
}
```

## 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `copilot.es.init` | boolean | false | 是否初始化索引模板 |
| `copilot.es.templates` | List | [] | 模板文件列表 |

## 依赖说明

本 Starter 依赖以下模块：
- `elasticsearch-rest-high-level-client`：ES REST 客户端
- `copilot-search`：搜索核心组件
