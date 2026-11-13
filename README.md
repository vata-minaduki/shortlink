# ShortLink · 短链接服务

一个基于 Spring Boot 的短链接服务：把长网址转成短码，访问短码时 302 跳转到原地址。

## 功能

- [x] 创建短链接：自增 ID + Base62 编码生成短码
- [x] 跳转：`GET /{code}` 返回 302
- [x] 异常处理：不存在 / 已禁用 / 已过期 → 404
- [x] 数据持久化（MySQL + MyBatis）
- [ ] 点击统计（Redis 计数 + 定时落库）
- [ ] Redis 缓存（Cache-Aside）
- [ ] 过期数据自动清理
- [ ] 开放重定向防护 / IP 限流
- [ ] Docker + Nginx 部署

## 技术栈

| 层 | 技术 |
|---|---|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.5 |
| Web | Spring MVC（内嵌 Tomcat） |
| 持久层 | MyBatis 3 |
| 数据库 | MySQL 8 |
| 连接池 | HikariCP |
| 构建 | Maven |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8

### 1. 建库建表

```bash
mysql -u root -p < schema.sql
```

### 2. 配置数据库密码

数据库密码不写入仓库，通过环境变量注入：

```bash
# Linux / macOS
export MYSQL_PASSWORD=你的密码

# Windows PowerShell
$env:MYSQL_PASSWORD="你的密码"
```

或在 IDEA 的 Run Configuration 中设置环境变量 `MYSQL_PASSWORD`。

其余配置见 `src/main/resources/application.yml`。

### 3. 启动

```bash
mvn spring-boot:run
```

服务默认监听 `http://127.0.0.1:8080`。

## 接口

### 创建短链接

```http
POST /api/links
Content-Type: application/json

{"longUrl": "https://www.example.com/very/long/path"}
```

响应：

```json
{
  "code": "1",
  "shortUrl": "http://127.0.0.1:8080/1"
}
```

### 跳转

```http
GET /{code}
```

- 存在且有效：`302 Found`，`Location: 原长链接`
- 不存在 / 已禁用 / 已过期：`404 Not Found`

## 核心设计

### 短码生成

短码由数据库自增 `id` 经 **Base62** 编码得到：

```
id = 1        → "1"
id = 62       → "10"
id = 3843     → "ZZ"
```

7 位 Base62 可表示 62^7 ≈ 3.5 万亿个短码。

**为什么用 Base62 而不是直接数字**：URL 安全的 62 个字符（`0-9a-zA-Z`，去掉 `+ / =`），信息密度高，长度可控。

**为什么是"先插占位码再回填"**：短码依赖自增 `id`，而 `id` 在插入后才产生；`code` 列 `NOT NULL`，所以先写入一个临时占位码，拿到 `id` 后计算真码并 `UPDATE`。两步操作包在 `@Transactional` 中，保证原子性。

### 为什么用 302 而不是 301

301 是永久重定向，会被浏览器缓存，之后请求不再经过服务端——**点击统计和过期控制都会失效**。302 是临时重定向，每次都会回到服务端。

### 为什么统计单独一张表

若在主表加 `click_count` 字段，每次点击都要 `UPDATE` 同一行，高并发下会产生**行锁竞争**。统计独立成表可按天汇总、保留历史，也能批量写入。

## 项目结构

```
src/main/java/com/shortlink/
├── ShortlinkApplication.java   启动类（@MapperScan）
├── controller/
│   └── LinkController.java     创建 / 跳转接口
├── entity/
│   └── ShortLink.java          短链实体
├── mapper/
│   └── LinkMapper.java         MyBatis Mapper（注解式 SQL）
└── util/
    └── Base62.java             62 进制编码

src/main/resources/application.yml
schema.sql                      建库建表脚本
```

## 后续计划

- Redis 缓存短码 → 长链，防缓存穿透
- 点击统计异步化
- 过期数据定时清理
- Docker 化 + Nginx 反代，部署到公网

## License

MIT
