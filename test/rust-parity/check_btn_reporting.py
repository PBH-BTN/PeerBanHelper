#!/usr/bin/env python3
"""Verify the native application's BTN acknowledgement and persistent cursor."""

import argparse
import gzip
import json
import sqlite3
import subprocess
import tempfile
import threading
import time
from contextlib import closing
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
from urllib.error import URLError
from urllib.request import Request, urlopen

from capture_qbittorrent import BAN_MILLISECONDS, PEER_IP, DownloaderState, configure


def verify(binary, legacy):
    additional = "/peers" if legacy else "/swarm"
    reports = {"/report": [], additional: []}
    downloader = DownloaderState()
    downloader.peer_present = not legacy
    publish_allowlist = threading.Event()

    class Btn(BaseHTTPRequestHandler):
        def do_GET(self):
            url = urlsplit(self.path)
            if url.path == "/allowlist" and not legacy:
                if publish_allowlist.is_set() and parse_qs(url.query).get("rev") != ["fixture-1"]:
                    return self.reply(200, f"{PEER_IP}#fixture allowlisted\n".encode(), "fixture-1")
                return self.reply(204, b"")
            if url.path != "/config":
                return self.send_error(404)
            result = {"min_protocol_version": 19 if legacy else 20, "max_protocol_version": 21, "ability": {
                "submit_bans": {"endpoint": "/report", "interval": 130, "random_initial_delay": 0},
                ("submit_peers" if legacy else "submit_swarm"): {
                    "endpoint": additional, "interval": 130, "random_initial_delay": 0,
                },
            }}
            if not legacy:
                result["ability"]["ip_allowlist"] = {
                    "endpoint": "/allowlist", "interval": 130, "random_initial_delay": 0,
                }
            self.reply(200, json.dumps(result).encode())

        def do_POST(self):
            path = urlsplit(self.path).path
            if path not in reports:
                return self.send_error(404)
            data = self.rfile.read(int(self.headers["Content-Length"]))
            reports[path].append(json.loads(gzip.decompress(data)))
            first_report = len(reports[path]) == 1 and (not legacy or path == "/report")
            self.reply(503 if first_report else 200, b"{}")

        def reply(self, status, data, revision=None):
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            if revision is not None:
                self.send_header("X-BTN-ContentVersion", revision)
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, *_):
            pass

    with (
        ThreadingHTTPServer(("127.0.0.1", 0), downloader.handler()) as qb,
        ThreadingHTTPServer(("127.0.0.1", 0), Btn) as btn,
        tempfile.TemporaryDirectory(prefix="pbh-btn-reporting-") as directory,
    ):
        workers = [threading.Thread(target=server.serve_forever, daemon=True) for server in (qb, btn)]
        for worker in workers:
            worker.start()
        try:
            directory = Path(directory)
            configure(directory, qb.server_port)
            profile = directory / "config/profile.yml"
            profile.write_text(profile.read_text(encoding="utf-8").replace(str(BAN_MILLISECONDS), "120000"), encoding="utf-8")
            if not legacy:
                with profile.open("a", encoding="utf-8") as output:
                    output.write("  peer-analyse-service:\n    swarm-tracking:\n      enabled: true\n")
            (directory / "config/config.yml").write_text(f"""
server:
  http: 0
  address: 127.0.0.1
  token: test
btn:
  enabled: true
  submit: true
  app-id: local-test
  app-secret: local-test-secret
  config-url: http://127.0.0.1:{btn.server_port}/config
client:
  fixture:
    type: qbittorrent
    endpoint: http://127.0.0.1:{qb.server_port}
    increment-ban: true
""", encoding="utf-8")
            command = [str(binary), "scan", "--data-dir", str(directory), "--apply"]
            command += ["--webui-dir", str(directory / "webui")]
            with (directory / "output.txt").open("w+b") as log:
                process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
                try:
                    endpoint = wait_for_btn(process, directory / "output.txt")
                    if legacy:
                        with downloader.lock:
                            downloader.peer_present = True
                    deadline = time.monotonic() + 20
                    while process.poll() is None and time.monotonic() < deadline:
                        if legacy and len(reports["/report"]) >= 2 and reports[additional]:
                            break
                        if not legacy and all(len(rows) >= 2 for rows in reports.values()) and cursor(directory):
                            break
                        time.sleep(0.04)
                    else:
                        log.seek(0)
                        counts = {path: len(rows) for path, rows in reports.items()}
                        raise AssertionError((counts, log.read().decode(errors="replace").splitlines()[-15:]))
                    if legacy:
                        assert reports["/report"][0]["bans"] == reports["/report"][1]["bans"]
                        ban, = reports["/report"][1]["bans"]
                        peer, = reports[additional][0]["peers"]
                        assert ban["peer"] == peer and peer["ip_address"] == PEER_IP, (ban, peer)
                        assert len(ban["ban_unique_id"]) == 64 and len(peer["torrent_identifier"]) == 64
                        assert not ban["btn_ban"] and ban["module"].endswith(".IPBlackList"), ban
                        assert not cursor(directory)
                        time.sleep(0.35)
                        assert len(reports["/report"]) == 2
                        return
                    assert all(first == second for first, second, *_ in reports.values()), reports
                    row, = reports["/report"][1]["bans"]
                    assert row["peer_ip"] == PEER_IP, row
                    assert len(row["torrent_identifier"]) == 64, row
                    swarm, = reports["/swarm"][1]["swarms"]
                    assert swarm["peer_ip"] == PEER_IP and swarm["to_peer_traffic"] == 100, swarm
                    assert swarm["torrent_identifier"] == row["torrent_identifier"], swarm
                    time.sleep(0.35)
                    assert all(len(rows) == 2 for rows in reports.values()), reports
                    publish_allowlist.set()
                    deadline = time.monotonic() + 7
                    while time.monotonic() < deadline:
                        alerts = api(endpoint, "/api/alerts")
                        if alerts:
                            break
                        time.sleep(0.06)
                    alert, = alerts
                    assert alert["level"] == "INFO", alert
                    assert alert["identifier"].startswith(f"btn-allowlist-unbanned-peer-{PEER_IP}"), alert
                    assert "fixture allowlisted" in alert["content"] and PEER_IP in alert["content"], alert
                    assert api(endpoint, "/api/bans")["total"] == 0
                    time.sleep(0.35)
                    assert api(endpoint, "/api/alerts") == [alert]
                finally:
                    if process.poll() is None:
                        process.terminate()
                    process.wait(timeout=10)
        finally:
            for server in (qb, btn):
                server.shutdown()
            for worker in workers:
                worker.join()


