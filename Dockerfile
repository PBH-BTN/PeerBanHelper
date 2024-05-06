FROM maven:3.9.6-eclipse-temurin-17 as build

ADD . /build
WORKDIR /build
RUN sh setup-webui.sh && mvn -B clean package --file pom.xml

FROM eclipse-temurin:17.0.10_7-jre
LABEL MAINTAINER="https://github.com/PBH-BTN/PeerBanHelper"
USER 0
ENV TZ=UTC
WORKDIR /app
VOLUME /tmp
COPY --from=build build/target/PeerBanHelper.jar /app/PeerBanHelper.jar
ENV PATH "${JAVA_HOME}/bin:${PATH}"
ENTRYPOINT ["java","-Xmx256M","-XX:+UseSerialGC","-jar","PeerBanHelper.jar"]
