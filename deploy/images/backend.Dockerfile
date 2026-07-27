# Maven Wrapper 固定 Maven 版本；基础镜像同时以版本号和摘要锁定 JDK 26.0.2。
FROM amazoncorretto:26.0.2-al2023-headless@sha256:4ac0a394ad94cd1e25a0481e72a85451728faf7370fe053e13040d6629869463 AS builder

ARG RUNTIME
WORKDIR /workspace

RUN dnf install --assumeyes gzip tar \
    && dnf clean all \
    && rm -rf /var/cache/dnf

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY backend backend

# 运行入口使用白名单，避免构建参数被解释成任意 Maven 路径。
RUN case "${RUNTIME}" in \
      control-plane) BOOTSTRAP=control-plane-bootstrap ;; \
      query-runtime) BOOTSTRAP=query-bootstrap ;; \
      ingestion-worker) BOOTSTRAP=ingestion-bootstrap ;; \
      *) echo "不支持的运行入口: ${RUNTIME}" >&2; exit 1 ;; \
    esac \
    && ./mvnw -q -pl "backend/bootstrap/${BOOTSTRAP}" -am package -DskipTests \
    && cp "backend/bootstrap/${BOOTSTRAP}/target/${BOOTSTRAP}-0.1.0-SNAPSHOT.jar" /workspace/application.jar

# 运行阶段不包含 Maven 与源码。后续通过 jlink 工作包进一步裁剪精确运行时。
FROM amazoncorretto:26.0.2-al2023-headless@sha256:4ac0a394ad94cd1e25a0481e72a85451728faf7370fe053e13040d6629869463

ARG RUNTIME
RUN mkdir --parents /opt/foundation \
    && chown 10001:10001 /opt/foundation

WORKDIR /opt/foundation
COPY --from=builder --chown=10001:10001 /workspace/application.jar application.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8" \
    SERVER_PORT="8080"

USER 10001:10001

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD curl --fail --silent "http://127.0.0.1:${SERVER_PORT}/actuator/health/readiness" >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/opt/foundation/application.jar"]
