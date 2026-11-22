#!/bin/sh
set -eu

cd "$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
(
    cd webui
    pnpm install --frozen-lockfile
    pnpm run build
)
cargo build -p peerbanhelper --release --locked
