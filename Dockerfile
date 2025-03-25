FROM --platform=$BUILDPLATFORM node:current-alpine as build_web
COPY webui /webui
WORKDIR /webui
RUN corepack enable pnpm && CI=1 pnpm i && pnpm run build

FROM --platform=$BUILDPLATFORM docker.io/maven:3.9.9-eclipse-temurin-21-alpine AS build
COPY . /build
WORKDIR /build
COPY --from=build_web webui/dist src/main/resources/static
RUN apk add git  && \
    mvn -B clean package --file pom.xml -T 1.5C -P thin-sqlite-packaging

FROM docker.io/bellsoft/liberica-openjdk-alpine-musl:24
LABEL maintainer="https://github.com/PBH-BTN/PeerBanHelper"
USER 0
EXPOSE 9898
ENV TZ=UTC
ENV JAVA_OPTS="-Dpbh.release=docker -Djava.awt.headless=true -Xmx512M -Xms16M -Xss512k --enable-native-access=ALL-UNNAMED -XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+ShrinkHeapInSteps"
WORKDIR /app
VOLUME /tmp
COPY --from=build build/target/libraries /app/libraries
COPY --from=build build/target/PeerBanHelper.jar /app/PeerBanHelper.jar
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENTRYPOINT ["sh", "-c", "${JAVA_HOME}/bin/java ${JAVA_OPTS} -jar PeerBanHelper.jar"]