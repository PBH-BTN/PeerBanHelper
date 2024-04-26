FROM eclipse-temurin:17.0.10_7-jre

LABEL MAINTAINER="https://github.com/PBH-BTN/PeerBanHelper"

ENV TZ=UTC PUID=0 PGID=0

RUN set -ex && \
    export DEBIAN_FRONTEND=noninteractive && \
    apt update -y && \
    apt install -y gosu dumb-init && \
    apt autoremove -y && \
    apt clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --chmod=755 ./docker-entrypoint.sh /app/

COPY ./target/PeerBanHelper.jar /app/

ENTRYPOINT [ "/app/docker-entrypoint.sh" ]