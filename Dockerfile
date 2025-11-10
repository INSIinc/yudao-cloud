# ====================================
# 多阶段构建 Dockerfile for yudao-cloud
# ====================================

# ====================================
# 第一阶段：Maven 构建阶段
# ====================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

# 设置工作目录
WORKDIR /build

# 设置 Maven 镜像加速（使用阿里云镜像）
RUN mkdir -p /root/.m2 && \
    cat > /root/.m2/settings.xml <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven Mirror</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
EOF

# 复制所有源代码（简化构建流程，避免复杂的 pom.xml 依赖问题）
COPY . .

# 执行 Maven 打包（跳过测试）
RUN mvn clean package -DskipTests -pl yudao-server -am

# ====================================
# 第二阶段：运行时阶段
# ====================================
FROM eclipse-temurin:17-jre-alpine-3.21

# 设置标签
LABEL maintainer="yudao-cloud" \
      version="2025.10-SNAPSHOT" \
      description="Yudao Cloud Application"

# 安装必要的工具和时区数据
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 创建应用目录和用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

# 从构建阶段复制 jar 包
COPY --from=builder /build/yudao-server/target/yudao-server.jar /app/app.jar

# 创建日志目录和文件目录，并设置权限
RUN mkdir -p /app/logs /app/files && \
    chown -R appuser:appgroup /app

# 切换到非 root 用户
USER appuser

# 暴露端口（默认 48080）
EXPOSE 48080

# 设置环境变量
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200" \
    SPRING_PROFILES_ACTIVE=prod

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:48080/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar /app/app.jar"]
