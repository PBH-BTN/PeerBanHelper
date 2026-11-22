#!/usr/bin/env python3
"""Capture a PBH ban-expiry scenario against a loopback qBittorrent."""

import argparse
import json
import os
import sqlite3
import subprocess
import tempfile
import threading
import time
from contextlib import closing
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


ROOT = Path(__file__).resolve().parents[2]
PEER_IP = "192.0.2.113"
PEER_PORT = 51413
BAN_MILLISECONDS = 1400
TORRENT_HASH = "a" * 40
PEER_KEY = f"{PEER_IP}:{PEER_PORT}"
TORRENT = {"hash": TORRENT_HASH, "infohash_v1": TORRENT_HASH, "name": "parity",
           "total_size": 4096, "size": 4096, "progress": 1.0, "private": False,
           "has_metadata": True, "state": "uploading"}
PEER = {"ip": PEER_IP, "port": PEER_PORT, "client": "Parity client",
        "peer_id_client": "-PC0001-reference", "connection": "BT", "flags": "D",
        "progress": 0.5, "uploaded": 100, "downloaded": 50, "up_speed": 100, "dl_speed": 50}


class DownloaderState:
    def __init__(self):
        self.lock = threading.Lock()
        self.events = []
        self.finished = threading.Event()
        self.seen_peer_request = False
        self.peer_present = True

    def handler(self):
        state = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                path = urlsplit(self.path).path.removeprefix("/api/v2")
                if path == "/sync/torrentPeers":
                    with state.lock:
                        state.seen_peer_request = True
                        peers = {PEER_KEY: PEER} if state.peer_present else {}
                    self.send_json({"full_update": True, "peers": peers, "rid": 1})
                    return
                responses = {
                    "/app/buildInfo": {"libtorrent": "2.0.11"},
                    "/app/preferences": {},
                    "/sync/maindata": {"server_state": {"alltime_dl": 1, "alltime_ul": 1}},
                    "/torrents/info": [TORRENT],
                    "/torrents/properties": {"is_private": False},
                    "/torrents/trackers": [],
                }
                if path == "/app/version":
                    self.send_content(200, b"v5.1.1", "text/plain")
                elif path in responses:
                    self.send_json(responses[path])
                else:
                    self.send_error(404)

            def do_POST(self):
                path = urlsplit(self.path).path.removeprefix("/api/v2")
                data = self.rfile.read(int(self.headers.get("Content-Length", "0")))
                form = parse_qs(data.decode("ascii"), keep_blank_values=True)
                with state.lock:
                    if path == "/app/setPreferences":
                        preferences = json.loads(form.get("json", ["{}"])[0])
                        if "banned_IPs" in preferences:
                            ips = preferences["banned_IPs"].splitlines()
                            state.events.append({"request": "setPreferences", "banned_IPs": ips})
                            if PEER_IP in ips:
                                state.peer_present = False
                    elif path == "/transfer/banPeers":
                        peers = form.get("peers", [""])[0].split("|")
                        state.events.append({"request": "banPeers", "peers": peers})
                        if PEER_KEY in peers:
                            state.peer_present = False
                    elif path not in ("/auth/login", "/auth/logout"):
                        self.send_error(404)
                        return
                    finished = (state.seen_peer_request and not state.peer_present
                                and state.events[-1] == {"request": "setPreferences", "banned_IPs": []})
                self.send_content(200, b"Ok.", "text/plain")
                if finished:
                    state.finished.set()

            def send_json(self, data):
                self.send_content(200, json.dumps(data).encode("ascii"), "application/json")

            def send_content(self, status, content, content_type):
                self.send_response(status)
                self.send_header("Content-Type", content_type)
                self.send_header("Content-Length", str(len(content)))
                self.end_headers()
                self.wfile.write(content)

            def log_message(self, *_):
                pass

        return Handler


