FROM --platform=$BUILDPLATFORM node:24-alpine AS build_web
ARG GIT_HASH
COPY webui /webui
WORKDIR /webui
RUN npm i -g pnpm && CI=1 pnpm i
RUN pnpm run build

FROM --platform=$BUILDPLATFORM eclipse-temurin:25.0.2_10-jdk-alpine AS build
RUN apk add git
COPY . /build
WORKDIR /build
COPY --from=build_web webui/dist src/main/resources/static
RUN chmod +x ./gradlew && ./gradlew clean build --no-daemon

FROM docker.io/bellsoft/liberica-runtime-container:jdk-all-25-musl
LABEL maintainer="https://github.com/PBH-BTN/PeerBanHelper"
USER 0
EXPOSE 9898
ENV TZ=UTC
WORKDIR /app
VOLUME /tmp
COPY --from=build build/build/libraries /app/libraries
COPY --from=build build/build/libs/PeerBanHelper.jar /app/PeerBanHelper.jar
ENV PATH="/usr/lib/jvm/liberica25-container-jre/bin:${PATH}"
ENTRYPOINT ["sh", "-c", "java -XX:+UseCompactObjectHeaders -XX:SoftMaxHeapSize=386M --enable-native-access=ALL-UNNAMED -Djdk.attach.allowAttachSelf=true -Dsun.net.useExclusiveBind=false -Dpbh.release=docker -Djava.awt.headless=true -XX:+UseZGC -XX:ZUncommitDelay=1 -Xss512k -XX:+UseStringDeduplication -XX:-ShrinkHeapInSteps -XX:MaxRAMPercentage=85.0 -jar PeerBanHelper.jar"]
