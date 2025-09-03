FROM --platform=$BUILDPLATFORM node:current-alpine as build_web
ARG GIT_HASH
COPY webui /webui
WORKDIR /webui
RUN corepack enable pnpm && CI=1 pnpm i && pnpm run build

FROM --platform=$BUILDPLATFORM docker.io/maven:3.9.10-eclipse-temurin-21-alpine AS build
COPY . /build
WORKDIR /build
COPY --from=build_web webui/dist src/main/resources/static
RUN apk add git  && \
    mvn -B clean package --file pom.xml -T 1.5C -P thin-sqlite-packaging

FROM docker.io/bellsoft/liberica-runtime-container:jre-21-slim-musl
LABEL maintainer="https://github.com/PBH-BTN/PeerBanHelper"
USER 0
EXPOSE 9898
ENV TZ=UTC
ENV JAVA_OPTS="-Djdk.attach.allowAttachSelf=true -Dsun.net.useExclusiveBind=false -Dpbh.release=docker -Djava.awt.headless=true -XX:+UseZGC -XX:+ZGenerational -XX:SoftMaxHeapSize=386M -XX:ZUncommitDelay=1 -Xss512k -XX:+UseStringDeduplication -XX:+ShrinkHeapInSteps"
WORKDIR /app
VOLUME /tmp
COPY --from=build build/target/libraries /app/libraries
COPY --from=build build/target/PeerBanHelper.jar /app/PeerBanHelper.jar
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENTRYPOINT ["sh", "-c", "${JAVA_HOME}/bin/java ${JAVA_OPTS} -jar PeerBanHelper.jar"]