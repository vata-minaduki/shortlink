# 部署流程 SOP · 短链接服务

> 环境：腾讯云轻量应用服务器（2核2G）· Ubuntu 24.04 · JDK 17 · MySQL 8
>
> 本文档假设：已经有一台可 SSH 登录的 Linux 服务器，且能在控制台放行端口。

---

## 总览

| 阶段 | 做什么 | 频率 |
|---|---|---|
| 一、准备 | 放行端口、SSH 登录 | 一次性 |
| 二、装环境 | JDK、MySQL、建库建用户 | 一次性 |
| 三、配应用 | 环境变量、上传、建表 | 一次性 |
| 四、启动 | 启停脚本、启动、验证 | 一次性 |
| 五、日常更新 | 重新打包、上传、重启 | 每次发版 |

---

## 一、准备

### 1.1 放行端口（最容易忘）

控制台 → 实例 → **防火墙**（轻量）或 **安全组**（ECS），入方向添加：

| 协议 | 端口 | 来源 |
|---|---|---|
| TCP | 8080 | 0.0.0.0/0 |

**不开这一步，外网只会“连接超时”，没有任何报错提示。**

### 1.2 登录

```bash
ssh ubuntu@服务器IP
sudo -i          # 后续操作都在 root 下
```

> Ubuntu 云镜像默认用户是 `ubuntu`；不切 root 会导致后面 `pkill` 报 `Operation not permitted`。

---

## 二、装运行环境

```bash
apt update
apt install -y openjdk-17-jre-headless
apt install -y mysql-server
java -version                    # 17.x
systemctl status mysql           # active (running)
```

> 用 `jre-headless`：不需要编译，只要能运行，体积小一半。

### 建库 + 建专用用户

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

**为什么不用 root**：Ubuntu 上 MySQL 的 `root` 走 `auth_socket` 认证，**JDBC 连不上**；专用用户权限只到 `shortlink.*`，泄露也影响不到别的库。

---

## 三、配置应用

### 3.1 上传

```powershell
cd D:\Desktop\java\Project\shortlink
scp target\shortlink-0.0.1-SNAPSHOT.jar ubuntu@服务器IP:/home/ubuntu/
scp schema.sql ubuntu@服务器IP:/home/ubuntu/
```

### 3.2 建表

```bash
cd /home/ubuntu
mysql < schema.sql
mysql -e "USE shortlink; DESC short_link;"    # 确认列名是下划线风格
```

### 3.3 环境变量（账号密码）

```bash
cat > /home/ubuntu/app.env <<'EOF'
SPRING_DATASOURCE_USERNAME=shortlink
SPRING_DATASOURCE_PASSWORD=你的密码
EOF
chmod 600 /home/ubuntu/app.env
```

**原理**：`application.yml` 里写的是 `username: root` / `password: ${MYSQL_PASSWORD}`，部署时用环境变量**覆盖**：

| 环境变量 | 覆盖的配置项 |
|---|---|
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` |

这叫 **relaxed binding**（大写下划线自动映射成小写点号）。好处：密码不进代码、不进 Git。

---

## 四、启动

### 4.1 启停脚本

`deploy/manage.sh`（仓库内），核心逻辑：

```bash
start)  nohup java -jar "$APP_JAR" > "$APP_LOG" 2>&1 &   # 后台 + 日志落文件
stop)   kill $(pgrep -f "$PAT")                          # SIGTERM，优雅停机
```

| 设计 | 原因 |
|---|---|
| `nohup ... &` | 断开 SSH 后进程继续运行 |
| `> app.log 2>&1` | 日志落文件，方便排查 |
| `kill`（非 `-9`） | SIGTERM 触发优雅停机，关闭连接池、执行 `@PreDestroy` |

### 4.2 启动

```bash
cd /home/ubuntu
./manage.sh start
sleep 12
tail -40 app.log
```

成功标志：

```
HikariPool-1 - Start completed.
Adding welcome page: class path resource [static/index.html]
Tomcat started on port(s): 8080 (http)
```

### 4.3 验证清单

| # | 检查 | 期望 |
|---|---|---|
| 1 | `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/` | `200` |
| 2 | `http://公网IP:8080/` | 首页 |
| 3 | 页面生成一条短链 | 返回短链 |
| 4 | 点短链 | 302 跳转 |
| 5 | `http://公网IP:8080/swagger-ui/index.html` | 接口列表 |

---

## 五、日常更新

```powershell
# 本地
.\mvnw.cmd clean package -DskipTests
scp target\shortlink-0.0.1-SNAPSHOT.jar ubuntu@服务器IP:/home/ubuntu/
```

```bash
# 服务器
cd /home/ubuntu && ./manage.sh restart
```

**改了 `static/` 下的资源必须重新打包**——jar 里的资源是打包时的快照。

---

## 六、排查手册

| 现象 | 原因 | 解法 |
|---|---|---|
| 外网连不上，无报错 | 防火墙/安全组没开 8080 | 控制台加规则 |
| `Access denied for user 'root'` | root 是 socket 认证 | 改用 `shortlink` 用户 |
| `/` 和 `/index.html` 都 404 | `@GetMapping("/{code}")` 抢了单段路径 | 加正则 `/{code:[0-9a-zA-Z]{1,10}}` |
| 首页 404 但 jar 里有 | 用的旧 jar | 重新 `mvn package` |
| `pkill` 报 Operation not permitted | `ubuntu` 用户杀不掉 `root` 进程 | `sudo -i` |
| `./manage.sh: No such file` | 当前在 `/root` | `cd /home/ubuntu` |
| `scp` 传一半 Connection reset | 网络波动 | 重试 / heredoc 直接写文件 |
| `./start.sh: command not found` | Linux 当前目录不在 PATH | `./start.sh` |

---

## 附：常用命令

| 场景 | 命令 |
|---|---|
| 启停 | `cd /home/ubuntu && ./manage.sh {start\|stop\|restart\|status}` |
| 看日志 | `tail -f /home/ubuntu/app.log` |
| 看端口 | `ss -lntp \| grep 8080` |
| 查内存 | `free -h` |
| 进 MySQL | `mysql -u shortlink -p` |