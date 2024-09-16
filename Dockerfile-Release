FROM docker.io/azul/zulu-openjdk-alpine:21.0.4-21.36-jre
LABEL maintainer="https://github.com/PBH-BTN/PeerBanHelper"
COPY target/PeerBanHelper.jar /app/PeerBanHelper.jar
USER 0
ENV TZ=UTC
WORKDIR /app
VOLUME /tmp
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENTRYPOINT ["java","-Xmx386M","-XX:+UseG1GC", "-XX:+UseStringDeduplication","-XX:+ShrinkHeapInSteps","-jar","PeerBanHelper.jar"]
