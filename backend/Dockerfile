FROM public.ecr.aws/docker/library/maven:3.9.16-eclipse-temurin-21-alpine@sha256:d88e5b38297858f65f97bc7e7964c760ab988fd18ace41589176f1468c49a489 AS build

ARG ROCKETMQ_LOGGING_COMMIT=c14642b14f6ee66eb610ab80f07acca391df3fd3
WORKDIR /rocketmq-logging
ADD --checksum=sha256:23c229775bd032ad02869116f550735859441cc5e63f1525cc512f3dff139b30 \
  https://codeload.github.com/aliyunmq/rocketmq-logging/tar.gz/c14642b14f6ee66eb610ab80f07acca391df3fd3 /tmp/rocketmq-logging.tar.gz
RUN tar -xzf /tmp/rocketmq-logging.tar.gz --strip-components=1 -C /rocketmq-logging \
    && mvn -B -ntp -DskipTests -Dmaven.javadoc.skip=true \
      -Dlogback-classic.version=1.3.16 install

WORKDIR /workspace
COPY . .
RUN mvn -pl platform-boot -am package -DskipTests

FROM public.ecr.aws/docker/library/eclipse-temurin:21.0.11_10-jre-noble@sha256:373787d1d45a87f084fda43e7de0e9acf5eedee049446efac738f13587ec4c64
ARG AIP_SOURCE_REVISION=unknown
LABEL org.opencontainers.image.revision="${AIP_SOURCE_REVISION}"
WORKDIR /app
RUN groupadd --system --gid 10001 aiplatform \
    && useradd --system --uid 10001 --gid 10001 --no-create-home \
      --home-dir /nonexistent --shell /usr/sbin/nologin aiplatform \
    && chown 10001:10001 /app
COPY --from=build --chown=10001:10001 /workspace/platform-boot/target/platform-boot-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
