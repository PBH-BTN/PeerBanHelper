#!/usr/bin/env python3
"""Rehearse SQLite cutover and rollback with one writer and loopback qBittorrent."""

import argparse
import hashlib
import json
import os
import shutil
import socket
import sqlite3
import subprocess
import sys
import threading
import time
from contextlib import closing, contextmanager
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.error import URLError
from urllib.request import ProxyHandler, Request, build_opener

from capture_qbittorrent import (
    BAN_MILLISECONDS, PEER_IP, PEER_PORT, ROOT, DownloaderState, configure, java_command,
)

DB_NAME = "peerbanhelper-nt.db"
OLD_DB = ROOT / "test/test-db/persist/peerbanhelper.db"
RUST_BAN = "198.51.100.144"
ROLLBACK_BAN = "198.51.100.145"
SCOPED_CTRL_C = """
import ctypes
import sys
import time

kernel = ctypes.WinDLL('kernel32', use_last_error=True)
kernel.FreeConsole()
if not kernel.AttachConsole(int(sys.argv[1])):
    raise ctypes.WinError(ctypes.get_last_error())
try:
    if not kernel.SetConsoleCtrlHandler(None, True):
        raise ctypes.WinError(ctypes.get_last_error())
    if not kernel.GenerateConsoleCtrlEvent(0, 0):
        raise ctypes.WinError(ctypes.get_last_error())
    time.sleep(0.2)
finally:
    kernel.FreeConsole()
"""


def digest(path):
    with path.open("rb") as file:
        return hashlib.file_digest(file, "sha256").hexdigest()


def fingerprint(directory):
    names = [*directory.joinpath("config").rglob("*"), *directory.joinpath("persist").rglob("*")]
    return {str(path.relative_to(directory)): digest(path) for path in sorted(names) if path.is_file()}


def setup(directory, downloader_port, web_port, source_data):
    directory.mkdir()
    configure(directory, downloader_port)
    config = directory / "config/config.yml"
    contents = config.read_text(encoding="utf-8")
    config.write_text(
        contents.replace("http: 0", f"http: {web_port}")
        .replace("ban-logs-keep-days: 180", "ban-logs-keep-days: 0")
        .replace("increment-ban: true", "increment-ban: false")
        .replace("auto-update: true", "auto-update: false"),
        encoding="utf-8",
    )
    profile = directory / "config/profile.yml"
    contents = profile.read_text(encoding="utf-8")
    profile.write_text(contents.replace(str(BAN_MILLISECONDS), "86400000"), encoding="utf-8")
    if source_data is not None:
        shutil.copytree(source_data / "persist", directory / "persist")
    else:
        (directory / "persist").mkdir()
        shutil.copyfile(OLD_DB, directory / "persist" / OLD_DB.name)


def stop(process, label, timeout, reference):
    if process.poll() is not None:
        raise RuntimeError(f"{label} exited before the handoff with status {process.returncode}")
    try:
        if os.name == "nt":
            subprocess.run([sys.executable, "-c", SCOPED_CTRL_C, str(process.pid)], check=True, timeout=5)
        else:
            process.terminate()
        code = process.wait(timeout=timeout)
    except (OSError, subprocess.SubprocessError) as error:
        process.kill()
        process.wait(timeout=5)
        raise RuntimeError(f"{label} did not shut down normally; handoff cancelled") from error
    expected = {0}
    if reference:
        expected.update({130, 0xC000013A} if os.name == "nt" else {143})
    if code not in expected:
        raise RuntimeError(f"{label} shutdown returned {code}; handoff cancelled")


@contextmanager
def running(command, directory, log, timeout, *, reference=False):
    options = {}
    if os.name == "nt":
        # The Ctrl+C sender attaches only to this hidden private fixture console.
        startup = subprocess.STARTUPINFO()
        startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        startup.wShowWindow = subprocess.SW_HIDE
        options = {"creationflags": subprocess.CREATE_NEW_CONSOLE, "startupinfo": startup}
    with log.open("wb") as output:
        process = subprocess.Popen(command, cwd=directory, stdout=output, stderr=subprocess.STDOUT, **options)
        try:
            yield process
            stop(process, log.stem, timeout, reference)
            if reference and "[Exit] All done!" not in log.read_text(encoding="utf-8", errors="replace"):
                raise RuntimeError(f"{log.stem} did not complete the Java shutdown hook")
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=5)


def wait_for(process, predicate, description, timeout):
    deadline = time.monotonic() + timeout
    last_error = None
    while process.poll() is None and time.monotonic() < deadline:
        try:
            result = predicate()
            if result:
                return result
        except URLError as error:
            last_error = error
        time.sleep(0.1)
    raise RuntimeError(f"Timed out waiting for {description}; process status {process.poll()}") from last_error


