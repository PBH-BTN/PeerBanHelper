# Native Firewall Integration

## Why

PeerBanHelper currently applies bans inside its own workflows. A native
firewall list would enforce the same addresses at the operating-system layer,
covering inbound and outbound traffic even when the traffic does not pass
through the downloader's peer-management path.

The list must remain scoped to one PeerBanHelper process. It must not survive a
normal restart, and stale OS objects left by a crash must be removed on the next
start without disturbing active PeerBanHelper instances.

## What Changes

- Add a small `platform.firewall` contract with capabilities, query, add,
  remove, replace, clear, and close operations.
- Expose the firewall backend through the existing platform abstraction.
- Add FFM-based backends:
  - Linux: nftables through `libnftables`.
  - Windows: Windows Filtering Platform through `fwpuclnt.dll`.
  - macOS: PF through its native device/ioctl interface when its ABI is
    available; otherwise report unsupported without silently weakening policy.
- Block both inbound and outbound traffic for IPv4 and IPv6 addresses.
- Report feature support explicitly, including system-wide filtering,
  inbound/outbound support, address families, privilege requirements, process
  filtering, and crash-cleanup behavior.
- Use process-unique but human-readable `peerbanhelper` namespace names so
  startup cleanup can identify orphaned objects safely.

## Capabilities

### New Capabilities

- `native-firewall`: lifecycle-scoped native IP blocking with feature discovery
  and transactional list management.

### Modified Capabilities

None.

## Impact

- Java backend only; the Vue.js frontend and public HTTP API are unchanged.
- Native firewall mutation generally requires administrator or root
  privileges, or the equivalent Linux capability such as `CAP_NET_ADMIN`.
- The initial implementation manages exact IP addresses, not CIDR ranges.
- Tests that mutate a real firewall require elevated integration environments;
  unit tests must not alter the host firewall.

## Non-goals

- Persisting the address list across PeerBanHelper restarts.
- Replacing the existing downloader-level ban pipeline.
- Adding a web UI or HTTP controller in this change.
- Claiming per-process filtering when a backend does not implement it.

## Delivery Gate

The repository's `AGENTS.md` prohibits agent-generated backend Java changes
without explicit human approval for each implementation part. This proposal and
the accompanying design are planning artifacts only. Java source generation
must stop at the approval gate and proceed module by module only after the human
operator explicitly approves each module.
