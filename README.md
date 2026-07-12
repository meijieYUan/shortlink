# ShortLink — 高性能多租户短链接服务

一个面向 B2B SaaS 场景的高性能短链接系统，支持多租户隔离、HMAC 鉴权、多级缓存、异步访问统计和管理控制台。

## 架构总览

```
┌──────────────────────────────────────────────────────────┐
│  shortlink-common   Result / 异常 / 工具 / 鉴权上下文     │
├──────────────────────────────────────────────────────────┤
│  shortlink-domain   实体 / DTO / Service 接口             │
├──────────────────┬──────────────────┬────────────────────┤
│  shortlink-core   │  shortlink-openapi│  shortlink-admin  │
│  启动入口         │  SDK 客户端 API   │  管理控制台        │
│  浏览器跳转       │  HMAC 鉴权       │  REST API + 前端   │
│  Service 实现     │  限流             │                    │
├──────────────────┴──────────────────┴────────────────────┤
│  shortlink-cache    Caffeine + Redis + 布隆过滤器          │
├──────────────────────────────────────────────────────────┤
│  shortlink-analytics  异步访问日志采集 + 分析查询           │
└──────────────────────────────────────────────────────────┘
```

---

## 快速开始

### 1. 启动基础设施

```bash
cd docker
docker compose up -d
```

这会启动 MySQL、Redis、RocketMQ NameServer + Broker。

### 2. 初始化数据库

```bash
# 用 docker/init.sql 初始化（首次启动时 Docker 会自动执行）
docker exec -i shortlink-mysql mysql -uroot -proot123 < docker/init.sql
```

### 3. 启动应用

```bash
# Windows
set JAVA_HOME=jdk-21\jdk-21.0.2
mvnw.cmd spring-boot:run -pl shortlink-core

# Linux / macOS
JAVA_HOME=jdk-21/jdk-21.0.2 ./mvnw spring-boot:run -pl shortlink-core
```

应用启动在 `http://localhost:8080`。

### 4. 验证

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 用测试密钥创建短链
curl -X POST http://localhost:8080/api/v1/shorten \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://github.com"}'

# 浏览器打开返回的短链即可跳转
```

### 5. 管理控制台

浏览器打开 `http://localhost:8080/`，用测试密钥登录：

| 字段 | 值 |
|---|---|
| Access Key | `sk_test_001` |
| Secret Key | `sec_001_secret_key_32_chars_here!` |

---

## API 文档

### SDK / 客户端 API（需 HMAC 鉴权）

所有 `/openapi/v1/*` 路径需要 HMAC-SHA256 签名认证。

#### 短链接 CRUD

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/openapi/v1/shorten` | 创建短链（支持自定义短码、过期时间） |
| `GET` | `/openapi/v1/shorten` | 分页列出我的短链 |
| `GET` | `/openapi/v1/shorten/{code}` | 查询单个短链 |
| `PUT` | `/openapi/v1/shorten/{code}` | 更新状态 / 过期时间 |
| `DELETE` | `/openapi/v1/shorten/{code}` | 软删除（进入回收站） |
| `POST` | `/openapi/v1/shorten/{code}/restore` | 从回收站恢复 |

#### 分析统计

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/openapi/v1/shorten/{code}/stats` | 单个短链的 PV |
| `GET` | `/openapi/v1/analytics/overview` | 我的总览（短链数 + PV） |
| `GET` | `/openapi/v1/analytics/daily-trend?days=7` | 最近 N 天的 PV 趋势 |
| `GET` | `/openapi/v1/analytics/top-links` | 我的热门短链排行 |

#### HMAC 签名方式

每个请求需要携带以下 Header：

```
X-AccessKey: 你的 AccessKey
X-Timestamp: 当前时间戳（毫秒）
X-Nonce:     随机 32 位 hex
X-Signature: HMAC-SHA256 签名（hex 小写）
```

签名串拼接规则（换行符 `\n`）：

```
{HTTP方法}\n{请求路径}\n{X-Timestamp}\n{X-Nonce}\n{Body的MD5}\n{Content-Type}
```

- GET / DELETE 请求：Body 为空，MD5 为空字符串
- POST / PUT 请求：Body MD5 = `MD5(请求体原始字符串).toLowerCase()`

**示例（curl + 预计算签名）**：

