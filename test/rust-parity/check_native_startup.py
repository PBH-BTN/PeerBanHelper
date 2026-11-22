#!/usr/bin/env python3
"""Complete browser onboarding and reach the scanner without a JVM or process restart."""

import argparse
import json
import os
import subprocess
import tempfile
import threading
import time
from pathlib import Path
from http.server import ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import uuid4

from capture_qbittorrent import DownloaderState, PEER_IP, configure, history


def verify(command, stop, check_file_log):
    downloader = DownloaderState()
    with (
        ThreadingHTTPServer(("127.0.0.1", 0), downloader.handler()) as server,
        tempfile.TemporaryDirectory(prefix="pbh-native-startup-") as directory,
    ):
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        data = Path(directory)
        try:
            configure(data, server.server_port)
            (data / "config/config.yml").write_text(
                "server:\n  http: 0\n  address: 127.0.0.1\n  token: ''\n", encoding="utf-8",
            )
            with (data / "startup.log").open("w+b") as log:
                process = subprocess.Popen(
                    command(data),
                    stdout=log, stderr=subprocess.STDOUT,
                    env={**os.environ, "PBH_DISABLE_NETWORK_PROBES": "true"},
                )
                try:
                    endpoint = wait_for(process, log, lambda: listening_address(data / "startup.log"))
                    wait_for(process, log, lambda: request(endpoint, "/"))
                    assert not (data / "persist").exists()
                    first_run = request(endpoint, "/api/oobe/init", body={
                        "token": "native-startup-test",
                        "database": {"type": "sqlite"},
                        "downloader": {"id": "loopback", "config": {
                            "type": "qbittorrent", "name": "Loopback",
                            "endpoint": f"http://127.0.0.1:{server.server_port}",
                            "incrementBan": True,
                        }},
                    })
                    assert first_run[0] == 201 and json.loads(first_run[1])["success"], first_run
                    def scan_ready():
                        _, body = request(endpoint, "/api/downloaders", token="native-startup-test")
                        entries = json.loads(body)["data"]
                        return entries if entries and history(data) else None

                    entries = wait_for(process, log, scan_ready)
                    assert entries[0]["id"] == "loopback", entries
                    persisted, = history(data)
                    assert persisted["ip"] == PEER_IP, persisted
                    assert process.poll() is None
                    if check_file_log:
                        file_log = (data / "logs/latest.log").read_text(encoding="utf-8")
                        assert "/INFO]:" in file_log and endpoint.removeprefix("http://") in file_log, file_log
                finally:
                    if process.poll() is None:
                        stop(process)
                    process.wait(timeout=10)
        finally:
            server.shutdown()
            worker.join()


def request(endpoint, path, token=None, body=None):
    headers = {}
    if token is not None:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(body).encode()
    with urlopen(Request(endpoint + path, data=body, headers=headers), timeout=2) as response:
        return response.status, response.read()


def listening_address(log_path):
    for line in log_path.read_text(encoding="utf-8").splitlines():
        if "listening on 127.0.0.1:" in line:
            return f"http://{line.rsplit(' ', 1)[1]}"
    return None


