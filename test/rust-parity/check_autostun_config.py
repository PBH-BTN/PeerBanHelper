#!/usr/bin/env python3
"""Exercise AutoSTUN settings through a native PBH process and loopback fixtures."""

import argparse
import json
import os
import socket
import subprocess
import tempfile
import threading
import time
from contextlib import ExitStack, contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from socketserver import BaseRequestHandler, ThreadingTCPServer
from urllib.parse import parse_qs, urlsplit
from urllib.request import ProxyHandler, Request, build_opener


TOKEN = "autostun-loopback-fixture"
DEADLINE = 12


@contextmanager
def run_server(server_class, handler):
    with server_class(("127.0.0.1", 0), handler) as server:
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            yield server.server_address[1]
        finally:
            server.shutdown()
            worker.join()


def eventually(action):
    last = None
    deadline = time.monotonic() + DEADLINE
    while time.monotonic() < deadline:
        try:
            value = action()
            if value:
                return value
        except OSError as error:
            last = error
        time.sleep(0.05)
    raise AssertionError(f"Timed out waiting for AutoSTUN state: {last}")


def verify(binary):
    state = {"port": 6881, "bindings": 0, "heartbeats": 0}
    lock = threading.Lock()

    class WebApi(BaseHTTPRequestHandler):
        def do_GET(self):
            path = urlsplit(self.path).path.removeprefix("/api/v2")
            if path == "/app/version":
                self.reply(b"v5.2.1", "text/plain")
                return
            with lock:
                responses = {
                    "/app/buildInfo": {"libtorrent": "2.0.11"},
                    "/app/preferences": {"listen_port": state["port"]},
                    "/torrents/info": [],
                    "/sync/maindata": {"server_state": {"alltime_ul": 1, "alltime_dl": 1}},
                }
            self.reply(json.dumps(responses[path]).encode(), "application/json")

        def do_POST(self):
            assert urlsplit(self.path).path == "/api/v2/app/setPreferences"
            form = parse_qs(self.rfile.read(int(self.headers["Content-Length"])).decode())
            preferences = json.loads(form["json"][0])
            if "listen_port" in preferences:
                with lock:
                    state["port"] = preferences["listen_port"]
            self.reply(b"Ok.", "text/plain")

        def reply(self, content, content_type):
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)

        def log_message(self, *_):
            pass

    class Stun(BaseRequestHandler):
        def handle(self):
            self.request.settimeout(3)
            with self.request.makefile("rb") as incoming:
                header = incoming.read(20)
            assert header[:8] == b"\0\1\0\0\x21\x12\xa4\x42", header
            with lock:
                state["bindings"] += 1
            reply = b"\1\1\0\x0c" + header[4:] + b"\0\1\0\x08\0\1"
            self.request.sendall(reply + backend_port.to_bytes(2, "big") + b"\x7f\0\0\1")

    class Heartbeat(BaseRequestHandler):
        def handle(self):
            self.request.settimeout(3)
            with self.request.makefile("rb") as incoming:
                while incoming.readline() == b"HEAD / HTTP/1.1\r\n":
                    for line in incoming:
                        if line == b"\r\n":
                            break
                    with lock:
                        state["heartbeats"] += 1
                    self.request.sendall(b"HTTP/1.1 204 No Content\r\n\r\n")

    with ExitStack() as stack:
        temporary = Path(stack.enter_context(tempfile.TemporaryDirectory(prefix="pbh-autostun-")))
        (temporary / "flags.properties").write_text("pbh.forceDisableIPDB=true\n", encoding="utf-8")
        backend = stack.enter_context(socket.socket())
        backend.bind(("127.0.0.1", 0))
        backend.listen()
        backend_port = backend.getsockname()[1]
        api_port = stack.enter_context(run_server(ThreadingHTTPServer, WebApi))
        stun_port = stack.enter_context(run_server(ThreadingTCPServer, Stun))
        heartbeat_port = stack.enter_context(run_server(ThreadingTCPServer, Heartbeat))
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            web_port = probe.getsockname()[1]
        base = f"http://127.0.0.1:{web_port}"
        directory = temporary / "config"
        directory.mkdir()
        config = directory / "config.yml"
        config.write_text(f"""server:
  address: 127.0.0.1
  http: {web_port}
  prefix: {base}
  token: {TOKEN}
client:
  fixture:
    type: qbittorrent
    endpoint: http://127.0.0.1:{api_port}
auto-stun:
  enabled: false
  downloaders: []
stun:
  tcp-servers: ["127.0.0.1:{stun_port}"]
  udp-servers: []
  available-test: false
  upnp: false
""", encoding="utf-8")
        (directory / "profile.yml").write_text("check-interval: 150\nmodule: {}\n", encoding="utf-8")
        env = dict(os.environ, PBH_STUNTCPTUNNEL_TESTHOST=f"127.0.0.1:{heartbeat_port}", PBH_DEMOMODE="false")
        env.pop("PBH_BTSTUN_LOCALPORT_FIXTURE", None)
        log_path = temporary / "pbh.log"
        with log_path.open("wb") as log:
            app = subprocess.Popen(
                [str(binary), "scan", "--data-dir", str(temporary), "--apply", "--webui-dir", str(temporary)],
                env=env, stdout=log, stderr=subprocess.STDOUT,
            )

        opener = build_opener(ProxyHandler({}))

        def call(path, method="GET", payload=None, allow_error=False):
            headers = {"Authorization": f"Bearer {TOKEN}"}
            data = None
            if payload is not None:
                data = json.dumps(payload).encode()
                headers["Content-Type"] = "application/json"
            request = Request(base + "/api/autostun" + path, data=data, headers=headers, method=method)
            with opener.open(request, timeout=2) as response:
                result = json.load(response)
            assert allow_error or result["success"], result
            return result

        def active_tunnel(after=0):
            info = call("/tunnel/fixture/info", allow_error=True)["data"]
            if info and info["valid"] and info["startedAt"] > after and info["lastSuccessHeartbeatAt"]:
                return info
            return None

        settings = {"enabled": True, "useFriendlyLoopbackMapping": False, "downloaders": ["fixture"]}
        try:
            status = eventually(lambda: call("/status")["data"])
            assert status["enabled"] is False and call("/tunnels")["data"] == []
            with lock:
                assert state["bindings"] == 0
            call("/config", "PUT", settings)
            first = eventually(active_tunnel)
            with lock:
                assert state["port"] == backend_port and state["heartbeats"] > 0
            assert call("/status")["data"]["selectedDownloaders"][0]["id"] == "fixture"
            call("/restart", "POST")
            second = eventually(lambda: active_tunnel(first["startedAt"]))
            with lock:
                assert state["bindings"] >= 2

            settings["enabled"] = False
            call("/config", "PUT", settings)
            eventually(lambda: not call("/status")["data"]["enabled"] and not call("/tunnels")["data"])

            def listener_is_closed():
                try:
                    with socket.create_connection(("127.0.0.1", second["proxyPort"]), timeout=3):
                        return False
                except ConnectionRefusedError:
                    return True

            eventually(listener_is_closed)
            assert "enabled: false" in config.read_text(encoding="utf-8")
        except Exception:
            print(log_path.read_text(encoding="utf-8", errors="replace")[-4000:])
            raise
        finally:
            app.terminate()
            app.wait(timeout=5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    verify(parser.parse_args().binary.resolve(strict=True))
    print("AutoSTUN config PUT, TCP startup, API restart, and disable passed.")


if __name__ == "__main__":
    main()
