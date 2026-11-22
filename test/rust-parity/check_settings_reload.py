#!/usr/bin/env python3
"""Exercise the native settings API and retain bans across a same-process reload."""

import argparse
from http.server import ThreadingHTTPServer
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from capture_qbittorrent import BAN_MILLISECONDS, PEER_IP, DownloaderState, configure, history


def request(endpoint, path, method="GET", body=None):
    headers = {"Authorization": "Bearer test", "Content-Type": "application/json"}
    encoded = json.dumps(body).encode() if body is not None else None
    with urlopen(Request(endpoint + path, encoded, headers, method=method), timeout=5) as response:
        return response.status, json.load(response)


def wait_for(process, predicate, directory):
    deadline = time.monotonic() + 15
    while process.poll() is None and time.monotonic() < deadline:
        try:
            if value := predicate():
                return value
        except (OSError, URLError):
            pass
        time.sleep(0.05)
    raise AssertionError((process.poll(), (directory / "service.log").read_text(encoding="utf-8")))


def verify(binary):
    downloader = DownloaderState()
    with ThreadingHTTPServer(("127.0.0.1", 0), downloader.handler()) as server:
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            with tempfile.TemporaryDirectory(prefix="pbh-reload-") as directory:
                data = Path(directory)
                configure(data, server.server_port)
                config = data / "config/config.yml"
                profile = data / "config/profile.yml"
                with socket.socket() as reservation:
                    reservation.bind(("127.0.0.1", 0))
                    port = reservation.getsockname()[1]
                config.write_text(
                    config.read_text(encoding="utf-8").replace("http: 0", f"http: {port}").replace("banlist: true", "banlist: false"),
                    encoding="utf-8",
                )
                profile.write_text(profile.read_text(encoding="utf-8").replace(str(BAN_MILLISECONDS), "120000"), encoding="utf-8")
                endpoint = f"http://127.0.0.1:{port}"
                with (data / "service.log").open("wb") as output:
                    args = [str(binary), "run", "--data-dir", str(data), "--webui-dir", str(data / "webui")]
                    process = subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=output)
                    try:
                        def addresses():
                            return [row["address"] for row in request(endpoint, "/api/bans")[1]["data"]["results"]]

                        assert wait_for(process, addresses, data) == [PEER_IP]
                        names = request(endpoint, "/api/metadata/manifest")[1]["data"]["modules"]
                        assert {module["configName"] for module in names} >= {
                            "ip-address-blocker", "webapi-background-tasks", "webapi-laboratory",
                        }
                        module_path = "/api/general/checkModuleAvailable?module=IPBlackList"
                        assert request(endpoint, module_path)[1]["data"] is True
                        login = Request(endpoint + "/api/auth/login", b'{"token":"test"}', {"Content-Type": "application/json"})
                        with urlopen(login, timeout=5) as response:
                            cookie = response.headers["Set-Cookie"].split(";", 1)[0]
                        before = profile.read_bytes()
                        try:
                            request(endpoint, "/api/general/profile", "PUT", {"check_interval": 0})
                        except HTTPError as error:
                            assert error.code == 400
                            assert not json.load(error)["success"]
                            error.close()
                        else:
                            raise AssertionError("invalid profile was accepted")
                        assert profile.read_bytes() == before

                        assert request(endpoint, "/api/general/global", "PATCH", {"globalPaused": True})[1]["success"]
                        with urlopen(Request(endpoint + "/api/logs/live?token=test"), timeout=5) as logs:
                            assert logs.headers["Content-Type"].startswith("text/event-stream")
                            status, saved = request(endpoint, "/api/general/profile", "PUT", {
                                "check_interval": 200,
                                "module": {"ip_address_blocker": {"enabled": False}},
                            })
                            assert status == 201 and saved["success"], saved
                            assert saved["data"][0]["reloadResult"] == "SCHEDULED", saved
                            assert b"listening on" in logs.read()

                        wait_for(process, lambda: (data / "service.log").read_text(encoding="utf-8").count("listening on") >= 2, data)
                        assert request(endpoint, "/api/general/global")[1]["data"]["globalPaused"] is True
                        assert request(endpoint, "/api/general/profile")[1]["data"]["check_interval"] == 200
                        assert request(endpoint, module_path)[1]["data"] is False
                        with urlopen(Request(endpoint + "/api/bans", headers={"Cookie": cookie}), timeout=5) as response:
                            assert json.load(response)["data"]["total"] == 1
                        assert addresses() == [PEER_IP]
                        assert request(endpoint, "/api/general/global", "PATCH", {"globalPaused": False})[1]["success"]
                        assert len(history(data)) == 1

                        status, saved = request(endpoint, "/api/general/config", "PUT", {"server": {"http": port + 1 if port < 65535 else port - 1}})
                        assert status == 201 and saved["success"], saved
                        assert {row["reloadResult"] for row in saved["data"]} == {"SCHEDULED", "REQUIRE_RESTART"}, saved
                        wait_for(process, lambda: (data / "service.log").read_text(encoding="utf-8").count("listening on") >= 3, data)
                        assert addresses() == [PEER_IP]
                    finally:
                        if process.poll() is None:
                            process.terminate()
                        process.wait(timeout=10)
        finally:
            server.shutdown()
            worker.join()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    verify(parser.parse_args().binary.resolve(strict=True))
    print("Settings validation, graceful stream reload, pause and active bans passed.")
