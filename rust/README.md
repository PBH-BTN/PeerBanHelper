# PeerBanHelper native runtime

Build from the repository root using the pinned Rust toolchain and `cargo build -p peerbanhelper --locked`. Build the Vue application with `pnpm install --frozen-lockfile` and `pnpm run build` in `webui/`. Start the application with `peerbanhelper run --data-dir data --webui-dir webui/dist`. The first launch serves the setup wizard. An existing installation uses its configured token and downloaders.

`sh build.sh` builds both parts in release mode. Use the root `Dockerfile` to build from source or provide a prebuilt Linux binary and front end to `Dockerfile-Release`. See the [packaging guide](packaging/README.md) for operating system dependencies and [SQLite cutover](../traceboard/sqlite-cutover.md) for back-up and rollback.

An explicit `--data-dir` takes precedence over `PBH_DATADIR`. When neither is supplied, `PBH_USEPLATFORMCONFIGLOCATION=true` opts into the operating system's user configuration path. `PBH_CONFIGDIR` and `PBH_LOGSDIR` select separate configuration and log directories. `PBH_SERVERADDRESS`, `PBH_PORT` and `PBH_APITOKEN` override the runtime listener and token without changing their persisted YAML values.

## Code layout

- `app` owns startup, configuration upgrades, scheduling, downloader management, rule evaluation, monitoring and the API composition. `scan.rs` owns the writer lock and transaction boundaries between a completed wave, database state, and downloader synchronization.
- `core` contains address canonicalization, rule matching and decision types. The downloader crates convert each client's wire protocol to a shared snapshot and expose the operations its capability flags advertise. `network` owns outbound proxy policy and local downloader discovery.
- `storage` applies the bundled SQLite, PostgreSQL and MySQL migrations and implements each dialect's queries. `web` serves the Vue assets and owns HTTP authentication, session state, localization and first-run requests.
- `btn`, `geoip`, `plus`, `traversal`, and `desktop` own the corresponding protocol, database, license, tunnel and platform integrations. Their application-facing scheduling and endpoint wiring live in `app`.

An applying scan persists history and the current ban snapshot before sending changes to a downloader. Failed incremental updates request full reconciliation at the next successful connection. Configuration writes share an atomic YAML editor. A reload drains the current scan, retains the listening socket, and starts fresh integrations with the same active bans, counters and token sessions. Database and listener changes take effect on process restart.

The [traceboard](../traceboard/README.md) records behavioral comparisons, decisions and remaining validation. Run `cargo test --workspace --locked` for native tests. The `test/rust-parity` fixtures include the Java reference comparison and loopback tests for HTTP, protocol, persistence and startup behavior.
