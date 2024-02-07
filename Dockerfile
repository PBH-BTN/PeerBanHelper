FROM eclipse-temurin:17.0.10_7-jre
LABEL MAINTAINER="https://github.com/Ghost-chu/PeerBanHelper"

RUN mkdir /app
COPY ./docker-entrypoint.sh /app/
COPY ./target/PeerBanHelper.jar /app/

RUN chmod +x /app/docker-entrypoint.sh
WORKDIR /app
ENTRYPOINT ["/app/docker-entrypoint.sh"]