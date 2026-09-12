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

# 编码设置很关键：容器默认 locale 常为 POSIX/ASCII，
# 会导致 JVM 输出（日志）里的中文变成 "?"，也影响读写的字符集
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8" \
    SERVER_PORT=8080 \
    UPLOAD_DIR=/app/data/uploads \
    JAVA_OPTS=""

EXPOSE 8080
VOLUME ["/app/data"]

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