def verify_legacy_environment(binary, webui):
    downloader = DownloaderState()
    with (
        ThreadingHTTPServer(("127.0.0.1", 0), downloader.handler()) as server,
        tempfile.TemporaryDirectory(prefix="pbh-legacy-environment-") as directory,
    ):
        data = Path(directory) / "runtime"
        data.mkdir()
        configure(data, server.server_port)
        config = data.parent / "legacy config"
        (data / "config").rename(config)
        main_config = config / "config.yml"
        contents = main_config.read_text(encoding="utf-8")
        main_config.write_text(
            contents.replace("http: 0", "http: 1").replace("address: 127.0.0.1", "address: 192.0.2.7"),
            encoding="utf-8",
        )
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        startup = data / "startup.log"
        token = "native-environment-test"
        environment = {
            **os.environ,
            "PBH_DISABLE_NETWORK_PROBES": "true",
            "PBH_LOG_LEVEL": "INFO",
            "PBH_DATADIR": str(data),
            "PBH_CONFIGDIR": str(config),
            "PBH_APITOKEN": token,
            "PBH_PORT": "0",
            "PBH_SERVERADDRESS": "127.0.0.1",
        }
        try:
            with startup.open("w+b") as log:
                process = subprocess.Popen(
                    [str(binary), "run", "--webui-dir", str(webui)],
                    stdout=log, stderr=subprocess.STDOUT,
                    env=environment,
                )
                try:
                    endpoint = wait_for(process, log, lambda: listening_address(startup))
                    wait_for(process, log, lambda: request(endpoint, "/api/downloaders", token=token))
                    wait_for(process, log, lambda: history(data))
                    try:
                        request(endpoint, "/api/downloaders", token="test")
                    except HTTPError as denied:
                        assert denied.code == 403, denied.code
                    else:
                        raise AssertionError("the YAML token was accepted while the environment token was set")
                    status, body = request(endpoint, "/api/general/reload", token=token, body={})
                    assert status == 200 and json.loads(body)["success"], body
                    wait_for(
                        process,
                        log,
                        lambda: startup.read_text(encoding="utf-8").count("listening on 127.0.0.1:") >= 2,
                    )
                    wait_for(process, log, lambda: request(endpoint, "/api/downloaders", token=token))
                    assert not (data / "config").exists()
                    assert "address: 192.0.2.7" in main_config.read_text(encoding="utf-8")
                    assert token not in main_config.read_text(encoding="utf-8")
                finally:
                    if process.poll() is None:
                        process.terminate()
                    process.wait(timeout=10)
        finally:
            server.shutdown()
            worker.join()


def wait_for(process, log, callback):
    deadline = time.monotonic() + 15
    while process.poll() is None and time.monotonic() < deadline:
        try:
            outcome = callback()
            if outcome:
                return outcome
        except (URLError, ConnectionResetError, TimeoutError):
            pass
        time.sleep(0.05)
    log.seek(0)
    raise AssertionError(log.read().decode(errors="replace"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path, nargs="?")
    parser.add_argument("webui", type=Path, nargs="?")
    parser.add_argument("--docker-image")
    parser.add_argument("--check-file-log", action="store_true")
    parser.add_argument("--legacy-env", action="store_true")
    args = parser.parse_args()
    if args.docker_image:
        if args.legacy_env:
            parser.error("--legacy-env requires a local binary and WebUI")
        name = f"pbh-native-smoke-{uuid4().hex[:12]}"
        subprocess.run([
            "docker", "run", "--rm", "--entrypoint", "/bin/sh", args.docker_image,
            "-ec", "test -x /app/peerbanhelper && test -f /app/webui/dist/index.html && ! command -v java",
        ], check=True, timeout=20)

        def command(data):
            return ["docker", "run", "--rm", "--name", name, "--network", "host", "--env", "PBH_DISABLE_NETWORK_PROBES=true", "--volume", f"{data}:/app/data", args.docker_image]

        def stop(process):
            subprocess.run(["docker", "stop", "--time", "8", name], check=True, timeout=15, stdout=subprocess.DEVNULL)
            assert process.wait(timeout=10) == 0, "native container did not handle SIGTERM"
    else:
        if args.binary is None or args.webui is None:
            parser.error("supply binary and webui paths, or --docker-image")
        binary = args.binary.resolve(strict=True)
        webui = args.webui.resolve(strict=True)

        if args.legacy_env:
            verify_legacy_environment(binary, webui)
            print("Native legacy data, configuration, listener and token environment passed.")
            raise SystemExit(0)

        def command(data):
            return [str(binary), "run", "--data-dir", str(data), "--webui-dir", str(webui)]

        def stop(process):
            process.terminate()

    verify(command, stop, args.check_file_log)
    print("Native first-run setup became an authenticated, persistent scan in the same process.")
