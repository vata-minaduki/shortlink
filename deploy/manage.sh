#!/bin/bash
# 短链接服务管理脚本：start | stop | restart | status
# 用法：把 jar、app.env 和本脚本放在同一目录，执行 ./manage.sh start

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
APP_JAR="$APP_HOME/shortlink-0.0.1-SNAPSHOT.jar"
APP_LOG="$APP_HOME/app.log"
APP_ENV="$APP_HOME/app.env"
PAT="shortlink-0.0.1-SNAPSHOT.jar"

is_running() { pgrep -f "$PAT" >/dev/null 2>&1; }

case "$1" in
  start)
    if is_running; then echo "already running pid=$(pgrep -f "$PAT" | head -1)"; exit 0; fi
    if [ ! -f "$APP_JAR" ]; then echo "jar not found: $APP_JAR"; exit 1; fi
    if [ -f "$APP_ENV" ]; then set -a; . "$APP_ENV"; set +a; fi
    nohup java -jar "$APP_JAR" > "$APP_LOG" 2>&1 &
    echo "started pid=$!"
    echo "log: tail -f $APP_LOG"
    ;;
  stop)
    if ! is_running; then echo "not running"; exit 0; fi
    kill $(pgrep -f "$PAT")
    echo "stopping (SIGTERM)"
    ;;
  restart)
    "$0" stop || true
    sleep 2
    "$0" start
    ;;
  status)
    if is_running; then echo "running pid=$(pgrep -f "$PAT" | head -1)"; else echo "not running"; fi
    ;;
  *)
    echo "usage: $0 {start|stop|restart|status}"
    ;;
esac