class Web:
    def __init__(self, port):
        self.endpoint = f"http://127.0.0.1:{port}"
        self.opener = build_opener(ProxyHandler({}))

    def request(self, path, method="GET", body=None):
        headers = {"Authorization": "Bearer test", "Accept-Language": "en-US"}
        data = None
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode("ascii")
        request = Request(self.endpoint + path, headers=headers, method=method, data=data)
        with self.opener.open(request, timeout=3) as response:
            if response.headers.get_content_type() != "application/json":
                # Java's static page is reachable before its API routers finish starting.
                raise URLError(f"{path} did not return JSON")
            result = json.load(response)
        if not result["success"]:
            raise RuntimeError(f"{path}: {result['message']}")
        return result["data"]

    def addresses(self):
        return {row["address"] for row in self.current_bans()}

    def current_bans(self):
        return self.request("/api/bans?page=1&pageSize=1000")["results"]

    def history(self):
        rows = self.request("/api/bans/logs?page=1&pageSize=1000")["results"]
        return [row for row in rows if row["peerIp"] in {PEER_IP, RUST_BAN, ROLLBACK_BAN}]

    def add(self, address):
        assert self.request("/api/bans", "PUT", [address])["count"] == 1


def reset_qb(state):
    with state.lock:
        state.events.clear()
        state.finished.clear()
        state.seen_peer_request = False
        # The downloader loses its in-memory ban list. The next writer must replay it.
        state.peer_present = True


def assert_restored(process, web, state, addresses, timeout):
    def synchronized():
        with state.lock:
            requests = [e for e in state.events if e["request"] == "setPreferences"]
            return next((e for e in requests if addresses <= set(e["banned_IPs"])), None)

    event = wait_for(process, synchronized, f"full qBittorrent update with {addresses}", timeout)
    wait_for(process, lambda: addresses <= web.addresses(), f"current ban API containing {addresses}", timeout)
    return event


def settled_addresses(web):
    rows = web.current_bans()
    now_ms = time.time_ns() // 1_000_000
    addresses = {row["address"] for row in rows}
    if PEER_IP in addresses and all(row["banMetadata"]["unbanAt"] >= now_ms for row in rows):
        return addresses
    return None


def checkpoint(directory, destination):
    # Writers are stopped before this function. Preserve the tree and all SQLite sidecars.
    shutil.copytree(directory / "config", destination / "config")
    shutil.copytree(directory / "persist", destination / "persist")
    shutil.copyfile(directory / "flags.properties", destination / "flags.properties")
    source = destination / "persist" / DB_NAME
    image = destination / "database.sqlite3"
    with closing(sqlite3.connect(source.as_uri() + "?mode=ro", uri=True)) as db, closing(sqlite3.connect(image)) as backup:
        db.backup(backup)
        assert backup.execute("PRAGMA integrity_check").fetchall() == [("ok",)]

    with closing(sqlite3.connect(image.as_uri() + "?mode=ro&immutable=1", uri=True)) as db:
        migrations = db.execute("SELECT version, checksum, success FROM flyway_schema_history ORDER BY installed_rank").fetchall()
        db.row_factory = sqlite3.Row
        history = [dict(row) for row in db.execute("SELECT * FROM history ORDER BY id")]
    assert migrations[-1][0] == "1.5" and all(success for _, _, success in migrations)
    return migrations, history


def rehearsal(args):
    source_before = fingerprint(args.source_data) if args.source_data else None
    legacy_before = digest(OLD_DB)
    state = DownloaderState()
    with ThreadingHTTPServer(("127.0.0.1", 0), state.handler()) as fixture, closing(socket.socket()) as port:
        port.bind(("127.0.0.1", 0))
        web_port = port.getsockname()[1]
        port.close()
        server = threading.Thread(target=fixture.serve_forever, daemon=True)
        server.start()
        try:
            return exercise(args, state, fixture.server_port, web_port)
        finally:
            fixture.shutdown()
            server.join()
            with state.lock:
                events = json.dumps(state.events, ensure_ascii=True, indent=2)
            (args.artifacts / "last-downloader.json").write_text(events + "\n", encoding="utf-8")
            assert digest(OLD_DB) == legacy_before, "tracked ORMLite source changed"
            if args.source_data:
                assert fingerprint(args.source_data) == source_before, "input modern database or configuration changed"


