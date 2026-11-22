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
test -x "$binary"
test -f "$webui/index.html"
packaging=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
project=$(CDPATH='' cd -- "$packaging/../../.." && pwd)
version=$("$binary" --version | awk 'NR == 1 { print $2 }')
abi=$(pkg config ABI)
stage=$(mktemp -d)
trap 'rm -rf -- "$stage"' EXIT HUP INT TERM
mkdir -p "$stage/meta" "$stage/root/usr/local/lib/peerbanhelper/webui/dist"
install -d "$stage/root/usr/local/etc/rc.d" "$stage/root/usr/local/share/licenses/peerbanhelper"
install -m 755 "$binary" "$stage/root/usr/local/lib/peerbanhelper/peerbanhelper"
install -m 755 "$packaging/peerbanhelper" "$stage/root/usr/local/etc/rc.d/peerbanhelper"
install -m 644 "$project/LICENSE" "$stage/root/usr/local/share/licenses/peerbanhelper/GPLv3"
cp -R "$webui/." "$stage/root/usr/local/lib/peerbanhelper/webui/dist/"

cat > "$stage/meta/+MANIFEST" <<EOF
name: "peerbanhelper"
version: "$version"
origin: "net/peerbanhelper"
comment: "Monitor BitTorrent peers and synchronize blocklists"
desc: "Native PeerBanHelper service with the bundled WebUI"
maintainer: "ghostchu@111.com"
www: "https://github.com/PBH-BTN/PeerBanHelper"
arch: "$abi"
prefix: "/usr/local"
categories: ["net"]
licenselogic: "single"
licenses: ["GPLv3"]
EOF
echo 'To enable the service, run sysrc peerbanhelper_enable=YES.' > "$stage/meta/+DISPLAY"
(cd "$stage/root" && find usr -type f -print | LC_ALL=C sort | sed 's|^|/|') > "$stage/plist"
pkg create -m "$stage/meta" -r "$stage/root" -p "$stage/plist" -o "$output"
