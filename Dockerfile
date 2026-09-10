# ---------- 构建阶段：项目内不需要预装 Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 先只拷 pom 拉依赖，利用 Docker 层缓存加速后续构建
COPY pom.xml .
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ---------- 运行阶段：仅 JRE，镜像更小 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

ENV SERVER_PORT=8080 \
    UPLOAD_DIR=/app/data/uploads \
    JAVA_OPTS=""

EXPOSE 8080
VOLUME ["/app/data"]

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
