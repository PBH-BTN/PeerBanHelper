#!/usr/bin/env python3
"""Exercise authenticated native downloader settings and read-only connection testing."""

import argparse
import json
import socket
import subprocess
import tempfile
import threading
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

from capture_qbittorrent import DownloaderState
from check_native_startup import wait_for


TOKEN = "downloader-settings-smoke"


def api(endpoint, path, method="GET", body=None, authorized=True, language="en-US"):
    headers = {"Accept-Language": language}
    if authorized:
        headers["Authorization"] = f"Bearer {TOKEN}"
    if body is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(body).encode()
    try:
        with urlopen(Request(endpoint + path, body, headers, method=method), timeout=3) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.load(error)


def verify(binary, webui):
    state = DownloaderState()
    base_handler = state.handler()
    requests = []
    forwarded = []
    deny = threading.Event()

    class Fixture(base_handler):
        def do_GET(self):
            target = urlsplit(self.path)
            path = target.path
            requests.append(("GET", path))
            if target.netloc:
                forwarded.append((target.netloc, path))
            if deny.is_set() and path.endswith("/app/buildInfo"):
                self.send_content(403, b"Forbidden", "text/plain")
            else:
                super().do_GET()

        def do_POST(self):
            path = urlsplit(self.path).path
            requests.append(("POST", path))
            if deny.is_set() and path.endswith("/auth/login"):
                self.send_content(200, b"Fails.", "text/plain")
            else:
                super().do_POST()

    with (
        ThreadingHTTPServer(("127.0.0.1", 0), Fixture) as downloader,
        tempfile.TemporaryDirectory(prefix="pbh-downloader-api-") as temporary,
    ):
        worker = threading.Thread(target=downloader.serve_forever, daemon=True)
        worker.start()
        try:
            data = Path(temporary)
            config = data / "config"
            config.mkdir()
            flags = data / "flags.properties"
            ipdb_disabled = "pbh.forceDisableIPDB=true\n"
            flags.write_text(ipdb_disabled, encoding="utf-8")
            with socket.socket() as port_probe:
                port_probe.bind(("127.0.0.1", 0))
                port = port_probe.getsockname()[1]
            (config / "config.yml").write_text(
                f"server:\n  http: {port}\n  address: 127.0.0.1\n  token: '{TOKEN}'\n"
                "database:\n  type: sqlite\nclient: {}\nprivacy:\n  analytics: false\n"
                f"proxy:\n  setting: 1\n  host: 127.0.0.1\n  port: {downloader.server_port}\n",
                encoding="utf-8",
            )
            (config / "profile.yml").write_text("check-interval: 100\nmodule: {}\n", encoding="utf-8")
            endpoint = f"http://127.0.0.1:{port}"
            draft = {"id": "loopback", "config": {
                "type": "qBittorrent", "name": "Loopback",
                "endpoint": f"http://127.0.0.1:{downloader.server_port}", "verifySsl": False,
                "username": "fixture-user", "password": "fixture-password", "paused": True,
            }}
            with (data / "api.log").open("w+b") as log:
                process = subprocess.Popen(
                    [str(binary), "scan", "--data-dir", str(data), "--apply", "--webui-dir", str(webui)],
                    stdout=log, stderr=subprocess.STDOUT,
                )
                try:
                    wait_for(process, log, lambda: api(endpoint, "/api/downloaders")[0] == 200)
                    assert api(endpoint, "/api/downloaders", "PUT", draft, authorized=False)[0] == 403
                    assert api(endpoint, "/api/downloaders/scan", "POST", authorized=False)[0] == 403
                    status, body = api(endpoint, "/api/downloaders/test", "POST", draft)
                    assert status == 200 and body["success"] and not requests, body

                    active = {**draft, "config": {**draft["config"], "paused": False}}
                    status, body = api(endpoint, "/api/downloaders/test", "POST", active)
                    assert status == 200 and body["success"], body
                    assert ("GET", "/api/v2/app/buildInfo") in requests, requests
                    assert ("POST", "/api/v2/app/setPreferences") not in requests, requests
                    assert not forwarded, forwarded
                    flags.write_text(ipdb_disabled + "pbh.downloader.bypassproxy=false\n", encoding="utf-8")
                    assert api(endpoint, "/api/downloaders/test", "POST", active)[1]["success"]
                    assert (f"127.0.0.1:{downloader.server_port}", "/api/v2/app/buildInfo") in forwarded
                    flags.write_text(ipdb_disabled, encoding="utf-8")
                    deny.set()
                    status, body = api(endpoint, "/api/downloaders/test", "POST", active)
                    assert status == 200 and not body["success"], body
                    assert "fixture-password" not in body["message"] and "fixture-user" not in body["message"]
                    deny.clear()

                    status, body = api(endpoint, "/api/downloaders", "PUT", draft, language="zh-CN")
                    assert status == 201 and body["success"] and body["message"] == "下载器创建成功", body
                    wait_for(process, log, lambda: api(endpoint, "/api/downloaders/loopback/status")[0] == 200)
                    assert api(endpoint, "/api/downloaders", "PUT", draft)[0] == 400
                    assert api(endpoint, "/api/downloaders", "PUT", {**draft, "id": "duplicate-name"})[0] == 400
                    assert api(endpoint, "/api/downloaders/loopback/status")[1]["data"]["config"]["verifySsl"] is False

                    changed = {"id": "ignored", "config": {**draft["config"], "name": "Edited", "apiKey": "configured-key"}}
                    assert api(endpoint, "/api/downloaders/loopback", "PATCH", changed)[0] == 200
                    wait_for(process, log, lambda: any(item["id"] == "loopback" and item["name"] == "Edited"
                                                         for item in api(endpoint, "/api/downloaders")[1].get("data") or []))
                    serialized = (config / "config.yml").read_text(encoding="utf-8")
                    assert "api-key: configured-key" in serialized and "ignored:" not in serialized

                    alias = {"config": {**draft["config"], "type": "Deluge", "name": "Deluge", "rpcUrl": "/json"}}
                    assert api(endpoint, "/api/downloaders/alias", "PATCH", alias)[0] == 200
                    wait_for(process, log, lambda: api(endpoint, "/api/downloaders/alias/status")[0] == 200)
                    alias_config = api(endpoint, "/api/downloaders/alias/status")[1]["data"]["config"]
                    assert alias_config["type"] == "raccoonfink/deluge" and alias_config["rpcUrl"] == "/json"

                    conflict = {"config": {**changed["config"], "name": "Deluge"}}
                    assert api(endpoint, "/api/downloaders/loopback", "PATCH", conflict)[0] == 400
                    assert api(endpoint, "/api/downloaders/loopback/status")[1]["data"]["config"]["name"] == "Edited"
                    assert api(endpoint, "/api/downloaders/loopback", "PATCH", active)[0] == 200
                    wait_for(process, log, lambda: state.seen_peer_request)

                    assert api(endpoint, "/api/downloaders/loopback", "DELETE")[0] == 200
                    wait_for(process, log, lambda: api(endpoint, "/api/downloaders/loopback/status")[0] == 404)
                    assert api(endpoint, "/api/downloaders/loopback", "DELETE")[0] == 404
                    assert "loopback:" not in (config / "config.yml").read_text(encoding="utf-8")
                finally:
                    if process.poll() is None:
                        process.terminate()
                        process.wait(timeout=5)
        finally:
            downloader.shutdown()
            worker.join()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("webui", type=Path)
    args = parser.parse_args()
    verify(args.binary.resolve(strict=True), args.webui.resolve(strict=True))
    print("Authenticated downloader configuration and read-only connection tests passed.")
