#!/usr/bin/env python3
"""Run native PBH against a real BiglyBT protocol-2 plugin in a temporary profile."""

import argparse
import json
import os
import shutil
import socket
import subprocess
import tempfile
import time
from pathlib import Path
from urllib.error import URLError
from urllib.request import ProxyHandler, Request, build_opener

from check_scan_recovery import run


TOKEN = "native-loopback-fixture"
PLUGIN_ID = "peerbanhelperadatper"


def bencode(value):
    if isinstance(value, int):
        return f"i{value}e".encode()
    if isinstance(value, str):
        raw = value.encode()
        return str(len(raw)).encode() + b":" + raw
    return b"d" + b"".join(bencode(key) + bencode(value[key]) for key in sorted(value)) + b"e"


def configure_biglybt(directory, plugin, port):
    plugin_dir = directory / "plugins" / PLUGIN_ID
    plugin_dir.mkdir(parents=True)
    shutil.copyfile(plugin, plugin_dir / "BiglyBT-PBH-Plugin.jar")
    prefix = f"Plugin.{PLUGIN_ID}.web"
    settings = {
        f"{prefix}.bind": "127.0.0.1",
        f"{prefix}.port": port,
        f"{prefix}.token": TOKEN,
        "Ip Filter Enabled": 1,
        "Bind IP": "127.0.0.1",
        "Enforce Bind IP": 1,
        "TCP.Listen.Port.Enable": 0,
        "UDP.Listen.Port.Enable": 0,
        "Plugin.DHT.dht.enabled": 0,
        "Plugin.UPnP.upnp.enable": 0,
    }
    (directory / "biglybt.config").write_bytes(bencode(settings))


def verify(binary, java, core_jar, plugin_jar, cli_jar):
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
    endpoint = f"http://127.0.0.1:{port}"
    opener = build_opener(ProxyHandler({}))

    def request(route, method="GET", payload=None):
        headers = {"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"}
        body = None if payload is None else json.dumps(payload).encode()
        with opener.open(Request(endpoint + route, body, headers, method=method), timeout=2) as response:
            return json.load(response)

    with tempfile.TemporaryDirectory(prefix="pbh-native-biglybt-") as temporary:
        root = Path(temporary)
        bigly = root / "bigly"
        install = root / "install"
        install.mkdir()
        configure_biglybt(bigly, plugin_jar, port)
        log = root / "bigly-start.log"
        classpath = os.pathsep.join((str(core_jar), str(cli_jar)))
        command = [
            java, "-Djava.awt.headless=true", "-DMULTI_INSTANCE=true",
            f"-Dazureus.config.path={bigly}", f"-Dazureus.install.path={install}",
            "-cp", classpath, "com.biglybt.ui.Main", "-u", "console",
        ]
        creation = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        with log.open("wb") as output:
            child = subprocess.Popen(
                command, cwd=install, stdin=subprocess.PIPE, stdout=output,
                stderr=output, creationflags=creation,
            )
            try:
                for _ in range(80):
                    try:
                        metadata = request("/metadata")
                        assert metadata["protocol"] == 2, metadata
                        break
                    except URLError:
                        if child.poll() is not None:
                            raise RuntimeError(log.read_text(errors="replace")[-2000:])
                        time.sleep(0.1)
                else:
                    raise RuntimeError(log.read_text(errors="replace")[-2000:])
                pbh = root / "pbh"
                (pbh / "config").mkdir(parents=True)
                (pbh / "config/config.yml").write_text(
                    f"""client:
  biglybt-fixture:
    type: biglybt
    endpoint: {endpoint}
    token: {TOKEN}
""", encoding="utf-8")
                (pbh / "config/profile.yml").write_text("{}", encoding="utf-8")

                initial = request("/v2/bans", "PUT", {
                    "added": ["192.0.2.113", "2001:db8::/52"], "removed": [],
                })
                assert initial["filterEnabled"], initial
                for apply in (False, True):
                    result, report = run(binary, pbh, apply=apply)
                    assert result.returncode == 0, result.stderr
                    assert not report["failures"], report
                    current = request("/v2/bans")
                    assert current["instance"] == initial["instance"], current
                    assert current["revision"] == initial["revision"] + int(apply), current
                    assert (pbh / "persist/peerbanhelper-nt.db").is_file() == apply
                print(f"Native PBH observe/apply passed against BiglyBT {metadata['applicationVersion']} "
                      f"and plugin {metadata['pluginVersion']} (protocol 2)")
            finally:
                if child.poll() is None:
                    child.terminate()
                child.wait(timeout=5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("--java", default="java")
    parser.add_argument("--core-jar", type=Path, required=True)
    parser.add_argument("--plugin-jar", type=Path, required=True)
    parser.add_argument("--cli-jar", type=Path, required=True)
    args = parser.parse_args()
    verify(
        args.binary.resolve(strict=True), args.java, args.core_jar.resolve(strict=True),
        args.plugin_jar.resolve(strict=True), args.cli_jar.resolve(strict=True),
    )


if __name__ == "__main__":
    main()