def api(endpoint, path):
    with urlopen(Request(endpoint + path, headers={"Authorization": "Bearer test", "Accept-Language": "en-US"}), timeout=2) as response:
        return json.load(response)["data"]


def wait_for_btn(process, log):
    deadline = time.monotonic() + 10
    while process.poll() is None and time.monotonic() < deadline:
        for line in log.read_text(encoding="utf-8").splitlines():
            if "listening on 127.0.0.1:" not in line:
                continue
            endpoint = f"http://{line.rsplit(' ', 1)[1]}"
            try:
                if api(endpoint, "/api/modules/btn")["configSuccess"]:
                    return endpoint
            except URLError:
                pass
        time.sleep(0.05)
    raise AssertionError(log.read_text(encoding="utf-8"))


def cursor(directory):
    path = directory / "persist/peerbanhelper-nt.db"
    with closing(sqlite3.connect(f"{path.as_uri()}?mode=ro", uri=True)) as db:
        cursors = dict(db.execute(
            "select k, v from metadata where k in ('BtnAbilitySubmitBans.cursor', 'BtnAbilitySubmitSwarm.cursor')"
        ))
    swarm = cursors.get("BtnAbilitySubmitSwarm.cursor", "")
    return cursors.get("BtnAbilitySubmitBans.cursor") == "1" and swarm.partition(",")[2] == "1"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("--legacy", action="store_true")
    args = parser.parse_args()
    verify(args.binary.resolve(strict=True), args.legacy)
    if args.legacy:
        print("BTN legacy peer snapshot, ban retry and acknowledged watermark passed.")
    else:
        print("BTN report retry, acknowledged cursors, and an allowlist unban with one INFO alert passed.")


if __name__ == "__main__":
    main()
