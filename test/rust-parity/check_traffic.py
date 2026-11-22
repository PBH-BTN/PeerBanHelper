#!/usr/bin/env python3
"""Exercise cumulative downloader counters through the native ActiveMonitoring module."""

import argparse
import json
import sqlite3
import subprocess
import tempfile
import threading
from contextlib import closing
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

from capture_qbittorrent import DownloaderState, configure


class TrafficDownloader(DownloaderState):
    def __init__(self):
        super().__init__()
        self.peer_present = False
        self.up = 100
        self.down = 50
        self.limits = []
        self.notifications = []

    def handler(self):
        state = self
        base = super().handler()

        class Handler(base):
            def do_GET(self):
                path = urlsplit(self.path).path
                if path == "/api/v2/sync/maindata":
                    self.send_json({"server_state": {"alltime_ul": state.up, "alltime_dl": state.down}})
                elif path == "/api/v2/app/preferences":
                    self.send_json({"up_limit": 1_000, "dl_limit": 2_000})
                else:
                    super().do_GET()

            def do_POST(self):
                path = urlsplit(self.path).path
                if path == "/hook":
                    body = self.rfile.read(int(self.headers["Content-Length"]))
                    state.notifications.append(json.loads(body))
                    return self.send_content(200, b"Ok.", "text/plain")
                if path != "/api/v2/app/setPreferences":
                    return super().do_POST()
                body = self.rfile.read(int(self.headers["Content-Length"]))
                settings = json.loads(parse_qs(body.decode())["json"][0])
                if "up_limit" in settings:
                    state.limits.append((settings["up_limit"], settings["dl_limit"]))
                self.send_content(200, b"Ok.", "text/plain")

        return Handler


def verify(binary):
    state = TrafficDownloader()
    with ThreadingHTTPServer(("127.0.0.1", 0), state.handler()) as server:
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            with tempfile.TemporaryDirectory(prefix="pbh-traffic-") as directory:
                data = Path(directory)
                configure(data, server.server_port)
                with (data / "config/config.yml").open("a", encoding="utf-8") as config:
                    config.write(
                        f"\npush-notification:\n  test-hook:\n    type: webhook\n"
                        f"    url: http://127.0.0.1:{server.server_port}/hook\n"
                    )
                profile = data / "config/profile.yml"
                with profile.open("a", encoding="utf-8") as target:
                    target.write(
                        "  active-monitoring:\n"
                        "    enabled: true\n"
                        "    traffic-monitoring:\n"
                        "      daily: 1\n"
                        "    traffic-sliding-capping:\n"
                        "      enabled: true\n"
                        "      daily-max-allowed-upload-traffic: 8640000\n"
                        "      min-speed: 10\n"
                        "      max-speed: 5000\n"
                    )
                command = [str(binary), "scan", "--data-dir", str(data), "--apply", "--once"]
                for up, down in [(100, 50), (20, 10), (200, 30)]:
                    state.up, state.down = up, down
                    result = subprocess.run(command, capture_output=True, text=True, timeout=20)
                    assert result.returncode == 0, result.stderr
                database_path = data / "persist/peerbanhelper-nt.db"
                with closing(sqlite3.connect(database_path)) as conn:
                    counters = conn.execute(
                        "SELECT data_overall_uploaded_at_start, data_overall_downloaded_at_start, "
                        "data_overall_uploaded, data_overall_downloaded FROM traffic_journal_v3"
                    ).fetchall()
                    warning, = conn.execute(
                        "SELECT level, identifier FROM alert WHERE identifier LIKE 'dataTrafficCapping-%'"
                    ).fetchall()
                    assert warning[0] == "WARN", warning
                    conn.execute("UPDATE alert SET read_at = create_at WHERE identifier = ?", (warning[1],))
                    conn.commit()
                assert counters == [(100, 50, 200, 50)], counters
                assert state.limits == [(1100, 2000), (1100, 2000), (1099, 2000)], state.limits
                repeated = subprocess.run(command, capture_output=True, text=True, timeout=20)
                assert repeated.returncode == 0, repeated.stderr
                with closing(sqlite3.connect(database_path)) as conn:
                    assert conn.execute(
                        "SELECT count(*) FROM alert WHERE identifier = ?", (warning[1],)
                    ).fetchone() == (1,)
                notice, = state.notifications
                assert notice["level"] == "WARN" and notice["channelName"] == "test-hook", notice
                assert notice["title"].startswith("[PeerBanHelper/WARN] "), notice
        finally:
            server.shutdown()
            worker.join()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    verify(parser.parse_args().binary.resolve(strict=True))
    print("Hourly baseline, counter reset, rolling qBittorrent limits, and daily alert deduplication passed.")
