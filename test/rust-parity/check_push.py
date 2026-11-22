#!/usr/bin/env python3
"""Verify provider CRUD, a local webhook request, and a local SMTP message."""

import argparse
import json
import socketserver
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError


def verify(binary):
    notifications = []
    emails = []

    class Webhook(BaseHTTPRequestHandler):
        def do_POST(self):
            data = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            notifications.append((self.path, self.headers["X-Label"], data))
            self.send_response(204)
            self.end_headers()

        def log_message(self, *_):
            pass

    class Mail(socketserver.StreamRequestHandler):
        def handle(self):
            self.wfile.write(b"220 localhost test server\r\n")
            while command := self.rfile.readline():
                word = command.split()[0].upper()
                if word in (b"EHLO", b"HELO"):
                    self.wfile.write(b"250-localhost\r\n250 8BITMIME\r\n")
                elif word == b"DATA":
                    self.wfile.write(b"354 End with a dot\r\n")
                    lines = []
                    while (line := self.rfile.readline()) not in (b"", b".\r\n"):
                        lines.append(line)
                    emails.append(b"".join(lines))
                    self.wfile.write(b"250 stored\r\n")
                elif word == b"QUIT":
                    self.wfile.write(b"221 closing\r\n")
                    return
                else:
                    self.wfile.write(b"250 OK\r\n")

    with (
        ThreadingHTTPServer(("127.0.0.1", 0), Webhook) as http,
        socketserver.ThreadingTCPServer(("127.0.0.1", 0), Mail) as smtp,
        tempfile.TemporaryDirectory(prefix="pbh-push-") as directory,
    ):
        servers = (http, smtp)
        threads = [threading.Thread(target=server.serve_forever, daemon=True) for server in servers]
        for thread in threads:
            thread.start()
        try:
            data = Path(directory)
            (data / "config").mkdir()
            (data / "config/config.yml").write_text(
                "server:\n  address: 127.0.0.1\n  http: 0\n  token: push-test\n",
                encoding="utf-8",
            )
            (data / "config/profile.yml").write_text(
                "check-interval: 250\nban-duration: 1500\n", encoding="utf-8",
            )
            (data / "flags.properties").write_text("pbh.forceDisableIPDB=true\n", encoding="utf-8")
            with (data / "run.log").open("w+b") as log:
                process = subprocess.Popen(
                    [str(binary), "scan", "--data-dir", directory, "--apply", "--webui-dir", directory],
                    stdout=log, stderr=subprocess.STDOUT,
                )
                try:
                    end = time.monotonic() + 10
                    endpoint = None
                    while process.poll() is None and time.monotonic() < end:
                        for line in (data / "run.log").read_text(encoding="utf-8").splitlines():
                            if "listening on 127.0.0.1:" in line:
                                endpoint = "http://" + line.rsplit(" ", 1)[1]
                                break
                        if endpoint:
                            break
                        time.sleep(0.05)
                    assert endpoint, (data / "run.log").read_text(encoding="utf-8")
                    hook = {"name": "test-hook", "type": "webhook", "config": {
                        "url": f"http://127.0.0.1:{http.server_port}/event?level={{level}}",
                        "headers": {"X-Label": "配置"},
                        "body_template": '{"event":"{title}","content":"{content}"}',
                    }}
                    try:
                        request(endpoint, "/api/push/test", "POST", hook, authenticated=False)
                        raise AssertionError("unauthenticated request could send a notification")
                    except HTTPError as error:
                        assert error.code == 403, error
                        error.close()
                    assert request(endpoint, "/api/push", "PUT", hook)[0] == 201
                    assert request(endpoint, "/api/push")[1]["data"] == [hook]
                    assert request(endpoint, "/api/push/test", "POST", hook)[1]["success"]
                    path, label, payload = notifications[0]
                    assert path == "/event?level=INFO" and label == "UTF-8''%E9%85%8D%E7%BD%AE"
                    assert "test-hook" in payload["content"], payload
                    config = {**hook, "name": "renamed"}
                    assert request(endpoint, "/api/push/test-hook", "PATCH", config)[1]["success"]
                    assert request(endpoint, "/api/push/renamed", "DELETE")[1]["success"]
                    assert request(endpoint, "/api/push")[1]["data"] == []
                    mail = {"name": "smtp-fixture", "type": "smtp", "config": {
                        "host": "127.0.0.1", "port": smtp.server_address[1], "encryption": "NONE",
                        "sender": "from@example.test", "receivers": ["to@example.test"],
                    }}
                    assert request(endpoint, "/api/push/test", "POST", mail)[1]["success"]
                    assert len(emails) == 1 and b"Subject: PeerBanHelper Push Notification Test Message" in emails[0], emails
                finally:
                    if process.poll() is None:
                        process.terminate()
                    process.wait(timeout=10)
        finally:
            for server in servers:
                server.shutdown()
            for thread in threads:
                thread.join()


def request(endpoint, path, method="GET", body=None, authenticated=True):
    headers = {"Accept-Language": "en-US", "Content-Type": "application/json"}
    if authenticated:
        headers["Authorization"] = "Bearer push-test"
    data = json.dumps(body).encode() if body is not None else None
    with urlopen(Request(endpoint + path, data=data, method=method, headers=headers), timeout=8) as response:
        return response.status, json.load(response)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    verify(parser.parse_args().binary.resolve(strict=True))
    print("Authenticated push configuration and loopback webhook/SMTP delivery passed.")
