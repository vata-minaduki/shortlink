# 部署流程 SOP · 短链接服务

> 环境：腾讯云轻量应用服务器 · Ubuntu 24.04 · JDK 17 · MySQL 8
> 目标：把本地 Spring Boot 项目变成一个公网可访问的服务

---

## 总览：五个阶段

| 阶段 | 做什么 | 频率 |
|---|---|---|
| **一、准备服务器** | 购买、开端口、SSH | 一次性 |
| **二、装运行环境** | JDK、MySQL | 一次性 |
| **三、配置应用** | 环境变量、建库建表 | 一次性 |
| **四、部署启动** | 上传 jar、启动、验证 | 一次性 |
| **五、日常更新** | 重新打包、上传、重启 | 每次发版 |

**关键认知**：一二三是“把机器准备好”，四五是“把程序放上去”。

---

## 一、准备服务器（一次性）

### 1.1 购买

| 项 | 选择 |
|---|---|
| 产品 | 轻量应用服务器 2核2G |
| 地域 | 离自己近的 |
| 镜像 | **Ubuntu 24.04 LTS** |
| 时长 | 1 年 |

### 1.2 开放端口（关键，最容易忘）

控制台 → 实例 → **防火墙**（轻量）或 **安全组**（ECS）

| 协议 | 端口 | 来源 |
|---|---|---|
| TCP | **8080** | 0.0.0.0/0 |

**不开这一步，外网永远连不上，且报错只是“连接超时”，没有任何提示。**

### 1.3 连接服务器

```bash
ssh ubuntu@服务器公网IP
sudo -i          # 切成 root，后面所有操作都在 root 下
```

**注意**：Ubuntu 云镜像默认用户是 `ubuntu`，不是 `root`。

---

## 二、装运行环境（一次性）

### 2.1 更新软件源

```bash
apt update
```

### 2.2 装 JDK 17

```bash
apt install -y openjdk-17-jre-headless
java -version      # 确认 17.x
```

> 用 `jre-headless` 而不是完整 JDK：不需要编译，只要能跑，体积小一半。

### 2.3 装 MySQL

```bash
apt install -y mysql-server
systemctl status mysql     # 确认 active (running)
```

### 2.4 建库 + 建专用用户

```bash
mysql
```

```sql
CREATE DATABASE IF NOT EXISTS shortlink DEFAULT CHARSET utf8mb4;
CREATE USER IF NOT EXISTS 'shortlink'@'localhost' IDENTIFIED BY '你的密码';
GRANT ALL PRIVILEGES ON shortlink.* TO 'shortlink'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

**为什么要专用用户**：

- Ubuntu 上 MySQL 的 `root` 用 `auth_socket` 认证（只允许本机 socket，**JDBC 连不上**）
- 应用用 `shortlink` 用户，权限只到 `shortlink.*`，**即使泄露也动不了别的库**

---

## 三、配置应用（一次性）

### 3.1 上传文件

本地：

```powershell
cd D:\Desktop\java\Project\shortlink
scp target\shortlink-0.0.1-SNAPSHOT.jar ubuntu@服务器IP:/home/ubuntu/
scp schema.sql ubuntu@服务器IP:/home/ubuntu/
```

### 3.2 建表

服务器上：

```bash
cd /home/ubuntu
mysql < schema.sql
mysql -e "USE shortlink; DESC short_link;"   # 确认列名是下划线风格
```

### 3.3 配置环境变量（账号密码）

**不写在脚本里，也不提交到 Git**：

```bash
cat > /home/ubuntu/app.env <<'EOF'
SPRING_DATASOURCE_USERNAME=shortlink
SPRING_DATASOURCE_PASSWORD=你的密码
EOF
chmod 600 /home/ubuntu/app.env
```

**原理**：`application.yml` 里写的是

```yaml
username: root
password: ${MYSQL_PASSWORD}
```

部署时用环境变量**覆盖**：

| 环境变量 | 覆盖的配置项 |
|---|---|
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` |

这叫 **relaxed binding**（Spring Boot 把大写下划线自动映射成小写点号）。

---

## 四、部署启动（一次性）

### 4.1 启动脚本 manage.sh

```bash
cat > /home/ubuntu/manage.sh <<'EOF'
#!/bin/bash
APP_HOME=/home/ubuntu
APP_JAR="$APP_HOME/shortlink-0.0.1-SNAPSHOT.jar"
APP_LOG="$APP_HOME/app.log"
APP_ENV="$APP_HOME/app.env"
PAT="shortlink-0.0.1-SNAPSHOT.jar"
run() { pgrep -f "$PAT" >/dev/null 2>&1; }
case "$1" in
  start)
    run && { echo "already running"; exit 0; }
    [ -f "$APP_ENV" ] && { set -a; . "$APP_ENV"; set +a; }
    nohup java -jar "$APP_JAR" > "$APP_LOG" 2>&1 &
    echo "started pid=$!"
    ;;
  stop)
    run || { echo "not running"; exit 0; }
    kill $(pgrep -f "$PAT"); echo "stopping"
    ;;
  restart)
    "$0" stop || true
    sleep 2
    "$0" start
    ;;
  status)
    run && echo "running pid=$(pgrep -f "$PAT")" || echo "not running"
    ;;
  *) echo "usage: $0 {start|stop|restart|status}" ;;
esac
EOF
chmod +x /home/ubuntu/manage.sh
```

