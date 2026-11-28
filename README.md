# ShortLink · 短链接服务

一个基于 Spring Boot 的短链接服务：把长网址转成短码，访问短码时 302 跳转回原地址。

**在线演示**：http://43.139.120.181:8080/ ｜ [接口文档](http://43.139.120.181:8080/swagger-ui/index.html)

## 功能

- [x] 创建短链接：自增 ID + Base62 编码生成短码
- [x] 跳转：`GET /{code}` 返回 302（短码为 1~10 位字母或数字）
- [x] 查询短链详情
- [x] 禁用短链（软删除）
- [x] 点击统计：记录每次跳转，支持按天查询
- [x] 统一返回体与全局异常处理
- [x] 单元测试（Base62 编码）
- [ ] Redis 缓存（Cache-Aside）
- [ ] 过期时间与自动清理
- [ ] 开放重定向防护 / IP 限流
- [x] 部署到公网（腾讯云轻量应用服务器 + jar 直接运行）

## 技术栈

| 层 | 技术 |
|---|---|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.5 |
| Web | Spring MVC（内嵌 Tomcat） |
| 持久层 | MyBatis 3 |
| 数据库 | MySQL 8 |
| 连接池 | HikariCP |
| JSON | Jackson |
| 构建 | Maven |
| 测试 | JUnit 5 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8

### 1. 建库建表

在项目根目录执行：

```bash
# Linux / macOS / Git Bash
mysql -u root -p < schema.sql
```

```powershell
# Windows PowerShell（不支持 `<` 重定向，用管道）
Get-Content .\schema.sql | mysql -u root -p
```

### 2. 配置数据库密码

数据库密码不写入仓库，通过环境变量注入：

```bash
# Linux / macOS
export MYSQL_PASSWORD=你的密码
```

```powershell
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

| 方法 | 路径 | 作用 |
|---|---|---|
| `POST` | `/api/links` | 创建短链 |
| `GET` | `/{code}` | 跳转（302 重定向） |
| `GET` | `/api/links/{code}` | 查询短链详情 |
| `POST` | `/api/links/{code}/disable` | 禁用短链（软删除） |
| `GET` | `/api/links/{code}/stats` | 查询点击统计 |
| `GET` | `/api/links?page=&size=` | 分页查询短链列表 |
> 下方示例中的 `5` 是**短码（code）**，不是数据库 id。
> 由于短码由 id 经 Base62 编码而来，只有当 id 小于 62 时两者才相同（例如 id=62 时短码为 `10`）。

### 创建短链

```http
POST /api/links
Content-Type: application/json

{"longUrl": "https://www.example.com/very/long/path"}
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "code": "1",
    "shortUrl": "http://127.0.0.1:8080/1"
  }
}
```

### 查询详情

```http
GET /api/links/5
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 5,
    "code": "5",
    "longUrl": "https://www.example.com/very/long/path",
    "createdAt": "2026-11-26T12:30:00",
    "expireAt": null,
    "status": 1
  }
}
```

### 禁用短链

```http
POST /api/links/5/disable
```

响应：

```json
{"code": 0, "message": "ok", "data": null}
```

禁用为**软删除**：只把 `status` 置为 `0`，数据仍保留，跳转时返回 404。

### 跳转

```http
GET /{code}
```

- `{code}`：1~10 位字母或数字（`[0-9a-zA-Z]{1,10}`）
- 存在且有效：`302 Found`，`Location: 原长链接`
- 不存在 / 已禁用：`404 Not Found`

### 点击统计

```http
GET /api/links/5/stats
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "total": 3,
    "daily": [
      {"clickDate": "2026-11-26", "clickCount": 3}
    ]
  }
}
```

### 统一返回格式

所有 JSON 接口返回 `{code, message, data}`：

| 字段 | 说明 |
|---|---|
| `code` | 业务码：`0` 成功，`400` 参数错误，`404` 资源不存在，`500` 服务端错误 |
| `message` | 提示信息 |
| `data` | 业务数据，失败时为 `null` |

错误示例：

```json
{"code": 400, "message": "longUrl 不能为空", "data": null}
```

> 注：跳转接口 `GET /{code}` 不使用该格式——它返回 HTTP `302` + `Location` 头。

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

计数使用 `INSERT ... ON DUPLICATE KEY UPDATE` 一条语句原子累加，避免"先查再改"的并发问题。

## 项目结构

```
src/main/java/com/shortlink/
├── ShortlinkApplication.java       启动类（@SpringBootApplication + @MapperScan）
├── common/
│   ├── Result.java                 统一返回体
│   ├── BusinessException.java      业务异常
│   └── GlobalExceptionHandler.java 全局异常处理
├── controller/
│   └── LinkController.java         创建 / 跳转 / 详情 / 禁用 / 统计
├── entity/
│   ├── ShortLink.java              短链实体
│   └── DailyClick.java             单日点击统计
├── mapper/
│   └── LinkMapper.java             MyBatis Mapper（注解式 SQL）
└── util/
    └── Base62.java                 62 进制编码

src/main/resources/
├── application.yml
└── static/index.html             首页（输入长网址生成短链）

src/test/java/com/shortlink/util/Base62Test.java
docs/deployment-guide.md           部署 SOP
deploy/manage.sh                   服务器启停脚本
schema.sql
Dockerfile                         （容器化，未验证）
```

## 部署

已部署在腾讯云轻量应用服务器（Ubuntu 24.04），当前是 **jar 直接运行**：

```
http://43.139.120.181:8080/
```

服务器上用 `nohup` 后台运行，日志落到 `app.log`，启停由 `deploy/manage.sh` 封装：

```bash
./manage.sh start     # 启动
./manage.sh stop      # 停止（SIGTERM 优雅停机）
./manage.sh restart   # 重启
./manage.sh status    # 状态
```

数据库密码通过环境变量注入（`SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`），不写进代码、不进 Git。

完整的步骤与排错见 [部署流程 SOP](docs/deployment-guide.md)。

## 文档

- [部署流程 SOP](docs/deployment-guide.md)

## 后续计划

- Redis 缓存短码 → 长链，防缓存穿透
- 点击统计异步化（Redis 计数 + 定时落库）
- 过期数据定时清理
- 开放重定向防护（只允许 http/https）
- Docker 化 + Nginx 反代（当前为 jar 直接运行）

## License

MIT
