# syntax=docker/dockerfile:1
FROM --platform=$BUILDPLATFORM node:25-bookworm-slim AS webui
ARG GIT_HASH
WORKDIR /webui
RUN npm install --global pnpm@12.21.0
COPY webui/ ./
RUN pnpm install --frozen-lockfile
RUN pnpm run build

FROM rust:1.96.0-bookworm AS native
ARG GIT_HASH
WORKDIR /source
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates libssl-dev pkg-config \
    && rm -rf /var/lib/apt/lists/*
COPY Cargo.toml Cargo.lock rust-toolchain.toml LICENSE ./
COPY rust/ ./rust/
COPY src/main/resources/ ./src/main/resources/
COPY test/test-db/ ./test/test-db/
RUN PBH_BUILD_GIT_COMMIT="$GIT_HASH" cargo build -p peerbanhelper --release --locked

FROM debian:bookworm-slim
LABEL org.opencontainers.image.source="https://github.com/PBH-BTN/PeerBanHelper"
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates libssl3 tzdata \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
ENV TZ=UTC PBH_RELEASE=docker
EXPOSE 9898
VOLUME ["/app/data", "/tmp"]
COPY --from=native /source/target/release/peerbanhelper /app/peerbanhelper
COPY --from=webui /webui/dist /app/webui/dist
COPY LICENSE /app/LICENSE
ENTRYPOINT ["/app/peerbanhelper"]
CMD ["run", "--data-dir", "/app/data", "--webui-dir", "/app/webui/dist"]