```bash
# 用项目自带工具计算签名
TIMESTAMP=$(date +%s%3N)
NONCE=$(openssl rand -hex 16)

curl -X POST http://localhost:8080/openapi/v1/shorten \
  -H "Content-Type: application/json" \
  -H "X-AccessKey: sk_test_001" \
  -H "X-Timestamp: $TIMESTAMP" \
  -H "X-Nonce: $NONCE" \
  -H "X-Signature: <计算出的签名>" \
  -d '{"originalUrl":"https://example.com"}'
```

### 管理后台 API（同样 HMAC 鉴权）

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/openapi/v1/admin/stats` | 短链统计 |
| `GET` | `/openapi/v1/admin/analytics/overview` | 完整概览 |
| `GET` | `/openapi/v1/admin/analytics/daily-trend` | 日访问趋势 |
| `GET` | `/openapi/v1/admin/analytics/top-links` | 热门短链 |
| `GET` | `/openapi/v1/admin/links` | 短链分页列表 |
| `GET` | `/openapi/v1/admin/links/{code}` | 查询短链详情 |
| `PUT` | `/openapi/v1/admin/links/{code}` | 更新短链 |
| `DELETE` | `/openapi/v1/admin/links/{code}` | 删除（回收站） |
| `POST` | `/openapi/v1/admin/links/{code}/restore` | 恢复 |
| `GET` | `/openapi/v1/admin/access-logs` | 访问日志查询 |
| `GET` | `/openapi/v1/admin/keys` | API 密钥列表 |
| `POST` | `/openapi/v1/admin/keys` | 创建密钥 |
| `PUT` | `/openapi/v1/admin/keys/{ak}` | 更新密钥 |
| `DELETE` | `/openapi/v1/admin/keys/{ak}` | 删除密钥 |

---

## 多租户隔离

每个 API 密钥（AccessKey）对应一个租户。创建短链时自动关联租户，所有查询和分析自动限定在租户范围内。

```
客户 A (sk_001) 创建短链 → app_key = sk_001
客户 B (sk_002) 查列表     → 只看到 sk_002 的数据
客户 A 打开控制台          → 只看到自己的短链和访问日志
```

### 创建新租户

```bash
curl -X POST http://localhost:8080/openapi/v1/admin/keys \
  -H "Content-Type: application/json" \
  -H ...(HMAC 签名) \
  -d '{"owner":"客户名称","quotaPerMinute":600}'
```

返回的 `secretKey` 仅展示一次，请妥善保存。

---

## 自定义短码安全

系统内置保留关键字黑名单，禁止占用以下短码：

`admin` `api` `openapi` `login` `logout` `health` `metrics` `swagger` `static` `favicon` `robots` 等 50+ 个系统路径

自定义短码要求：
- 长度 ≥ 4 位
- 仅限字母和数字
- 不在黑名单内

如需扩展黑名单，在 `application.yml` 中配置：

```yaml
shortlink:
  blacklist:
    min-custom-length: 4
    reserved:
      - admin
      - api
      - 你的自定义关键字
```

---

## 技术栈

| 层次 | 技术 |
|---|---|
| 框架 | Spring Boot 3.x + JDK 21 |
| 持久层 | MyBatis-Plus + MySQL 8.0 |
| 缓存 | Caffeine（L1 本地）+ Redis（L2） + 布隆过滤器 |
| 消息队列 | Apache RocketMQ（异步访问日志） |
| 异步框架 | Disruptor RingBuffer |
| 限流 | 本地令牌桶 + Redis 滑动窗口 |
| 鉴权 | HMAC-SHA256 + Nonce 防重放 |
| 容器化 | Docker Compose |

---

## 项目结构

```
shortlink/
├── docker/                    Docker Compose + 初始化 SQL
├── shortlink-common/          基础设施（Result、异常、工具类）
├── shortlink-domain/          业务契约（实体、DTO、Service 接口）
├── shortlink-core/            启动入口 + 浏览器跳转 + Service 实现
├── shortlink-openapi/         SDK 客户端 API + HMAC 鉴权 + 限流
├── shortlink-admin/           管理控制台 REST API + 前端页面
├── shortlink-cache/           多级缓存（Caffeine + Redis）
├── shortlink-analytics/       访问日志采集（Disruptor + RocketMQ） + 分析
├── shortlink-sdk/             Java SDK
└── pom.xml                    根 POM
```