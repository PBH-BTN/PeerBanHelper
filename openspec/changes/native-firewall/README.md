# Native Firewall

## Usage

The lifecycle-scoped service is available from the platform object:

```java
Firewall firewall = Main.getPlatform().getFirewall();
FirewallCapabilities capabilities = firewall.capabilities();

if (capabilities.available()) {
    firewall.add(addresses);
    Set<InetAddress> current = firewall.addresses();
    firewall.replace(nextAddresses);
    firewall.remove(addresses);
}
```

The list accepts exact IPv4 and globally representable IPv6 `InetAddress`
values. CIDR ranges and scoped link-local IPv6 addresses are not supported.
Every successful mutation covers inbound and outbound traffic, and queries
return an immutable snapshot of the last committed list.

## Platform Support

### Linux

- Backend: nftables through `libnftables` FFM.
- Requirement: an installed `libnftables` shared library and effective
  `CAP_NET_ADMIN` (normally root or an explicit container capability).
- Namespace: `peerbanhelper_firewall_<pid>_<token>` in the `inet` family.
- Lifetime: `flags owner` without `persist`; the kernel removes the table when
  the PeerBanHelper netlink socket or process closes.
- Compatibility cleanup: startup removes old unowned tables whose PID is dead.
- Process filtering: not provided.

### Windows

- Backend: Windows Filtering Platform through `fwpuclnt.dll` FFM.
- Requirement: an elevated PeerBanHelper process and a running Base Filtering
  Engine.
- Namespace: `PeerBanHelper Firewall <pid>-<token>` display names with
  process-unique provider and sublayer GUIDs.
- Crash cleanup: the dynamic WFP session is released by Windows when the process
  ends.
- Process filtering: not provided, although WFP can support application
  conditions in a future backend.

### macOS

PF is probed through libc FFM, but enforcement is not enabled. A safe
implementation needs a dedicated PF anchor referenced by the system ruleset or a
signed Network Extension. The backend reports `available=false` with the probe
reason and does not modify the host PF configuration.

## Cleanup Semantics

`FirewallManager` installs a JVM shutdown hook immediately after an available
backend is activated. Normal application shutdown also calls `close()` before
dependent network services are stopped. Native teardown is idempotent.

For an untrappable process kill, Linux and Windows rely on kernel/OS ownership:
nftables removes the process-owned table when the owning socket closes, and WFP
removes dynamic objects when the session ends. macOS creates no enforcement
state.

## Verification

Run the ordinary suite with:

```text
.\gradlew.bat test
```

Privileged state-management tests are opt-in and use TEST-NET addresses:

```text
PBH_NFTABLES_INTEGRATION=true ./gradlew test
$env:PBH_WFP_INTEGRATION='true'; .\gradlew.bat test
```

These tests require the platform privileges described above. They were not run
on the development host because it did not provide both Linux and elevated
Windows environments.

## Contribution Requirement

The repository owner requires the pull request description to be written
manually by a human. Agent-generated pull request text must not be submitted.
