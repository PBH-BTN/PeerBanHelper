#!/usr/bin/env python3
"""Verify the Windows tray across OOBE, scanning, and graceful native shutdown."""

import argparse
import ctypes
from ctypes import wintypes
import json
import socket
import subprocess
import sys
import tempfile
import threading
from http.server import ThreadingHTTPServer
from pathlib import Path

from capture_qbittorrent import DownloaderState, PEER_IP, configure, history
from check_native_startup import request, wait_for


TOKEN = "native-desktop-test"
EXIT_COMMAND = 2
WM_COMMAND = 0x0111


class NotifyIconIdentifier(ctypes.Structure):
    _fields_ = [
        ("size", wintypes.DWORD),
        ("window", wintypes.HWND),
        ("id", wintypes.UINT),
        ("guid", ctypes.c_ubyte * 16),
    ]


def desktop_apis():
    user = ctypes.WinDLL("user32", use_last_error=True)
    shell = ctypes.WinDLL("shell32", use_last_error=True)
    callback_type = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    user.EnumWindows.argtypes = (callback_type, wintypes.LPARAM)
    user.GetShellWindow.restype = wintypes.HWND
    user.GetWindowThreadProcessId.argtypes = (wintypes.HWND, ctypes.POINTER(wintypes.DWORD))
    user.GetClassNameW.argtypes = (wintypes.HWND, wintypes.LPWSTR, ctypes.c_int)
    user.PostMessageW.argtypes = (wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM)
    shell.Shell_NotifyIconGetRect.argtypes = (
        ctypes.POINTER(NotifyIconIdentifier), ctypes.POINTER(wintypes.RECT),
    )

    def tray_window(pid):
        found = None

        @callback_type
        def inspect(window, _):
            nonlocal found
            owner = wintypes.DWORD()
            user.GetWindowThreadProcessId(window, ctypes.byref(owner))
            if owner.value == pid:
                name = ctypes.create_unicode_buffer(128)
                if user.GetClassNameW(window, name, len(name)) and name.value.startswith("PeerBanHelper.Tray."):
                    icon = NotifyIconIdentifier(size=ctypes.sizeof(NotifyIconIdentifier), window=window, id=1)
                    if shell.Shell_NotifyIconGetRect(ctypes.byref(icon), ctypes.byref(wintypes.RECT())) >= 0:
                        found = window
                        return False
            return True

        user.EnumWindows(inspect, 0)
        return found

    return user, tray_window


def verify(binary, webui):
    user, tray_window = desktop_apis()
    if not user.GetShellWindow():
        raise RuntimeError("the tray smoke test requires an interactive Windows Explorer session")
    state = DownloaderState()
    with (
        ThreadingHTTPServer(("127.0.0.1", 0), state.handler()) as downloader,
        tempfile.TemporaryDirectory(prefix="pbh-native-desktop-") as temporary,
    ):
        worker = threading.Thread(target=downloader.serve_forever, daemon=True)
        worker.start()
        data = Path(temporary)
        try:
            configure(data, downloader.server_port)
            (data / "flags.properties").write_text("pbh.forceDisableIPDB=true\n", encoding="utf-8")
            with socket.socket() as port_probe:
                port_probe.bind(("127.0.0.1", 0))
                port = port_probe.getsockname()[1]
            (data / "config/config.yml").write_text(
                f"server:\n  http: {port}\n  address: 127.0.0.1\n  token: ''\n",
                encoding="utf-8",
            )
            start = f"http://127.0.0.1:{port}"
            with (data / "desktop.log").open("w+b") as log:
                process = subprocess.Popen(
                    [str(binary), "run", "--tray", "--data-dir", str(data), "--webui-dir", str(webui)],
                    stdout=log, stderr=subprocess.STDOUT,
                )
                try:
                    window = wait_for(process, log, lambda: tray_window(process.pid))
                    wait_for(process, log, lambda: request(start, "/"))
                    assert not (data / "persist").exists()
                    status, body = request(start, "/api/oobe/init", body={
                        "token": TOKEN,
                        "database": {"type": "sqlite"},
                        "downloader": {"id": "loopback", "config": {
                            "type": "qbittorrent", "name": "Loopback",
                            "endpoint": f"http://127.0.0.1:{downloader.server_port}",
                            "incrementBan": True,
                        }},
                    })
                    assert status == 201 and json.loads(body)["success"], body

                    def scanner_ready():
                        _, result = request(start, "/api/downloaders", token=TOKEN)
                        return json.loads(result)["data"] and history(data)

                    ban_history = wait_for(process, log, scanner_ready)
                    assert ban_history[0]["ip"] == PEER_IP, ban_history
                    assert tray_window(process.pid) == window
                    assert user.PostMessageW(window, WM_COMMAND, EXIT_COMMAND, 0)
                    assert process.wait(timeout=12) == 0
                    assert not tray_window(process.pid)
                finally:
                    if process.poll() is None:
                        process.terminate()
                    process.wait(timeout=5)
        finally:
            downloader.shutdown()
            worker.join()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path)
    parser.add_argument("webui", type=Path)
    arguments = parser.parse_args()
    if sys.platform != "win32":
        parser.error("the native tray smoke test requires Windows")
    verify(arguments.binary.resolve(strict=True), arguments.webui.resolve(strict=True))
    print("Native Windows tray survived OOBE into scanning and shut down gracefully.")


if __name__ == "__main__":
    main()
