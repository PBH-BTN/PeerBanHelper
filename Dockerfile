FROM maven:3.9.6-eclipse-temurin-22 as build

ADD . /build
WORKDIR /build
RUN sh setup-webui.sh && mvn -B clean package --file pom.xml && \
    $JAVA_HOME/bin/jlink \
         --add-modules java.base \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

FROM debian:stable-slim
LABEL MAINTAINER="https://github.com/PBH-BTN/PeerBanHelper"

ENV TZ=UTC
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH "${JAVA_HOME}/bin:${PATH}"
WORKDIR /app
COPY --from=build build/target/PeerBanHelper.jar /app/PeerBanHelper.jar
COPY --from=build /javaruntime $JAVA_HOME

CMD ["java","-Xmx256M","-XX:+UseSerialGC","-jar","PeerBanHelper.jar"]