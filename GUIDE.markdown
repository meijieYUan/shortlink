# ShortLink — 高性能短链接系统

> 一个看起来简单、往下挖全是坑的高并发项目。  
> 目标：单机支撑 10w+ QPS 读、毫秒级跳转、支撑亿级短链存储。

---

## 技术选型总览

| 层次 | 技术栈 |
|------|--------|
| 基础框架 | Spring Boot 3.x + JDK 17+ + Gradle |
| 网络层 | Undertow（替代 Tomcat，吞吐更高） |
| 持久层 | MyBatis-Plus + MySQL 8.0 + ShardingSphere（分库分表） |
| 缓存层 | Redis 7.x（Cluster）+ Caffeine（本地缓存） |
| 消息队列 | Apache Kafka / RocketMQ（访问日志异步写入） |
| 搜索引擎 | Elasticsearch（访问统计检索） |
| 可观测性 | Micrometer + Prometheus + Grafana + SkyWalking |
| 压测 | JMeter + wrk + async-profiler |
| 容器化 | Docker + Docker Compose（本地开发）+ K8s（生产部署） |
| 配置中心 | Nacos（动态配置热更新） |

---

## 项目阶段划分

---

### 第一阶段：项目骨架与基础设施 【预计 2-3 天】

**目标**：跑通开发环境，建立干净的工程结构。

**工作项**

- [ ] 初始化 Spring Boot 3.x 项目（Gradle 多模块）
- [ ] 模块划分：
  - `shortlink-common` — 公共工具、常量、异常定义
  - `shortlink-core` — 核心业务逻辑（短链生成、跳转）
  - `shortlink-cache` — 缓存抽象层
  - `shortlink-analytics` — 统计分析模块
  - `shortlink-admin` — 管理后台 API
  - `shortlink-openapi` — 对外开放 API
- [ ] 引入核心依赖：Spring Boot Web、MyBatis-Plus、Redis、Kafka、Lombok、Hutool
- [ ] 配置 Undertow 作为 Web 容器，禁用 Tomcat
- [ ] Docker Compose 编排开发中间件：MySQL、Redis、Kafka、Elasticsearch
- [ ] 统一响应体、全局异常处理、请求日志拦截器
- [ ] 集成 Micrometer + Prometheus 指标导出端点
- [ ] Git 初始化 + `.gitignore` + 编码规范（Checkstyle）

**产出物**
- 可启动的空项目，`/actuator/health` 正常响应
- 所有中间件通过 Docker Compose 一键启动

---

### 第二阶段：短链生成与跳转核心链路 【预计 3-4 天】

**目标**：实现最短可用的 MVP——能生成短链、能跳转。

**工作项**

- [ ] 设计短链映射表结构：
  ```sql
  CREATE TABLE t_short_link (
    id            BIGINT PRIMARY KEY,
    short_code    VARCHAR(8)  NOT NULL UNIQUE,  -- 短码，如 "aB3x9K"
    original_url  TEXT        NOT NULL,          -- 原始长链
    expire_time   DATETIME    DEFAULT NULL,      -- 过期时间
    status        TINYINT     DEFAULT 1,         -- 1:有效 0:失效
    creator       VARCHAR(64) DEFAULT '',
    create_time   DATETIME    NOT NULL,
    update_time   DATETIME    NOT NULL
  );
  CREATE INDEX idx_short_code ON t_short_link(short_code);
  ```
- [ ] 实现 Base62 编解码工具类（`short_code` ↔ `id` 双向转换）
- [ ] 发号器 V1——基于数据库自增 ID 的简单实现
- [ ] 短链生成接口 `POST /api/v1/shorten`：
  - 入参：`originalUrl`、`expireTime`（可选）
  - 出参：`shortCode`、`shortUrl`
  - 幂等性：相同长链 + 未过期 → 返回已有短链
- [ ] 跳转接口 `GET /{shortCode}`：
  - 查缓存 → 查 DB → 302 重定向
  - 未找到返回 404 页面
  - 已过期返回 410 Gone
- [ ] 单元测试覆盖率：核心逻辑 ≥80%

**产出物**
- 能通过 `curl` 完成"生成短链 → 浏览器打开短链 → 跳转到目标页面"的完整闭环

---

### 第三阶段：高性能读链路——多级缓存 【预计 4-5 天】

**目标**：读路径延迟降到 5ms 以内，承受热点短链的高并发访问。

**工作项**

- [ ] **L1 缓存 — Caffeine 本地缓存**
  - 缓存短码 → 原始 URL 映射
  - 最大 10000 条，LRU 淘汰，写入后 30 分钟过期
  - 监控命中率（Micrometer Counter）
