# 多阶段构建 - 构建阶段
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# 复制 pom.xml 和依赖
COPY backend/pom.xml ./

# 下载依赖
RUN mvn dependency:go-offline -B

# 复制源代码
COPY backend/src ./src

# 构建应用
RUN mvn clean package -DskipTests -B

# 生产阶段
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 安装必要的工具
RUN apk add --no-cache curl

# 复制构建产物
COPY --from=builder /app/target/*.jar app.jar

# 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# 暴露端口
EXPOSE 8080

# JVM 参数优化
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
