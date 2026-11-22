# Rust behavioral comparison

`judge.py` adapts the [migrate-to-rust Stage 2 checklist](https://github.com/SynthLuvr/migrate-to-rust/blob/main/framework/stages/2-contracts.md) to a native CLI and saved Java observations. It is test tooling and runs with the Python standard library.

Each UTF-8 JSONL input record gives a unique string `id` and action-specific inputs. Each output record has the same `id` and one of `value` or `error`. An error has string `type` and `message` properties. A `null` value is valid evidence. JSON object and case order are immaterial. Array order, numeric type, strings and nulls compare exactly. Comparison reports JSON Pointer paths. Unexpected or missing cases and unusable reference captures are incomparable. A reference containing only errors is also incomparable. Exit codes are `0` for equivalent, `2` for divergent, and `3` for incomparable.

Run from the repository root:

```powershell
python -B test/rust-parity/judge.py self-test
python -B test/rust-parity/judge.py judge --corpus test/rust-parity/fixtures/decision-input.jsonl --reference test/rust-parity/fixtures/decision-reference.jsonl -- ./target/debug/peerbanhelper.exe compare-decisions
```

The decision fixtures were derived by inspecting Java commit `25be37332`. Their order is the order each result arrives at `DigestionSession`, with the logic described in [`traceboard/parity.md`](../../traceboard/parity.md). They do not contain runtime Java captures. To compare two independently captured files, use `compare` and supply `--candidate` instead of an executable.

The `ip-ban-expiry-java.jsonl` fixture comes from a JDK 25 run of the Gradle JAR at that commit. A loopback mock supplied one torrent and one Peer. The test waited for ban and expiry, then read SQLite history. Both runtimes use isolated temporary data directories and synthetic records. The sequence includes only requests that convey changes to the ban list.

```powershell
./gradlew.bat test --no-daemon --console=plain
python -B test/rust-parity/capture_qbittorrent.py --java 'C:/path/to/jdk-25/bin/java.exe'
python -B test/rust-parity/capture_qbittorrent.py --rust target/debug/peerbanhelper.exe | Set-Content -Encoding utf8NoBOM build/parity-rust-ip-ban-expiry.jsonl
python -B test/rust-parity/judge.py compare --corpus test/rust-parity/fixtures/ip-ban-expiry-input.jsonl --reference test/rust-parity/fixtures/ip-ban-expiry-java.jsonl --candidate build/parity-rust-ip-ban-expiry.jsonl
```
