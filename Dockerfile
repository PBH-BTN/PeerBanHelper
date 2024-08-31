# 构建前端
FROM --platform=$BUILDPLATFORM docker.io/node:alpine AS frontend-build
COPY ./webui /build/webui
WORKDIR /build/webui
RUN corepack enable pnpm && \
    pnpm i && \
    pnpm run build

# 下载依赖
FROM maven:3-eclipse-temurin-21-alpine as backend-build
ADD pom.xml /build/pom.xml
WORKDIR /build
# fetch all dependencies
RUN mvn dependency:go-offline -B -T 1.5C -Daether.dependencyCollector.impl=bf -Dmaven.artifact.threads=32

# 构建后端
COPY . /build
WORKDIR /build
# 把前端打包好的文件拉来
COPY --from=frontend-build /build/webui/dist src/main/resources/static
RUN apk add --update curl git && \
    mvn -B clean package --file pom.xml -T 1.5C -Daether.dependencyCollector.impl=bf -Dmaven.artifact.threads=32

# 最终阶段,只要成品
FROM docker.io/azul/zulu-openjdk-alpine:21.0.4-21.36-jre
LABEL maintainer="https://github.com/PBH-BTN/PeerBanHelper"
USER 0
ENV TZ=UTC
WORKDIR /app
VOLUME /tmp
COPY --from=backend-build build/target/PeerBanHelper.jar /app/PeerBanHelper.jar
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENTRYPOINT ["java","-Xmx386M","-XX:+UseG1GC", "-XX:+UseStringDeduplication","-XX:+ShrinkHeapInSteps","-jar","PeerBanHelper.jar"]