def configure(directory, downloader_port):
    config = (ROOT / "test/test-db/config/config.yml").read_text(encoding="utf-8")
    config = (config.replace("@@@@DATABESE_TYPE@@@@", "sqlite")
              .replace("http: 9898", "http: 0").replace("address: 0.0.0.0", "address: 127.0.0.1")
              .replace("http://127.0.0.1:8080", f"http://127.0.0.1:{downloader_port}"))
    (directory / "config").mkdir()
    (directory / "config/config.yml").write_text(config, encoding="utf-8")
    (directory / "flags.properties").write_text("pbh.forceDisableIPDB = true\n", encoding="utf-8")
    (directory / "config/profile.yml").write_text(
        f"""config-version: 42
check-interval: 250
ban-duration: {BAN_MILLISECONDS}
ignore-peers-from-addresses: []
module:
  ip-address-blocker:
    enabled: true
    ban-duration: {BAN_MILLISECONDS}
    ips:
      - {PEER_IP}
""", encoding="utf-8")


def history(directory):
    database = directory / "persist/peerbanhelper-nt.db"
    with closing(sqlite3.connect(f"{database.as_uri()}?mode=ro", uri=True)) as conn:
        rows = conn.execute("SELECT ip, port, ban_at, unban_at FROM history ORDER BY id").fetchall()
    return [{"ip": ip, "port": port, "duration_ms": until - start}
            for ip, port, start, until in rows]


def java_command(directory, java, jar, libraries):
    # Gradle's test JAR has no WebUI assets. Provide the classpath directory
    # that Javalin requires so the history and downloader paths can start.
    static = directory / "static"
    static.mkdir()
    (static / "index.html").write_text("<!doctype html><title>PBH fixture</title>\n", encoding="utf-8")
    classpath = os.pathsep.join([str(jar), str(libraries / "*"), str(directory)])
    return [java, f"-Dpbh.datadir={directory}", "-cp", classpath,
            "com.ghostchu.peerbanhelper.MainJumpLoader", "nogui"]


def capture(command, timeout):
    state = DownloaderState()
    with ThreadingHTTPServer(("127.0.0.1", 0), state.handler()) as server:
        serving = threading.Thread(target=server.serve_forever, daemon=True)
        serving.start()
        try:
            with tempfile.TemporaryDirectory(prefix="pbh-qb-parity-") as path:
                directory = Path(path)
                configure(directory, server.server_port)
                log = directory / "pbh.log"
                with log.open("wb") as stdout:
                    process = subprocess.Popen(command(directory), cwd=directory, stdout=stdout,
                                               stderr=subprocess.STDOUT)
                    deadline = time.monotonic() + timeout
                    try:
                        while process.poll() is None and time.monotonic() < deadline:
                            if state.finished.wait(0.2):
                                return {"requests": state.events, "history": history(directory)}
                    finally:
                        if process.poll() is None:
                            process.terminate()
                            process.wait(timeout=5)
                tail = "\n".join(log.read_text(encoding="utf-8", errors="replace").splitlines()[-50:])
                raise RuntimeError(f"No completed ban-expiry scenario. Requests: {state.events}\n{tail}")
        finally:
            server.shutdown()
            serving.join()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java")
    parser.add_argument("--jar", type=Path, default=ROOT / "build/libs/PeerBanHelper.jar")
    parser.add_argument("--libraries", type=Path, default=ROOT / "build/libraries")
    parser.add_argument("--rust", type=Path, metavar="PBH_BINARY")
    parser.add_argument("--timeout", default=90.0, type=float)
    options = parser.parse_args()
    if options.timeout <= 0:
        parser.error("timeout must be positive")
    if options.rust:
        binary = options.rust.resolve(strict=True)

        def command(directory):
            return [str(binary), "scan", "--data-dir", str(directory), "--apply"]
    else:
        jar = options.jar.resolve(strict=True)
        libraries = options.libraries.resolve(strict=True)

        def command(directory):
            return java_command(directory, options.java, jar, libraries)

    output = capture(command, options.timeout)
    print(json.dumps({"id": "ip-ban-expiry", "value": output}, ensure_ascii=True))


if __name__ == "__main__":
    main()
