FROM --platform=$BUILDPLATFORM node:current-alpine AS build_web
ARG GIT_HASH
COPY webui /webui
WORKDIR /webui
RUN npm i -g pnpm && CI=1 pnpm i
RUN pnpm run build

FROM --platform=$BUILDPLATFORM docker.io/maven:3.9.11-eclipse-temurin-25-alpine AS dependency-cache
WORKDIR /app
COPY pom.xml .
COPY m2-local-repo /app/m2-local-repo
RUN mvn -B dependency:go-offline

FROM --platform=$BUILDPLATFORM docker.io/maven:3.9.11-eclipse-temurin-25-alpine AS build
RUN apk add git
COPY --from=dependency-cache /root/.m2 /root/.m2
COPY . /build
WORKDIR /build
COPY --from=build_web webui/dist src/main/resources/static
RUN mvn -B clean package --file pom.xml -T 1.5C -P thin-sqlite-packaging

FROM docker.io/bellsoft/liberica-runtime-container:jre-25-slim-musl
LABEL maintainer="https://github.com/PBH-BTN/PeerBanHelper"
USER 0
EXPOSE 9898
ENV TZ=UTC
ENV JAVA_OPTS="-Djdk.attach.allowAttachSelf=true -Dsun.net.useExclusiveBind=false -Dpbh.release=docker -Djava.awt.headless=true -XX:+UseZGC -XX:+ZGenerational -XX:SoftMaxHeapSize=386M -XX:ZUncommitDelay=1 -Xss512k -XX:+UseStringDeduplication -XX:-ShrinkHeapInSteps -XX:MaxRAMPercentage=85.0"
WORKDIR /app
VOLUME /tmp
COPY --from=build build/target/libraries /app/libraries
COPY --from=build build/target/PeerBanHelper.jar /app/PeerBanHelper.jar
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENTRYPOINT ["sh", "-c", "${JAVA_HOME}/bin/java ${JAVA_OPTS} -jar PeerBanHelper.jar"]
