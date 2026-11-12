# Native Firewall Tasks

## Planning

- [x] Create the `native-firewall` branch.
- [x] Inspect the existing Java platform abstraction and lifecycle.
- [x] Draft the proposal, design, support model, and approval gates.
- [ ] Human approves firewall API value types and public contract.
- [ ] Human approves platform contract and lifecycle skeleton.
- [ ] Human approves Linux nftables FFM backend.
- [ ] Human approves Windows WFP FFM backend.
- [ ] Human approves macOS PF FFM backend.
- [ ] Human approves PeerBanHelper lifecycle integration.
- [ ] Human approves tests and operator documentation.

## Firewall API

- [ ] Add immutable capability and operation result types.
- [ ] Add the lifecycle-scoped `Firewall` contract.
- [ ] Add address normalization and deduplication.
- [ ] Add transactional add, remove, replace, clear, and query semantics.
- [ ] Add typed errors for unavailable, unsupported, and privileged operations.
- [ ] Add an unsupported backend that reports exact capabilities.

## Platform Integration

- [ ] Expose the firewall singleton through `Platform`.
- [ ] Select the backend from the OS and native capability probe.
- [ ] Activate firewall cleanup early enough to cover partial startup.
- [ ] Close the backend from application shutdown.
- [ ] Ensure repeated close and partial initialization are safe.

## Linux Backend

- [ ] Bind required `libnftables` functions with FFM.
- [ ] Create a process-unique `peerbanhelper` nftables table.
- [ ] Add input/output chains and IPv4/IPv6 sets.
- [ ] Implement atomic add, remove, replace, and clear.
- [ ] Implement query from the committed managed snapshot.
- [ ] Sweep dead-process nftables namespaces at startup.
- [ ] Report missing library, privilege, or capability accurately.

## Windows Backend

- [ ] Bind WFP engine, transaction, provider, sublayer, filter, and enum APIs.
- [ ] Open a dynamic WFP session.
- [ ] Add IPv4/IPv6 inbound and outbound block filters.
- [ ] Implement atomic add, remove, replace, and clear.
- [ ] Implement query from the committed managed snapshot.
- [ ] Sweep dead-process PeerBanHelper objects at startup.
- [ ] Report elevation and service availability accurately.

## macOS Backend

- [ ] Probe the PF device and required ioctl ABI.
- [ ] Create a process-unique PeerBanHelper PF anchor.
- [ ] Install IPv4/IPv6 input and output block rules.
- [ ] Implement atomic list mutations and query.
- [ ] Remove the anchor on close and sweep dead-process anchors at startup.
- [ ] Return unsupported with a reason when the ABI or privilege is unavailable.

## Verification

- [ ] Add unit tests for all non-privileged logic.
- [ ] Add privileged Linux integration coverage.
- [ ] Add privileged Windows integration coverage.
- [ ] Add macOS PF integration coverage on a disposable host.
- [ ] Verify IPv4 and IPv6 inbound blocking.
- [ ] Verify IPv4 and IPv6 outbound blocking.
- [ ] Verify graceful-exit cleanup.
- [ ] Verify next-start cleanup after forced process termination.
- [ ] Verify unrelated firewall rules are not modified.
- [ ] Document required privileges, capability fields, and crash-cleanup limits.
- [ ] Human reviews the final diff and writes the pull request description.
