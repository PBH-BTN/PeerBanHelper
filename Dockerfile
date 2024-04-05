FROM eclipse-temurin:17.0.10_7-jre
LABEL MAINTAINER="https://github.com/Ghost-chu/PeerBanHelper"
ENV USE_NATIVE_IMAGE 0
RUN mkdir /app
WORKDIR /app
COPY ./docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh

COPY ./target/PeerBanHelper.jar /app/
COPY ./target/peerbanhelper-binary /app/

ENTRYPOINT ["/app/docker-entrypoint.sh"]