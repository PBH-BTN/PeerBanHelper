# Native Firewall Design

## Context

The repository targets Java 25 and already uses the Foreign Function & Memory
API in platform-specific packages. The platform implementation is selected in
`Main.loadPlatform()`, and `PeerBanHelper.shutdown()` is the application-level
shutdown path. The firewall therefore belongs under the existing `platform`
package rather than in the ban pipeline or downloader abstractions.

## Goals

- Offer one simple, blocking API to the rest of PeerBanHelper.
- Keep the native firewall namespace private to one PeerBanHelper process.
- Make every list mutation atomic from the caller's perspective.
- Support IPv4 and IPv6 equally.
- Describe actual capabilities instead of assuming every platform can provide
  the same filtering semantics.
- Remove firewall state on graceful exit and clean up orphaned state on the
  next startup after an abnormal exit.

## Package Layout

```text
com.ghostchu.peerbanhelper.platform
  Platform.java
  firewall/
    Firewall.java
    FirewallCapabilities.java
    FirewallBackend.java
    FirewallException.java
    FirewallFactory.java
    FirewallManager.java
    UnsupportedFirewall.java
  impl/linux/firewall/
    LinuxFirewall.java
    NftablesNative.java
    NftablesNamespace.java
  impl/win32/firewall/
    WindowsFirewall.java
    WfpNative.java
    WfpStructures.java
  impl/macos/firewall/
    MacOsFirewall.java
    PfNative.java
```

The public contract remains in `platform.firewall`. Native libraries, memory
layouts, and handles stay in the OS-specific implementation packages.

## Public Contract

`Firewall` is an `AutoCloseable` service with the following behavior:

- `capabilities()` returns an immutable capability snapshot.
- `addresses()` returns an immutable snapshot containing both IPv4 and IPv6
  addresses.
- `add(addresses)`, `remove(addresses)`, and `replace(addresses)` are
  transactional. The managed snapshot changes only after the native operation
  succeeds.
- Duplicate addresses are ignored. Removing an absent address is a no-op.
- `clear()` removes every managed address but leaves the backend ready for
  later use.
- `close()` is idempotent and removes the process-owned native namespace.
- All mutation and read operations are serialized by one lock so a query cannot
  observe a partially replaced set.

The source of truth is the backend's managed snapshot. It is updated only after
the corresponding native transaction commits. External edits to the
PeerBanHelper-owned namespace are outside the contract; the namespace is
process-private and named clearly so administrators can identify it.

The initial API accepts exact `InetAddress` values. CIDR ranges, subnet
normalization, and scoped link-local IPv6 addresses are intentionally deferred.

## Capability Model

`FirewallCapabilities` is an immutable record describing:

- availability and backend name;
- IPv4 and IPv6 support;
- inbound and outbound blocking;
- system-wide filtering;
- process-level filtering;
- privilege requirement;
- graceful-exit and crash-recovery cleanup modes;
- a human-readable unsupported or degraded reason.

Availability must be based on a real probe. A missing native library, unsupported
OS build, inaccessible firewall service, or insufficient privilege results in an
unavailable backend with a reason. Calls that require mutation then throw a typed
`FirewallException`; the code must never silently report that traffic is blocked
when no rule was installed.

Feature detection is reported separately from availability. For the initial
system-wide backends, `systemWide=true` and `processFiltering=false`. WFP can
express application conditions, but that is not advertised until the backend
actually implements them.

## Common Blocking Semantics

For every managed address, the backend installs both directions:

- inbound traffic whose remote/source address matches;
- outbound traffic whose remote/destination address matches.

The address families are handled independently, so malformed or unsupported
IPv6 configuration on one backend cannot silently disable IPv4 enforcement.

## Linux: nftables

The backend binds `libnftables` through FFM and uses its command API. It does not
spawn `nft` or pass untrusted values through a shell.

Each process owns an `inet` table named:

```text
peerbanhelper_firewall_<pid>_<instance-token>
```

The table contains independent input and output base chains and two named sets:

