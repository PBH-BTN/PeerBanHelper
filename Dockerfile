FROM --platform=$BUILDPLATFORM docker.io/maven:3.9.6-eclipse-temurin-21 as build

COPY . /build
WORKDIR /build
RUN sh setup-webui.sh && mvn -B clean package --file pom.xml -T 1C

FROM eclipse-temurin:21.0.3_9-jre-alpine
LABEL maintainer="https://github.com/PBH-BTN/PeerBanHelper"
USER 0
ENV TZ=UTC
WORKDIR /app
VOLUME /tmp
COPY --from=build build/target/PeerBanHelper.jar /app/PeerBanHelper.jar
ENV PATH "${JAVA_HOME}/bin:${PATH}"
ENTRYPOINT ["java","-Xmx256M","-XX:+UseG1GC", "-XX:+UseStringDeduplication","-XX:+ShrinkHeapInSteps","-jar","PeerBanHelper.jar"]