#!/usr/bin/env python3
"""Verify the native BitComet scan, filter import and task-level unban with a loopback fixture."""

import argparse
import base64
import json
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from capture_qbittorrent import BAN_MILLISECONDS, PEER_IP, PEER_PORT, TORRENT_HASH, history
from check_scan_recovery import run


SUMMARY = {
    "task": {"task_id": 1, "task_name": "parity", "selected_downloaded_size": 4096,
             "download_rate": 100, "upload_rate": 50},
    "task_detail": {"infohash": TORRENT_HASH, "total_size": 4096, "torrent_private": False},
    "task_status": {"download_permillage": 1000},
}
PEER = {
    "group": "peers_connected", "ip": PEER_IP, "remote_port": PEER_PORT,
    "peer_id": "2d5043303030312d", "client_type": "Parity client",
    "dl_rate": 100, "up_rate": 50, "dl_size": 100, "up_size": 50, "permillage": 500,
}


class Fixture:
    def __init__(self):
        self.enabled = False
        self.peer_present = True
        self.writes = []
        self.lock = threading.Lock()

    def route(self, path, authorization, payload):
        if path == "/api/webui/login":
            assert not authorization
            assert base64.b64decode(payload["authentication"])[:2] == bytes((3, 1))
            return {"error_code": "OK", "invite_token": "fixture-invite", "version": "2.23"}
        if path == "/api/device_token/get":
            assert authorization == "Bearer fixture-invite"
            assert payload["invite_token"] == "fixture-invite"
            return {"device_token": "fixture-device", "version": "2.23"}
        assert authorization == "Bearer fixture-device"
        with self.lock:
            match path:
                case "/api/config/ipfilter/get":
                    return {"ip_filter_config": {
                        "enable_ipfilter": self.enabled, "ipfilter_mode": "blacklist",
                    }}
                case "/api/config/ipfilter/set":
                    assert payload["ip_filter_config"]["enable_ipfilter"] is True
                    self.enabled = True
                    self.writes.append(("enable filter",))
                case "/api/config/ipfilter/upload":
                    assert payload["import_type"] == "replace"
                    assert payload["data_type"] == "data_file"
                    entries = frozenset(base64.b64decode(payload["content_base64"]).decode().splitlines())
                    self.writes.append(("upload", entries))
                    if entries:
                        self.peer_present = False
                case "/api/task/peers/unban_peers":
                    assert payload["unban_range"] == "unban_peers_in_all_tasks"
                    self.writes.append(("unban", payload["ip_list"]))
                case "/api_v2/task_list/get":
                    return {"tasks": [{"task_id": 1, "type": "BT"}]}
                case "/api/task/summary/get":
                    return SUMMARY
                case "/api/task/peers/get":
                    return {"peers": [PEER] if self.peer_present else []}
                case _:
                    raise AssertionError(f"unexpected BitComet route {path}")
        return {"error_code": "ok"}

    def handler(self):
        fixture = self

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                assert self.headers["Client-Type"] == "BitComet WebUI"
                payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                response = fixture.route(self.path, self.headers.get("Authorization"), payload)
                data = json.dumps(response).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def log_message(self, *_):
                pass

        return Handler


def verify(binary):
    fixture = Fixture()
    with ThreadingHTTPServer(("127.0.0.1", 0), fixture.handler()) as server:
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            with tempfile.TemporaryDirectory(prefix="pbh-bitcomet-") as temporary:
                directory = Path(temporary)
                config = directory / "config"
                config.mkdir()
                (config / "config.yml").write_text(f"""client:
  bitcomet-fixture:
    type: bitcomet
    endpoint: http://127.0.0.1:{server.server_port}
    username: fixture-user
    password: fixture-password
""", encoding="utf-8")
                (config / "profile.yml").write_text(f"""ban-duration: {BAN_MILLISECONDS}
module:
  ip-address-blocker:
    enabled: true
    ips: [{PEER_IP}]
""", encoding="utf-8")
                result, observed = run(binary, directory, apply=False)
                assert result.returncode == 0, result.stderr
                assert [hit["address"] for hit in observed["matches"]] == [PEER_IP], observed
                assert fixture.writes == []
                assert not (directory / "persist").exists()

                result, applied = run(binary, directory, apply=True)
                assert result.returncode == 0, result.stderr
                assert [hit["address"] for hit in applied["matches"]] == [PEER_IP], applied
                banned = frozenset({PEER_IP, "::ffff:c000:271"})
                assert fixture.writes == [
                    ("enable filter",), ("upload", frozenset()), ("upload", banned),
                ], fixture.writes
                expected_history = [{
                    "ip": PEER_IP, "port": PEER_PORT, "duration_ms": BAN_MILLISECONDS,
                }]
                assert history(directory) == expected_history

                time.sleep(BAN_MILLISECONDS / 1000)
                result, expired = run(binary, directory, apply=True)
                assert result.returncode == 0, result.stderr
                assert expired["expired"] == [PEER_IP], expired
                assert fixture.writes[-3:] == [
                    ("upload", banned), ("unban", [PEER_IP]), ("upload", frozenset()),
                ], fixture.writes
                assert history(directory) == expected_history
        finally:
            server.shutdown()
            worker.join()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    verify(parser.parse_args().binary.resolve(strict=True))
    print("BitComet observe, full ban import, restart and task-level unban passed.")


if __name__ == "__main__":
    main()
