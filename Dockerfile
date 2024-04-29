FROM maven:3.9.6-eclipse-temurin-22 as build

ADD . /build
WORKDIR /build
RUN sh setup-webui.sh && mvn -B clean package --file pom.xml

FROM ubuntu/jre:17_edge
LABEL MAINTAINER="https://github.com/PBH-BTN/PeerBanHelper"

ENV TZ=UTC
WORKDIR /app
COPY --from=build build/target/PeerBanHelper.jar /app/PeerBanHelper.jar
ENV PATH "${JAVA_HOME}/bin:${PATH}"
CMD ["java","-Xmx256M","-XX:+UseSerialGC","-jar","PeerBanHelper.jar"]