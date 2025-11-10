#!/bin/bash

#####################################################
# 本地启动生产环境配置脚本
# 用于在本地测试生产环境配置
#####################################################

echo "================================"
echo "启动 yudao-server (生产环境配置)"
echo "================================"

# ==================== 加载环境变量 ====================
cd "$(dirname "$0")"

if [ -f .env.prod.local ]; then
    echo "正在加载配置文件: .env.prod.local"
    export $(grep -v '^#' .env.prod.local | xargs)
else
    echo "警告: 未找到 .env.prod.local 文件，使用默认配置"
    # 默认配置
    export DB_HOST=127.0.0.1
    export DB_PORT=5433
    export DB_NAME=app
    export DB_USERNAME=postgres
    export DB_PASSWORD=123456
    export REDIS_HOST=127.0.0.1
    export REDIS_PORT=6379
    export REDIS_DATABASE=0
    export REDIS_PASSWORD=
fi

# ==================== Spring Profile ====================
export SPRING_PROFILES_ACTIVE=prod

# ==================== JVM 参数 ====================
export JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

echo ""
echo "当前配置："
echo "  - Profile: ${SPRING_PROFILES_ACTIVE}"
echo "  - 数据库: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "  - Redis: ${REDIS_HOST}:${REDIS_PORT}/${REDIS_DATABASE}"
echo ""

# ==================== 启动应用 ====================
#echo "使用 Maven 启动应用..."
#mvn spring-boot:run -Dspring-boot.run.profiles=${SPRING_PROFILES_ACTIVE}

# 如果需要使用 JAR 包启动，请先执行: mvn clean package -DskipTests
# 然后取消注释下面两行：
echo "使用 JAR 包启动应用..."
java ${JAVA_OPTS} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar target/yudao-server.jar