**三个设计点**：

| 设计 | 原因 |
|---|---|
| `nohup ... &` | 断开 SSH 后进程继续跑 |
| `> app.log 2>&1` | 日志落文件，方便排查 |
| `kill`（不是 `kill -9`） | SIGTERM 触发优雅停机，关闭连接池、执行 `@PreDestroy` |

### 4.2 启动

```bash
cd /home/ubuntu
./manage.sh start
sleep 12
tail -40 app.log
```

**成功的标志**：

```
HikariPool-1 - Start completed.
Adding welcome page: class path resource [static/index.html]
Tomcat started on port(s): 8080 (http)
```

### 4.3 验证清单（五步，全过才算成功）

| # | 检查 | 期望 |
|---|---|---|
| 1 | `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/` | `200` |
| 2 | 浏览器打开 `http://公网IP:8080/` | 输入框页面 |
| 3 | 页面上生成一条短链 | 返回短链接 |
| 4 | 点短链接 | 302 跳转到原网址 |
| 5 | `http://公网IP:8080/swagger-ui/index.html` | 接口列表 |

---

## 五、日常更新（每次发版）

```powershell
# 本地
cd D:\Desktop\java\Project\shortlink
.\mvnw.cmd clean package -DskipTests
scp target\shortlink-0.0.1-SNAPSHOT.jar ubuntu@服务器IP:/home/ubuntu/
```

```bash
# 服务器
cd /home/ubuntu
./manage.sh restart
tail -f app.log
```

**注意**：改了静态资源（`index.html`）**必须重新打包**——jar 里的资源是打包时的快照。

---

## 六、排查手册

| 现象 | 原因 | 解法 |
|---|---|---|
| 浏览器连不上，无报错 | 防火墙/安全组没开 8080 | 控制台加规则 |
| 启动报 `Access denied for user 'root'` | root 是 socket 认证，JDBC 连不上 | 改用 `shortlink` 用户 |
| `GET /` 返回 404，`/index.html` 也 404 | `@GetMapping("/{code}")` 抢了单段路径 | 加正则：`/{code:[0-9a-zA-Z]{1,10}}` |
| 首页 404，但 jar 里明明有 | 用的旧 jar（改完没重新打包） | `mvn clean package` |
| `pkill` 报 `Operation not permitted` | 当前是 `ubuntu` 用户，进程属于 `root` | `sudo -i` |
| `./manage.sh: No such file` | 当前目录是 `/root`，脚本在 `/home/ubuntu` | `cd /home/ubuntu` |
| `scp` 传一半 `Connection reset` | 网络波动 | 重试 / 网页终端上传 / 用 heredoc 直接写文件 |
| `./start.sh: command not found` | Linux 当前目录不在 PATH | `./start.sh` |

---

## 七、面试怎么讲这段

> “我把它部署在腾讯云轻量服务器上，Ubuntu 24.04。
>
> 服务器上装了 JDK 17 和 MySQL 8，数据库建了专用账号，权限只到本库。
>
> 应用打包成可执行 jar，用 `nohup` 后台运行，日志落文件；启停用 shell 脚本封装，`stop` 走 SIGTERM 保证优雅停机。
>
> 数据库密码通过环境变量注入（Spring Boot 的 relaxed binding），不写进代码、不进 Git。
>
> 上线后踩过一个坑：跳转接口 `@GetMapping("/{code}")` 会捕获所有单段路径，把 `index.html` 也吃掉了，后来加了正则约束 `/{code:[0-9a-zA-Z]{1,10}}` 才解决。”

**这段说完，面试官会知道你不是“跑过教程”，是真部署过。**

---

## 附：常用命令速查

| 场景 | 命令 |
|---|---|
| 启动 | `cd /home/ubuntu && ./manage.sh start` |
| 停止 | `./manage.sh stop` |
| 重启 | `./manage.sh restart` |
| 状态 | `./manage.sh status` |
| 看日志 | `tail -f /home/ubuntu/app.log` |
| 看进程 | `ps -ef \| grep shortlink \| grep -v grep` |
| 看端口 | `ss -lntp \| grep 8080` |
| 查内存 | `free -h` |
| 进 MySQL | `mysql -u shortlink -p` |
