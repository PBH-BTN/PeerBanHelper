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
dpkg --validate-version "$version"
architecture=$(dpkg --print-architecture)
stage=$(mktemp -d)
trap 'rm -rf -- "$stage"' EXIT HUP INT TERM
chmod 0755 "$stage"

install -Dm755 "$binary" "$stage/usr/lib/peerbanhelper/peerbanhelper"
install -Dm644 "$packaging/peerbanhelper.service" "$stage/usr/lib/systemd/system/peerbanhelper.service"
"$binary" init-config --data-dir "$stage/defaults"
install -Dm644 "$stage/defaults/config/config.yml" "$stage/etc/peerbanhelper/config.yml"
install -Dm644 "$stage/defaults/config/profile.yml" "$stage/etc/peerbanhelper/profile.yml"
rm -r -- "$stage/defaults"
install -Dm644 "$project/LICENSE" "$stage/usr/share/doc/peerbanhelper/copyright"
install -Dm755 "$packaging/postinst" "$stage/DEBIAN/postinst"
install -Dm755 "$packaging/prerm" "$stage/DEBIAN/prerm"
mkdir -p "$stage/usr/lib/peerbanhelper/webui/dist"
cp -R "$webui/." "$stage/usr/lib/peerbanhelper/webui/dist/"

cat > "$stage/DEBIAN/conffiles" <<'EOF'
/etc/peerbanhelper/config.yml
/etc/peerbanhelper/profile.yml
EOF
cat > "$stage/DEBIAN/control" <<EOF
Package: peerbanhelper
Version: $version
Architecture: $architecture
Section: net
Priority: optional
Depends: libc6 (>= 2.34), libssl3 | libssl3t64, ca-certificates, adduser, init-system-helpers
Maintainer: PeerBanHelper contributors
Homepage: https://github.com/PBH-BTN/PeerBanHelper
Description: Monitor BitTorrent peers and synchronize blocklists
 Native PeerBanHelper service with the bundled WebUI.
EOF

dpkg-deb --root-owner-group --build "$stage" "$output/peerbanhelper_${version}_${architecture}.deb"
