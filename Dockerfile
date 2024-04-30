FROM maven:3.9.6-eclipse-temurin-22 as build

ADD . /build
WORKDIR /build
RUN sh setup-webui.sh && mvn -B clean package -Dmaven.compiler.release=21 --file pom.xml

FROM alpine:edge
LABEL MAINTAINER="https://github.com/PBH-BTN/PeerBanHelper"

ENV TZ=UTC
WORKDIR /app
RUN apk add --no-cache openjdk21-jre-headless
COPY --from=build build/target/PeerBanHelper.jar /app/PeerBanHelper.jar
ENTRYPOINT ["java","-Xmx256M","-XX:+UseSerialGC","-jar","PeerBanHelper.jar"]