def exercise(args, state, downloader_port, web_port):
    output, timeout = args.artifacts, args.timeout
    seed = output / "java-before"
    setup(seed, downloader_port, web_port, args.source_data)
    initial = checkpoint(seed, output / "existing-modern") if args.source_data else None
    web = Web(web_port)
    java_seed = java_command(seed, args.java, args.jar, args.libraries)
    with running(java_seed, seed, output / "01-java-before.log", timeout, reference=True) as java:
        active_before = wait_for(java, lambda: settled_addresses(web), "Java to ban the peer and remove expired bans", timeout)
        before_api = next(row for row in web.history() if row["peerIp"] == PEER_IP)
        assert before_api["peerPort"] == PEER_PORT
    before_schema, before_rows = checkpoint(seed, output / "before-cutover")
    assert sum(row["ip"] == PEER_IP for row in before_rows) == 1
    if initial:
        initial_schema, initial_rows = initial
        assert before_schema == initial_schema and before_rows[:-1] == initial_rows

    live = output / "live"
    live.mkdir()
    shutil.copytree(seed / "config", live / "config")
    shutil.copytree(seed / "persist", live / "persist")
    shutil.copyfile(seed / "flags.properties", live / "flags.properties")
    working_files = [live / "config/config.yml", live / "config/profile.yml", live / "persist" / OLD_DB.name]
    original_digests = {path: digest(path) for path in working_files if path.is_file()}
    ui = output / "webui"
    ui.mkdir()
    (ui / "index.html").write_text("<!doctype html><title>PBH cutover fixture</title>\n", encoding="ascii")
    rust_command = [args.rust, "scan", "--apply", "--data-dir", live, "--webui-dir", ui]
    reset_qb(state)
    with running(rust_command, live, output / "02-rust.log", timeout) as rust:
        rust_replay = assert_restored(rust, web, state, active_before, timeout)
        rust_api = next(row for row in web.history() if row["peerIp"] == PEER_IP)
        assert history_identity(rust_api) == history_identity(before_api)
        web.add(RUST_BAN)
        assert_restored(rust, web, state, active_before | {RUST_BAN}, timeout)
    middle_schema, middle_rows = checkpoint(live, output / "before-rollback")
    assert before_schema == middle_schema, "Rust rewrote Flyway history"
    assert middle_rows[:-1] == before_rows and middle_rows[-1]["ip"] == RUST_BAN
    assert {path: digest(path) for path in original_digests} == original_digests

    reset_qb(state)
    java_live = java_command(live, args.java, args.jar, args.libraries)
    with running(java_live, live, output / "03-java-rollback.log", timeout, reference=True) as java:
        java_replay = assert_restored(java, web, state, active_before | {RUST_BAN}, timeout)
        rollback_api = {row["peerIp"]: row for row in web.history()}
        assert history_identity(rollback_api[PEER_IP]) == history_identity(before_api)
        assert rollback_api[RUST_BAN]["peerPort"] == 0
        web.add(ROLLBACK_BAN)
        assert_restored(java, web, state, active_before | {RUST_BAN, ROLLBACK_BAN}, timeout)
    after_schema, after_rows = checkpoint(live, output / "after-rollback")
    assert after_schema == before_schema, "rollback changed Flyway history"
    assert after_rows[:-1] == middle_rows and after_rows[-1]["ip"] == ROLLBACK_BAN
    assert {path: digest(path) for path in original_digests} == original_digests
    return {
        "migrationVersions": [version for version, _, _ in after_schema],
        "existingModernHistoryRows": len(initial[1]) if initial else None,
        "historyRows": [len(before_rows), len(middle_rows), len(after_rows)],
        "activeBeforeCutover": sorted(active_before),
        "rustReplay": rust_replay,
        "javaReplay": java_replay,
        "unchangedArtifacts": [str(path.relative_to(live)) for path in original_digests],
        "legacySha256": digest(OLD_DB),
    }


def history_identity(row):
    return tuple(row.get(field) for field in ("id", "peerIp", "peerPort", "torrentName", "banAt", "unbanAt"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rust", type=Path, required=True, help="native PeerBanHelper executable")
    parser.add_argument("--java", default="java", help="Java 25 launcher used for reference runs")
    parser.add_argument("--jar", type=Path, default=ROOT / "build/libs/PeerBanHelper.jar")
    parser.add_argument("--libraries", type=Path, default=ROOT / "build/libraries")
    parser.add_argument("--source-data", type=Path, help="optional stopped modern SQLite data directory to copy")
    parser.add_argument("--artifacts", type=Path, required=True, help="new directory for checkpoints and logs")
    parser.add_argument("--timeout", type=float, default=60.0, help="seconds per phase readiness and shutdown")
    args = parser.parse_args()
    if args.timeout <= 0:
        parser.error("timeout must be positive")
    args.rust = args.rust.resolve(strict=True)
    args.jar = args.jar.resolve(strict=True)
    args.libraries = args.libraries.resolve(strict=True)
    args.artifacts = args.artifacts.resolve()
    if args.source_data is not None:
        args.source_data = args.source_data.resolve(strict=True)
        if not args.source_data.joinpath("persist", DB_NAME).is_file():
            parser.error(f"source requires persist/{DB_NAME}")
        if args.artifacts.is_relative_to(args.source_data):
            parser.error("artifacts must be outside the source data directory")
    args.artifacts.mkdir(parents=True, exist_ok=False)
    summary = json.dumps(rehearsal(args), ensure_ascii=True, indent=2)
    (args.artifacts / "result.json").write_text(summary + "\n", encoding="utf-8")
    print(summary)


if __name__ == "__main__":
    main()