- [ ] **L2 缓存 — Redis**
  - Key 设计：`shortlink:{shortCode}` → JSON `{originalUrl, expireTime, status}`
  - 过期时间对齐 DB 的 expire_time，避免脏读
  - Redis Cluster 环境下的 slot 分布验证
- [ ] **缓存穿透防护 — 布隆过滤器**
  - 使用 Redisson 的 `RBloomFilter`
  - 所有已生成的 shortCode 写入布隆过滤器
  - 系统启动时从 DB 全量重建布隆过滤器
- [ ] **缓存击穿防护**
  - 热点 shortCode 过期时使用 Redis 分布式锁保证只有一个线程回源 DB
  - 逻辑过期 + 异步刷新：不设 TTL，value 中存逻辑过期时间，后台线程异步刷新
- [ ] **缓存雪崩防护**
  - Redis 过期时间添加随机偏移（± 5 分钟）
  - 缓存降级开关：Redis 不可用时直接走 DB + 限流兜底
- [ ] **缓存一致性**
  - 更新/删除短链时先更新 DB，再删除 Redis（Cache Aside 模式）
  - 延时双删 + MQ 重试保证最终一致

**产出物**
- 压测脚本（JMeter），单机读 QPS 达到 5w+（本地缓存命中场景）
- 缓存命中率 Grafana 面板

---

### 第四阶段：分布式发号器 【预计 3-4 天】

**目标**：替换数据库自增 ID，实现高性能、全局唯一的发号器。

**工作项**

- [ ] **方案调研对比**
  | 方案 | 优点 | 缺点 |
  |------|------|------|
  | DB 自增 | 简单 | 单点瓶颈 |
  | UUID | 无中心 | 长，不适合短码 |
  | Snowflake | 高性能 | 时钟回拨 |
  | 号段模式（Leaf） | 高性能+易扩展 | 需 DB 协调 |
- [ ] **号段模式实现（推荐）**
  - 号段表设计：
    ```sql
    CREATE TABLE t_id_segment (
      biz_tag     VARCHAR(64) PRIMARY KEY,  -- 业务标识
      max_id      BIGINT NOT NULL,           -- 当前已分配最大 ID
      step        INT    NOT NULL DEFAULT 2000,
      update_time DATETIME
    );
    ```
  - 本地双 Buffer 设计：当前号段 + 预加载号段，异步填充
  - 多节点并发取号段使用 `UPDATE ... SET max_id = max_id + step WHERE biz_tag = ?` 的乐观锁
- [ ] **Snowflake 作为备选**
  - workId 通过 Zookeeper/Nacos 临时节点自动分配
  - 时钟回拨处理：等待时钟追上 或 抛出异常拒绝服务
- [ ] 发号器抽象接口，支持运行时切换实现
- [ ] 压测发号器 TPS ≥ 50w/s（单机）

**产出物**
- 独立的发号器模块，可被其他服务复用

---

### 第五阶段：异步访问统计 【预计 3-4 天】

**目标**：精确记录每次短链访问，不阻塞主跳转链路。

**工作项**

- [ ] **数据采集层**
  - 跳转成功时异步发送访问事件到 Disruptor RingBuffer
  - RingBuffer 大小 16384，多生产者单消费者模式
  - 事件结构：`{shortCode, ip, userAgent, referer, timestamp, country, city}`
- [ ] **批量写入层**
  - 消费者每 200ms 或累积 500 条批量写入 Kafka
  - Kafka Topic 设计：`shortlink-access-log`，分区数 8
- [ ] **存储层**
  - 原始日志：Elasticsearch（保留 30 天，按天创建索引）
  - 聚合统计：MySQL `t_access_stats`（小时级别聚合，TTL 自动清理）
- [ ] **聚合策略**
  - Flink / 自研轻量聚合器消费 Kafka
  - 按 `(shortCode, hour)` 维度聚合 PV/UV/IP 分布
  - 使用 Redis HyperLogLog 近似去重统计 UV
- [ ] **IP 地理位置解析**
  - 离线加载 GeoLite2 mmdb 到本地内存
  - 访问事件中异步附加地理位置信息

**产出物**
- 每次跳转记录不丢失、不阻塞主链路
- 支持按短链查询实时 PV/UV

---

### 第六阶段：开放 API 平台 【预计 3-4 天】

**目标**：提供对外的 API 调用能力，包含鉴权、限流、配额管理。

**工作项**

- [ ] **API 鉴权（AK/SK 签名模式）**
  - 接入方注册获取 AppKey + AppSecret
  - 请求签名算法：HMAC-SHA256，签名串包含 `{method, path, timestamp, nonce, body}`
  - 防重放攻击：nonce + timestamp（±5 分钟窗口）
- [ ] **限流控制**
  - 本地方案：令牌桶（Guava RateLimiter or 自研）
  - 分布式方案：Redis Lua 滑动窗口
  - 粒度：全局 QPS、单 AppKey QPS、单接口 QPS
  - 超限返回 429 + `X-RateLimit-*` 响应头
