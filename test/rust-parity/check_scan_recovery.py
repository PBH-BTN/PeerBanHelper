#!/usr/bin/env python3
"""Run observer, failed write, and restart against the native qBittorrent scanner."""

import argparse
import json
import sqlite3
import subprocess
import tempfile
import threading
import time
from contextlib import closing
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from capture_qbittorrent import (
    BAN_MILLISECONDS, PEER_IP, PEER_KEY, PEER_PORT, DownloaderState, configure, history,
)


class FailedIncrement(DownloaderState):
    def handler(self):
        state = self
        base = super().handler()

        class Handler(base):
            def do_POST(self):
                if urlsplit(self.path).path != "/api/v2/transfer/banPeers":
                    return super().do_POST()
                data = self.rfile.read(int(self.headers["Content-Length"]))
                peers = parse_qs(data.decode("ascii"))["peers"][0].split("|")
                with state.lock:
                    state.events.append({"request": "failed-banPeers", "peers": peers})
                    state.peer_present = False
                self.send_content(503, b"temporary failure", "text/plain")

        return Handler


def run(binary, directory, apply):
    arguments = [str(binary), "scan", "--data-dir", str(directory), "--once"]
    if apply:
        arguments.append("--apply")
    result = subprocess.run(arguments, capture_output=True, timeout=30, text=True)
    return result, json.loads(result.stdout)


def verify_bans_api(binary, directory):
    ui = directory / "webui"
    ui.mkdir()
    (ui / "index.html").write_text("<!doctype html><title>PeerBanHelper fixture</title>", encoding="utf-8")
    args = [str(binary), "scan", "--data-dir", str(directory), "--apply", "--webui-dir", str(ui)]
    with (directory / "http.log").open("w+b") as log:
        process = subprocess.Popen(args, stdout=subprocess.DEVNULL, stderr=log)
        try:
            deadline = time.monotonic() + 15
            endpoint = None
            while process.poll() is None and time.monotonic() < deadline:
                for line in (directory / "http.log").read_text(encoding="utf-8").splitlines():
                    if "listening on 127.0.0.1:" in line:
                        endpoint = f"http://{line.rsplit(' ', 1)[1]}"
                if endpoint is not None:
                    break
                time.sleep(0.05)
            assert endpoint is not None, (directory / "http.log").read_bytes()

            def request(path, token=None, method="GET", body=None):
                headers = {"Accept-Language": "en-US"}
                if token is not None:
                    headers["Authorization"] = f"Bearer {token}"
                if body is not None:
                    headers["Content-Type"] = "application/json"
                    body = json.dumps(body).encode()
                with urlopen(Request(endpoint + path, data=body, headers=headers, method=method), timeout=5) as response:
                    return response.read()

            try:
                request("/api/bans")
                raise AssertionError("unauthenticated current bans request was accepted")
            except HTTPError as error:
                assert error.code == 403, error
                error.close()
            except URLError as error:
                raise AssertionError(f"current bans endpoint unavailable at {endpoint}") from error

            row = json.loads(request("/api/bans?page=1&pageSize=1", "test"))["data"]
            assert row["total"] == 1 and row["size"] == 1, row
            ban = row["results"][0]
            assert ban["address"] == PEER_IP, ban
            assert ban["banMetadata"]["description"] == f"Match IP rule: {PEER_IP}", ban
            logs = json.loads(request("/api/bans/logs?page=1&pageSize=1&orderBy=peerPort%7Casc", "test"))["data"]
            assert logs["total"] == 1, logs
            assert logs["results"][0]["peerIp"] == PEER_IP, logs
            assert logs["results"][0]["torrentName"] == "parity", logs
            downloaders = json.loads(request("/api/downloaders", "test"))["data"]
            assert downloaders[0]["type"] == "qbittorrent", downloaders
            assert downloaders[0]["name"] == "test", downloaders
            status = json.loads(request("/api/general/status", "test"))["data"]
            assert status["native"]["runtime"] == "Rust" and status["system"]["cores"] > 0, status
            assert status["system"]["memory"]["total"] > 0, status
            profile = json.loads(request("/api/general/profile", "test"))["data"]
            assert profile["module"]["ip_address_blocker"]["enabled"] is True, profile
            nat = json.loads(request("/api/autostun/status", "test"))["data"]
            assert nat["enabled"] is False and nat["natType"] == "Unknown", nat
            events = json.loads(request("/api/logs/history", "test"))["data"]
            assert any("listening on" in event["content"] for event in events), events
            with urlopen(endpoint + "/api/logs/live?token=test", timeout=5) as stream:
                assert stream.headers["Content-Type"].startswith("text/event-stream")
                line = stream.readline()
                assert line.startswith(b"data:"), line
                first = json.loads(line.removeprefix(b"data:"))
                assert first == events[0], first
            assert b"PeerBanHelper fixture" in request("/banlist")

            def addresses(expected):
                deadline = time.monotonic() + 5
                while time.monotonic() < deadline:
                    entries = json.loads(request("/api/bans", "test"))["data"]["results"]
                    if {entry["address"] for entry in entries} == expected:
                        return entries
                    time.sleep(0.05)
                raise AssertionError(f"Expected active addresses {expected}, received {entries}")

            manual = "198.51.100.144"
            try:
                request("/api/bans", method="PUT", body=[manual])
                raise AssertionError("unauthenticated manual ban was accepted")
            except HTTPError as error:
                assert error.code == 403, error
                error.close()
            assert json.loads(request("/api/general/global", "test", "PATCH", {"globalPaused": True}))["success"]
            assert json.loads(request("/api/general/global", "test"))["data"]["globalPaused"] is True
            assert json.loads(request("/api/bans", "test", "PUT", [manual]))["data"]["count"] == 1
            time.sleep(0.35)
            assert json.loads(request("/api/bans", "test"))["data"]["total"] == 1
            assert json.loads(request("/api/general/global", "test", "PATCH", {"globalPaused": False}))["success"]
            manual_ban = next(entry for entry in addresses({PEER_IP, manual}) if entry["address"] == manual)
            assert manual_ban["banMetadata"]["description"] == f"User manually banned {manual} through API"
            assert (
                manual_ban["banMetadata"]["unbanAt"] - manual_ban["banMetadata"]["banAt"]
                == 100 * 365 * 24 * 60 * 60 * 1000
            )
            assert json.loads(request("/api/bans", "test", "DELETE", [manual]))["data"]["count"] == 1
            addresses({PEER_IP})
            assert json.loads(request("/api/bans", "test", "DELETE", ["*"]))["data"]["count"] == 1
            addresses(set())
            assert json.loads(request("/api/bans/logs", "test"))["data"]["total"] == 2
        finally:
            if process.poll() is None:
                process.terminate()
            process.wait(timeout=10)


