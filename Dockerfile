FROM eclipse-temurin:22 as build

ADD ./pom.xml pom.xml
ADD ./src src/
ADD ./setup-webui.sh setup-webui.sh
RUN apt-get update && apt-get install -y maven git && \
    sh setup-webui.sh && mvn -B clean package --file pom.xml && \
    RUN $JAVA_HOME/bin/jlink \
         --add-modules java.base \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

FROM debian:stable-slim
LABEL MAINTAINER="https://github.com/PBH-BTN/PeerBanHelper"

ENV TZ=UTC
WORKDIR /app
COPY --from=build target/PeerBanHelper.jar /app/PeerBanHelper.jar

CMD ["java","-Xmx256M","-XX:+UseSerialGC","-jar","PeerBanHelper.jar"]