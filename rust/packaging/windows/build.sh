#!/bin/sh
set -eu

if [ "$#" -ne 3 ]; then
    echo "Usage: sh $0 BINARY WEBUI_DIST OUTPUT_DIRECTORY" >&2
    exit 2
fi

binary=$(realpath "$1")
webui=$(realpath "$2")
mkdir -p "$3"
output=$(realpath "$3")
test -f "$binary"
test -f "$webui/index.html"
packaging=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
project=$(CDPATH='' cd -- "$packaging/../../.." && pwd)
version=$(python3 -c 'import sys,tomllib; print(tomllib.load(open(sys.argv[1], "rb"))["workspace"]["package"]["version"])' "$project/Cargo.toml")
entries=$(mktemp)
trap 'rm -f -- "$entries"' EXIT HUP INT TERM
python3 - "$webui" "$entries" <<'PY'
import sys
from pathlib import Path, PureWindowsPath

root = Path(sys.argv[1])
paths = sorted(root.rglob("*"), key=lambda path: (-len(path.parts), path.as_posix()))
with Path(sys.argv[2]).open("w", encoding="utf-8") as output:
    for path in paths:
        item = str(PureWindowsPath(path.relative_to(root))).replace("$", "$$")
        if path.is_symlink() or any(c in item for c in '\r\n"'):
            raise SystemExit(f"Unsupported path in staged WebUI: {path}")
        command = "RMDir" if path.is_dir() else "Delete"
        print(f'{command} "$INSTDIR\\webui\\dist\\{item}"', file=output)
PY
makensis -V2 "-DOUTPUT=$output/PeerBanHelper-Native-$version-Setup.exe" \
    "-DAPP_VERSION=$version" "-DAPP_BINARY=$binary" "-DWEBUI_DIR=$webui" \
    "-DAPP_ICON=$project/install4j/icon.ico" "-DAPP_LICENSE=$project/LICENSE" \
    "-DUNINSTALL_WEBUI=$entries" "$packaging/installer.nsi"
