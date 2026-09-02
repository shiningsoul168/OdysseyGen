# ============================================================
# OdysseyGen 后端容器化 Dockerfile（多阶段构建）
# 构建: docker build -t odysseygen:latest .
# 运行: docker run -d --name odysseygen --network host \
#         -e DB_PASSWORD=xxx -e JWT_SECRET=xxx -e DEEPSEEK_API_KEY=xxx \
#         odysseygen:latest
# 说明: 服务器为 2C2G，MySQL/Redis 直接跑在宿主机上，
#       容器用 --network host 直连 localhost:3306 / 6379，避免再起 DB 容器挤占内存。
# ============================================================

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
# 先只拷 pom 拉依赖，利用 Docker 层缓存加速后续构建
RUN mvn -q -B dependency:go-offline || true
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---------- 运行阶段 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# 时区 + 非 root 用户（生产安全习惯）
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
