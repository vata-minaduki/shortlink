#!/bin/bash
# 短链接服务管理脚本：start | stop | restart | status
APP_HOME=/home/ubuntu
APP_JAR="$APP_HOME/shortlink-0.0.1-SNAPSHOT.jar"
APP_LOG="$APP_HOME/app.log"
APP_ENV="$APP_HOME/app.env"
PAT="shortlink-0.0.1-SNAPSHOT.jar"

run() { pgrep -f "$PAT" >/dev/null 2>&1; }

case "$1" in
  start)
    run && { echo "already running pid=$(pgrep -f "$PAT")"; exit 0; }
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
  *)
    echo "usage: $0 {start|stop|restart|status}"
    ;;
esac