def verify(binary):
    state = FailedIncrement()
    with ThreadingHTTPServer(("127.0.0.1", 0), state.handler()) as server:
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            with tempfile.TemporaryDirectory(prefix="pbh-recovery-") as directory:
                directory = Path(directory)
                configure(directory, server.server_port)
                duration = 120_000
                profile = directory / "config/profile.yml"
                updated_profile = profile.read_text(encoding="utf-8").replace(str(BAN_MILLISECONDS), str(duration))
                profile.write_text(updated_profile + "  peer-analyse-service:\n    peer-recording:\n      enabled: true\n", encoding="utf-8")

                result, report = run(binary, directory, apply=False)
                assert result.returncode == 0, result.stderr
                assert [peer["address"] for peer in report["matches"]] == [PEER_IP], report
                assert state.events == [], state.events
                assert not (directory / "persist").exists()

                result, report = run(binary, directory, apply=True)
                assert result.returncode != 0, result.stderr
                assert any("503" in reason for reason in report["failures"]), report
                expected_history = [{"ip": PEER_IP, "port": PEER_PORT, "duration_ms": duration}]
                assert history(directory) == expected_history
                database = directory / "persist/peerbanhelper-nt.db"
                with closing(sqlite3.connect(f"{database.as_uri()}?mode=ro", uri=True)) as connection:
                    assert connection.execute("SELECT address, port, uploaded FROM peer_records").fetchall() == [(PEER_IP, PEER_PORT, 100)]

                result, report = run(binary, directory, apply=True)
                assert result.returncode == 0, result.stderr
                assert report["matches"] == [], report
                assert history(directory) == expected_history
                assert state.events == [
                    {"request": "setPreferences", "banned_IPs": []},
                    {"request": "failed-banPeers", "peers": [PEER_KEY]},
                    {"request": "setPreferences", "banned_IPs": [PEER_IP, "::ffff:c000:271"]},
                ], state.events
                verify_bans_api(binary, directory)
        finally:
            server.shutdown()
            worker.join()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    verify(parser.parse_args().binary.resolve(strict=True))
    print("Read-only scan, failed incremental write, restart reconciliation, and authenticated live bans passed.")


if __name__ == "__main__":
    main()