- [ ] **配额管理**
  - 每日/每月短链生成数量限制
  - 配额消耗实时扣减（Redis DECR + Lua）
  - 配额恢复定时任务（每月 1 日凌晨重置）
- [ ] **SDK 输出（Java 版）**
  - 封装签名、重试、连接池
  - 提供 `ShortLinkClient` 一键调用

**产出物**
- 完整的 AK/SK 认证流程 + 限流策略
- Java SDK 可直接给调用方使用

---

### 第七阶段：高级功能 & 管理后台 【预计 3-4 天】

**目标**：补齐业务完整度，提供可用的管理界面。

**工作项**

- [ ] **自定义短码**
  - 用户可指定期望短码（如品牌名缩写）
  - 冲突检测失败时返回建议短码
  - 保留短码黑名单（防止占用系统关键词）
- [ ] **短链有效期**
  - 支持创建时指定过期时间
  - 定时任务每分钟扫描过期短链，标记 status=0
  - 扫描优化：只扫描 `expire_time <= NOW() AND status = 1`，索引覆盖
- [ ] **回收站机制**
  - 删除短链进入回收站（逻辑删除，7 天）
  - 回收站内可恢复或彻底删除
  - 定时任务清理超期回收站记录
- [ ] **管理后台 API**
  - 短链分页查询、搜索、筛选（按状态、创建时间、创建人）
  - 批量生成短链（Excel 上传）
  - 短链访问趋势图表数据接口（日/周/月维度）
- [ ] **数据导出**
  - 短链列表导出 CSV
  - 访问统计导出 Excel

**产出物**
- 功能完备的短链管理 REST API
- 可对接前端管理控制台

---

### 第八阶段：性能压测与调优 【预计 3-4 天】

**目标**：拿到硬核的性能数据，形成可展示的调优报告。

**工作项**

- [ ] **压测环境搭建**
  - JMeter 编写压测脚本：生成短链 + 跳转混合场景
  - wrk 纯读压测（预热本地缓存后压热点短链）
  - 准备 100w 条测试短链数据
- [ ] **压测指标目标**
  | 指标 | 目标值 | 测试场景 |
  |------|--------|----------|
  | 读 QPS | ≥ 10w/s | 纯缓存命中 |
  | 读 P99 | ≤ 3ms | 纯缓存命中 |
  | 写 TPS | ≥ 1w/s | 短链生成 |
  | CPU | ≤ 70% | 满载运行时 |
  | GC 停顿 | ≤ 50ms | Young GC |
- [ ] **JVM 调优**
  - GC 选型：G1（`-XX:+UseG1GC`）
  - 堆大小：4G，`-Xms4g -Xmx4g`（避免动态伸缩）
  - `-XX:MaxGCPauseMillis=50`
  - 堆外内存：`-XX:MaxDirectMemorySize=512m`（Netty 使用）
- [ ] **系统参数调优**
  - `ulimit -n 65535`（最大文件描述符）
  - `net.core.somaxconn = 1024`（TCP 连接队列）
  - `vm.swappiness = 1`（减少 swap）
- [ ] **连接池调优**
  - HikariCP：`maximumPoolSize=20`, `minimumIdle=10`
  - Lettuce（Redis）：`pool.max-active=20`, `pool.max-idle=10`
- [ ] **热点数据探测**
  - 实现自适应热点识别：滑动窗口统计每个 shortCode 的访问频率
  - 达到阈值自动提权到本地缓存，降低阈值后降权
- [ ] **压测报告输出**
  - QPS-TPS 折线图（Grafana）
  - P50/P99/P999 延迟分布图
  - GC 日志分析（GCViewer / GCEasy）
  - async-profiler 火焰图分析 CPU 热点

**产出物**
- 压测报告 PDF（可直接附在简历中）
- 性能瓶颈分析过程记录

---

### 第九阶段：容器化与持续集成 【预计 2-3 天】

**目标**：工程化收尾，形成可交付的制品。

**工作项**

- [ ] **Docker 镜像构建**
  - 多阶段构建（gradle 编译 + JRE 运行镜像）
  - 基础镜像：`eclipse-temurin:17-jre-alpine`
  - 镜像瘦身：排除无用依赖，最终镜像 ≤ 200MB
- [ ] **Docker Compose 全链路编排**
  - 一命令启动：MySQL + Redis + Kafka + ES + shortlink-app
  - 健康检查（`depends_on` + `healthcheck`）
- [ ] **K8s 部署文件（可选）**
  - Deployment + Service + ConfigMap
  - HPA 自动扩缩容配置（基于 CPU ≥ 70%）
  - Readiness Probe & Liveness Probe
