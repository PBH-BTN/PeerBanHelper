#!/usr/bin/env python3
"""Verify that a native PBH scan publishes bans and expiry to a Transmission RPC fixture."""

import argparse
import json
import socket
import subprocess
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
from urllib.request import ProxyHandler, Request, build_opener

from capture_qbittorrent import BAN_MILLISECONDS, PEER_IP, PEER_PORT, TORRENT_HASH, history


RPC_PATH = "/transmission/rpc"
BLOCKLIST_PATH = "/blocklist/p2p-plain-format"
SESSION_HEADER = "X-Transmission-Session-Id"
SESSION_ID = "pbh-fixture-session"
EMPTY_LIST = ["TransmissionWorkaround:127.127.127.127-127.127.127.127"]
EXPECTED_RANGES = {f"{PEER_IP}-{PEER_IP}", "::ffff:c000:271-::ffff:c000:271"}
CUSTOM_NAT64_PEER = "2001:db8:64::c000:271"
CUSTOM_ADDRESS_CONFIG = """ip-remapping:
  nat64:
    enabled: true
    prefix: ["2001:db8:64::/96"]
banlist-remapping:
  ipv4:
    enabled: true
    remap-range: 30
"""
TORRENT = {
    "id": 17, "hashString": TORRENT_HASH, "name": "parity",
    "totalSize": 4096, "sizeWhenDone": 4096, "percentDone": 1.0,
    "isPrivate": False, "peersConnected": 1, "rateDownload": 0, "rateUpload": 100,
    "peers": [{
        "address": PEER_IP, "port": PEER_PORT, "clientName": "Parity client",
        "flagStr": "D", "progress": 0.5, "rateToPeer": 100, "rateToClient": 50,
    }],
}


class TransmissionFixture:
    def __init__(self, pbh_port, peer_address):
        self.pbh_url = f"http://127.0.0.1:{pbh_port}{BLOCKLIST_PATH}"
        self.torrent = {**TORRENT, "peers": [{**TORRENT["peers"][0], "address": peer_address}]}
        self.configured_url = None
        self.downloaded_lists = []
        self.peer_present = True
        self.lock = threading.Lock()

    def rpc(self, method, arguments):
        with self.lock:
            if method == "session-get":
                return {
                    "version": "4.1.1 (fixture)",
                    "blocklist-enabled": self.configured_url is not None,
                    "blocklist-url": self.configured_url or "",
                }
            if method == "torrent-get":
                return {"torrents": [self.torrent] if self.peer_present else []}
            if method == "session-set":
                url = arguments["blocklist-url"]
                assert arguments["blocklist-enabled"] is True, arguments
                base, _, query = url.partition("?")
                assert base == self.pbh_url, url
                assert int(parse_qs(query)["t"][0]) > 0, url
                self.configured_url = url
                return {}
            if method == "blocklist-update":
                assert self.configured_url is not None
                request = Request(self.configured_url, headers={"User-Agent": "Transmission/4.1.1"})
                # This request goes to the actual PBH listener with no auth token.
                with build_opener(ProxyHandler({})).open(request, timeout=5) as response:
                    lines = response.read().decode("ascii").splitlines()
                self.downloaded_lists.append(lines)
                if lines != EMPTY_LIST:
                    self.peer_present = False
                return {"blocklist-size": len(lines)}
            raise AssertionError(f"unexpected Transmission RPC method {method}")

    def handler(self):
        fixture = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.challenge()

            def do_POST(self):
                assert urlsplit(self.path).path == RPC_PATH
                if self.headers.get(SESSION_HEADER) != SESSION_ID:
                    self.challenge()
                    return
                body = self.rfile.read(int(self.headers["Content-Length"]))
                rpc = json.loads(body)
                result = fixture.rpc(rpc["method"], rpc["arguments"])
                data = json.dumps({"result": "success", "arguments": result}).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def challenge(self):
                assert urlsplit(self.path).path == RPC_PATH
                self.send_response(409)
                self.send_header(SESSION_HEADER, SESSION_ID)
                self.send_header("Content-Length", "0")
                self.end_headers()

            def log_message(self, *_):
                pass

        return Handler


def configure(directory, downloader_port, pbh_port, custom_policy):
    config = directory / "config"
    config.mkdir()
    (config / "config.yml").write_text(
        f"""server:
  address: 127.0.0.1
  http: {pbh_port}
  prefix: http://127.0.0.1:{pbh_port}
  token: fixture-token
client:
  transmission-fixture:
    type: transmission
    endpoint: http://127.0.0.1:{downloader_port}
""" + (CUSTOM_ADDRESS_CONFIG if custom_policy else ""), encoding="utf-8")
    (config / "profile.yml").write_text(
        f"""ban-duration: {BAN_MILLISECONDS}
module:
  ip-address-blocker:
    enabled: true
    ips:
      - {PEER_IP}
""", encoding="utf-8")


def scan(binary, directory, apply=False):
    command = [str(binary), "scan", "--data-dir", str(directory), "--once"]
    if apply:
        command.append("--apply")
    result = subprocess.run(command, capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, (result.stdout, result.stderr)
    report = json.loads(result.stdout)
    assert not report["failures"], report
    return report


def verify(binary, custom_policy):
    with socket.socket() as port_probe:
        port_probe.bind(("127.0.0.1", 0))
        pbh_port = port_probe.getsockname()[1]
    fixture = TransmissionFixture(pbh_port, CUSTOM_NAT64_PEER if custom_policy else PEER_IP)
    expected_ranges = EXPECTED_RANGES.copy()
    if custom_policy:
        expected_ranges.update({"192.0.2.112-192.0.2.115", "::ffff:c000:270-::ffff:c000:273"})
    with ThreadingHTTPServer(("127.0.0.1", 0), fixture.handler()) as server:
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            with tempfile.TemporaryDirectory(prefix="pbh-transmission-") as temporary:
                directory = Path(temporary)
                configure(directory, server.server_port, pbh_port, custom_policy)

                observed = scan(binary, directory)
                assert [hit["address"] for hit in observed["matches"]] == [PEER_IP], observed
                assert fixture.configured_url is None
                assert fixture.downloaded_lists == []
                assert not (directory / "persist").exists()

                applied = scan(binary, directory, apply=True)
                assert [hit["address"] for hit in applied["matches"]] == [PEER_IP], applied
                assert fixture.downloaded_lists[0] == EMPTY_LIST, fixture.downloaded_lists
                final_ranges = {
                    line.split(":", 1)[1] for line in fixture.downloaded_lists[-1]
                }
                assert final_ranges == expected_ranges, fixture.downloaded_lists
                expected_history = [{
                    "ip": PEER_IP, "port": PEER_PORT, "duration_ms": BAN_MILLISECONDS,
                }]
                assert history(directory) == expected_history

                time.sleep(BAN_MILLISECONDS / 1000)
                expired = scan(binary, directory, apply=True)
                assert expired["matches"] == [], expired
                assert expired["expired"] == [PEER_IP], expired
                assert fixture.downloaded_lists[-1] == EMPTY_LIST, fixture.downloaded_lists
                assert history(directory) == expected_history
        finally:
            server.shutdown()
            worker.join()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("--custom-address-policy", action="store_true")
    args = parser.parse_args()
    verify(args.binary.resolve(strict=True), args.custom_address_policy)
    print("Transmission observe, blocklist ban, restart and expiration passed.")


if __name__ == "__main__":
    main()
