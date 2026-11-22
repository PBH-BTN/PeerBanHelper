#!/usr/bin/env python3
"""Verify Deluge scan, writes and the legacy config alias against a loopback plugin fixture."""

import argparse
import json
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from capture_qbittorrent import BAN_MILLISECONDS, PEER_IP, PEER_PORT, TORRENT_HASH, history
from check_scan_recovery import run


RPC_METHODS = [
    "peerbanhelperadapter.replace_blocklist",
    "peerbanhelperadapter.unban_ips",
    "peerbanhelperadapter.get_active_torrents_info",
    "peerbanhelperadapter.ban_ips",
]
EXPECTED_ADDRESSES = {PEER_IP, "::ffff:c000:271"}
TORRENT = {
    "id": TORRENT_HASH, "info_hash": TORRENT_HASH, "name": "parity",
    "size": 4096, "completed_size": 4096, "progress": 100,
    "upload_payload_rate": 100, "download_payload_rate": 50, "priv": False,
    "peers": [{
        "ip": PEER_IP, "port": PEER_PORT, "client_name": "Parity client",
        "peer_id": "2d5043303030312d", "flags": 1, "source": 1,
        "progress": 50, "total_upload": 100, "total_download": 50,
        "payload_up_speed": 100, "payload_down_speed": 50,
    }],
}


class DelugeFixture:
    def __init__(self):
        self.peer_present = True
        self.writes = []
        self.lock = threading.Lock()

    def handler(self):
        fixture = self

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                assert self.path == "/json"
                request = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                method = request["method"]
                if method == "auth.login":
                    assert request["params"] == ["fixture-password"]
                    result = True
                else:
                    assert self.headers["Cookie"] == "_session_id=fixture"
                    with fixture.lock:
                        match method:
                            case "system.listMethods":
                                result = RPC_METHODS
                            case "peerbanhelperadapter.get_active_torrents_info":
                                result = [TORRENT] if fixture.peer_present else []
                            case "peerbanhelperadapter.ban_ips" | "peerbanhelperadapter.replace_blocklist":
                                addresses, = request["params"]
                                fixture.writes.append((method, set(addresses)))
                                if addresses:
                                    fixture.peer_present = False
                                result = {}
                            case _:
                                raise AssertionError(f"unexpected Deluge RPC {method}")
                data = json.dumps({"id": request["id"], "result": result, "error": None}).encode()
                self.send_response(200)
                if method == "auth.login":
                    self.send_header("Set-Cookie", "_session_id=fixture; Path=/")
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def log_message(self, *_):
                pass

        return Handler


def verify(binary):
    fixture = DelugeFixture()
    with ThreadingHTTPServer(("127.0.0.1", 0), fixture.handler()) as server:
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            with tempfile.TemporaryDirectory(prefix="pbh-deluge-") as temporary:
                directory = Path(temporary)
                (directory / "config").mkdir()
                config = directory / "config/config.yml"
                settings = f"""client:
  deluge-fixture:
    type: deluge
    endpoint: http://127.0.0.1:{server.server_port}
    password: fixture-password
"""
                config.write_text(settings, encoding="utf-8")
                (directory / "config/profile.yml").write_text(
                    f"""ban-duration: {BAN_MILLISECONDS}
module:
  ip-address-blocker:
    enabled: true
    ips: [{PEER_IP}]
""", encoding="utf-8")
                result, report = run(binary, directory, apply=False)
                assert result.returncode == 0, result.stderr
                assert [hit["address"] for hit in report["matches"]] == [PEER_IP], report
                assert fixture.writes == []
                assert not (directory / "persist").exists()

                result, report = run(binary, directory, apply=True)
                assert result.returncode == 0, result.stderr
                assert fixture.writes == [
                    ("peerbanhelperadapter.replace_blocklist", set()),
                    ("peerbanhelperadapter.ban_ips", EXPECTED_ADDRESSES),
                ], fixture.writes
                expected_history = [{
                    "ip": PEER_IP, "port": PEER_PORT, "duration_ms": BAN_MILLISECONDS,
                }]
                assert history(directory) == expected_history

                config.write_text(
                    settings.replace("type: deluge", "type: raccoonfink/deluge"),
                    encoding="utf-8",
                )
                time.sleep(BAN_MILLISECONDS / 1000)
                result, report = run(binary, directory, apply=True)
                assert result.returncode == 0, result.stderr
                assert report["expired"] == [PEER_IP], report
                assert fixture.writes[-2:] == [
                    ("peerbanhelperadapter.replace_blocklist", EXPECTED_ADDRESSES),
                    ("peerbanhelperadapter.replace_blocklist", set()),
                ], fixture.writes
                assert history(directory) == expected_history
        finally:
            server.shutdown()
            worker.join()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    verify(parser.parse_args().binary.resolve(strict=True))
    print("Deluge observe, incremental ban, legacy alias and expiration passed.")


if __name__ == "__main__":
    main()