- [ ] **CI/CD（GitHub Actions）**
  - push → 编译 → 单元测试 → 镜像构建 → 推送镜像仓库
  - PR → 自动跑集成测试

**产出物**
- 本地 `docker compose up` 即可启动完整环境
- CI 流水线通过

---

### 第十阶段（可选）：安全加固与生产化 【预计 2-3 天】

**目标**：补齐安全短板，让项目经得起生产环境的检验。

**工作项**

- [ ] **短链安全性**
  - 目标 URL 白名单/黑名单校验（防止钓鱼短链）
  - 短链接入风控：同一 IP 短时间大量创建短链 → 触发验证码
  - 敏感短链（如已过期）301 → 提醒页面而非直接跳转
- [ ] **接口安全**
  - CORS 白名单配置
  - SQL 注入防护（MyBatis 参数化查询）
  - XSS 过滤（URL 参数、Referer 等）
  - 敏感信息脱敏（日志中 IP 掩码处理）
- [ ] **数据保护**
  - 数据库连接加密（SSL/TLS）
  - Redis 密码 + ACL 用户隔离
  - 关键配置项接入 Vault / 环境变量注入（禁止硬编码）
- [ ] **灾备**
  - MySQL 主从 + 定时全量备份脚本
  - Redis RDB + AOF 混合持久化

---

## 项目目录结构（规划）

```
shortlink/
├── docker-compose.yml                 # 本地开发中间件编排
├── Dockerfile                         # 应用镜像
├── build.gradle.kts                   # 根构建脚本
├── settings.gradle.kts
├── GUIDE.markdown                     # 本文件
├── docs/
│   ├── architecture.drawio            # 架构图
│   ├── benchmark-report.md            # 压测报告
│   └── api-spec.yaml                  # OpenAPI 3.0 规范
├── shortlink-common/
│   └── src/main/java/com/shortlink/common/
│       ├── constant/
│       ├── exception/
│       ├── result/                    # 统一响应体
│       └── util/
├── shortlink-core/
│   └── src/main/java/com/shortlink/core/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── model/
│       ├── idgen/                     # 发号器
│       └── bloom/                     # 布隆过滤器逻辑
├── shortlink-cache/
│   └── src/main/java/com/shortlink/cache/
│       ├── local/                     # Caffeine 配置
│       ├── redis/                     # Redis 抽象
│       └── strategy/                  # 缓存策略
├── shortlink-analytics/
│   └── src/main/java/com/shortlink/analytics/
│       ├── event/                     # 访问事件模型 + Disruptor
│       ├── kafka/
│       ├── repository/                # ES 操作
│       └── aggregate/                 # 离线聚合
├── shortlink-admin/
│   └── src/main/java/com/shortlink/admin/
│       ├── controller/
│       └── service/
├── shortlink-openapi/
│   └── src/main/java/com/shortlink/openapi/
│       ├── auth/                      # AK/SK 签名验证
│       ├── ratelimit/                 # 限流
│       └── controller/
└── shortlink-sdk/
    └── src/main/java/com/shortlink/sdk/
        ├── ShortLinkClient.java
        └── config/
```

---

## 关键里程碑 & 交付物总览

| 阶段 | 里程碑 | 核心交付物 |
|------|--------|-----------|
| 一 | 项目启动 | 可运行的空项目 + Docker Compose 环境 |
| 二 | MVP | 短链生成 + 跳转完整闭环 |
| 三 | 缓存层 | 多级缓存 + 布隆过滤器 + 缓存防护三件套 |
| 四 | 发号器 | 号段模式发号器，TPS ≥ 50w |
| 五 | 统计系统 | 异步访问日志采集 + PV/UV 查询 |
| 六 | 开放平台 | AK/SK 鉴权 + 限流 + Java SDK |
| 七 | 功能完善 | 自定义短码 + 管理后台 API |
| 八 | 性能报告 | 压测报告 + JVM 调优记录 + 火焰图 |
| 九 | 容器化 | Docker 镜像 + CI/CD 流水线 |
| 十 | 安全加固 | 安全基线检查清单 + 加固实现 |

---

## 技能地图（做完这个项目你能掌握什么）

```
网络编程：    Undertow 配置调优、HTTP 302/301 重定向语义
缓存设计：    多级缓存、布隆过滤器、穿透/击穿/雪崩防护
分布式：      号段发号器、分布式锁、Redis Cluster
并发编程：    Disruptor RingBuffer、线程池隔离、异步非阻塞
消息队列：    Kafka 生产消费、批量写入优化
可观测性：    Micrometer + Prometheus + Grafana + 火焰图
性能工程：    JMeter/wrk 压测、JVM GC 调优、系统参数调优
工程化：      Gradle 多模块、Docker 多阶段构建、CI/CD
安全：        HMAC 签名认证、限流策略、XSS/SQL注入防护
```
