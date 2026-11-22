#!/usr/bin/env python3
"""Exercise configured IP subscription refreshes, history, and failure retention."""

import argparse
import json
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.request import Request, urlopen

from capture_qbittorrent import DownloaderState, PEER_IP, configure, history


def verify(binary):
    unavailable = threading.Event()

    class Feed(BaseHTTPRequestHandler):
        def do_GET(self):
            assert self.path == "/feed"
            payload = b"temporary failure" if unavailable.is_set() else f"{PEER_IP}\n".encode()
            self.send_response(503 if unavailable.is_set() else 200)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, *_):
            pass

    downloader = DownloaderState()
    with (
        ThreadingHTTPServer(("127.0.0.1", 0), downloader.handler()) as qb,
        ThreadingHTTPServer(("127.0.0.1", 0), Feed) as feed,
        tempfile.TemporaryDirectory(prefix="pbh-subscription-") as directory,
    ):
        servers = (qb, feed)
        threads = [threading.Thread(target=server.serve_forever, daemon=True) for server in servers]
        for worker in threads:
            worker.start()
        try:
            data = Path(directory)
            configure(data, qb.server_port)
            (data / "config/profile.yml").write_text(
                "check-interval: 250\nban-duration: 60000\nmodule:\n"
                "  ip-address-blocker-rules:\n    enabled: true\n    check-interval: 60000\n    rules: {}\n",
                encoding="utf-8",
            )
            with (data / "run.log").open("w+b") as log:
                process = subprocess.Popen(
                    [str(binary), "scan", "--data-dir", directory, "--apply", "--webui-dir", directory],
                    stdout=log, stderr=subprocess.STDOUT,
                )
                try:
                    endpoint = None
                    deadline = time.monotonic() + 10
                    while process.poll() is None and time.monotonic() < deadline:
                        for line in (data / "run.log").read_text(encoding="utf-8").splitlines():
                            if "listening on 127.0.0.1:" in line:
                                endpoint = "http://" + line.rsplit(" ", 1)[1]
                                break
                        if endpoint:
                            break
                        time.sleep(0.05)
                    assert endpoint, (data / "run.log").read_text(encoding="utf-8")
                    assert request(endpoint, "/api/sub/rules")["data"] == []
                    rule = {"ruleId": "fixture", "ruleName": "Loopback", "subUrl": f"http://127.0.0.1:{feed.server_port}/feed"}
                    assert request(endpoint, "/api/sub/rule", "PUT", rule)["success"]
                    brief = request(endpoint, "/api/sub/rule/fixture")["data"]
                    assert brief["entCount"] == 1 and brief["lastUpdate"] > 0, brief
                    database_log, = request(endpoint, "/api/sub/logs/fixture")["data"]["results"]
                    assert database_log["updateType"] == "MANUAL" and database_log["count"] == 1, database_log
                    assert request(endpoint, "/api/sub/rule/fixture/update", "POST")["success"]
                    unavailable.set()
                    assert not request(endpoint, "/api/sub/rule/fixture/update", "POST")["success"]
                    assert request(endpoint, "/api/sub/logs")["data"]["total"] == 1
                    assert (data / "sub/fixture.txt").read_text(encoding="utf-8") == PEER_IP + "\n"
                    deadline = time.monotonic() + 5
                    while not history(data) and time.monotonic() < deadline:
                        time.sleep(0.05)
                    assert history(data)[0]["ip"] == PEER_IP
                    assert request(endpoint, "/api/sub/rule/fixture", "PATCH", {"enabled": False})["success"]
                    assert request(endpoint, "/api/sub/rule/fixture")["data"]["enabled"] is False
                    assert request(endpoint, "/api/sub/rule/fixture", "DELETE")["success"]
                    assert request(endpoint, "/api/sub/rules")["data"] == []
                finally:
                    if process.poll() is None:
                        process.terminate()
                    process.wait(timeout=10)
        finally:
            for server in servers:
                server.shutdown()
            for worker in threads:
                worker.join()


def request(endpoint, path, method="GET", body=None):
    headers = {"Authorization": "Bearer test", "Content-Type": "application/json"}
    data = json.dumps(body).encode() if body is not None else None
    with urlopen(Request(endpoint + path, data=data, method=method, headers=headers), timeout=8) as response:
        return json.load(response)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    verify(parser.parse_args().binary.resolve(strict=True))
    print("Subscription lifecycle, authenticated history, active ban and cache retention passed.")