- an `ipv4_addr` set used by source and destination rules;
- an `ipv6_addr` set used by source and destination rules.

Input rules drop matching IPv4/IPv6 sources; output rules drop matching
IPv4/IPv6 destinations. The table and chains carry an accept policy so unrelated
traffic remains untouched.

Mutations are submitted as one libnftables transaction. Startup cleanup lists
tables with the `peerbanhelper_firewall_` prefix, extracts the PID, and removes a
table only when that process is no longer alive. This avoids deleting an active
PeerBanHelper instance. Library absence or missing `CAP_NET_ADMIN` is reported as
unavailable/privilege-required.

## Windows: Windows Filtering Platform

The backend binds `fwpuclnt.dll` through FFM, opens a dynamic WFP engine
session, and adds a process-unique provider, sublayer, and filters. Display names
are human-readable and contain `PeerBanHelper Firewall`.

Filters cover ALE connect and receive-accept layers for IPv4 and IPv6. Remote
address conditions are used for both directions, and the action is block. Each
list mutation runs inside a WFP transaction (`begin`, changes, `commit`), with
`abort` on failure.

The dynamic session is the crash-cleanup mechanism: when the process dies, WFP
releases the session and all objects added during it. Opening the next session
therefore starts without leftover PeerBanHelper filters. Mutations require
elevation and the Windows Filtering Platform service to be running.

## macOS: PF

The current macOS module binds libc through FFM and probes `/dev/pf`. PF is
present, but effective rules require either a referenced PF anchor in the system
ruleset or a signed Network Extension. Neither lifecycle can be installed
non-disruptively by this change.

The backend therefore reports `available=false`, no filtering support, and a
reason. It never modifies the system PF ruleset and never advertises a false
blocking capability. A future macOS implementation must add an operator-managed
anchor or ship a signed Network Extension with a verifiable cleanup contract.

## Lifecycle

1. Platform creation selects and activates the backend.
2. Backend activation performs any startup orphan cleanup before creating the
   current process-owned namespace.
3. A `FirewallManager` registers a shutdown hook immediately after activation so a
   partial startup failure still has a best-effort cleanup path.
4. `PeerBanHelper.shutdown()` calls `close()` before dependent network services
   are torn down; the manager then removes its hook.
5. `close()` deletes native rules, closes engine handles and confined arenas,
   and becomes a no-op on repeated calls.

Crash cleanup is best-effort by nature. WFP dynamic sessions provide OS-driven
teardown. Persistent nftables state is removed on the next startup by the
dead-PID sweep. macOS advertises no native firewall and therefore creates no
state to clean. The capability model documents these differences rather than
claiming that an untrappable process kill can run in-process cleanup.

## Integration

`Platform` gains one `getFirewall()` method returning the singleton firewall
service for that platform. `Main.loadPlatform()` remains the OS selection point.
The implementation is activated from the application startup path and closed
from `PeerBanHelper.shutdown()`.

The capability is internal to the Java application in this change. A later web
API can expose `addresses()` and `capabilities()` without changing the native
backend contract.

## Validation

- Unit tests validate normalization, deduplication, idempotent close,
  unsupported capability reporting, namespace parsing, and WFP ABI layout.
- Linux integration tests require a disposable namespace/container with
  `CAP_NET_ADMIN` and verify IPv4/IPv6 input and output blocking.
- Windows integration tests require elevation and verify provider/filter cleanup
  after graceful exit and process termination.
- macOS remains capability-probe-only and has no integration test that claims
  enforcement.
- Privileged integration tests are opt-in through environment variables and use
  TEST-NET addresses. They have not been run in this development environment.

## Approval and Commit Plan

Each module below is a separate human approval and commit boundary:

1. Firewall value types and public contract.
2. Platform interface and factory/lifecycle skeleton.
3. Linux nftables FFM backend and cleanup.
4. Windows WFP FFM backend and cleanup.
5. macOS PF FFM backend and capability probe.
6. PeerBanHelper lifecycle integration.
7. Unit/integration tests and operator documentation.

The human operator explicitly approved implementation. The final pull request
description must be written manually by a human.
