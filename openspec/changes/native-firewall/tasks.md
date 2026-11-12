# Native Firewall Tasks

## Planning

- [x] Create the `native-firewall` branch.
- [x] Inspect the existing Java platform abstraction and lifecycle.
- [x] Draft the proposal, design, support model, and approval gates.
- [x] Human approves firewall API value types and public contract.
- [x] Human approves platform contract and lifecycle skeleton.
- [x] Human approves Linux nftables FFM backend.
- [x] Human approves Windows WFP FFM backend.
- [x] Human approves macOS PF capability/probe module.
- [x] Human approves PeerBanHelper lifecycle integration.
- [x] Human approves tests and operator documentation.

## Firewall API

- [x] Add immutable capability types.
- [x] Add the lifecycle-scoped `Firewall` contract.
- [x] Add address normalization and deduplication.
- [x] Add transactional add, remove, replace, clear, and query semantics.
- [x] Add typed errors for unavailable and native failures.
- [x] Add an unsupported backend that reports exact capabilities.

## Platform Integration

- [x] Expose the firewall singleton through `Platform`.
- [x] Select the backend from the OS and native capability probe.
- [x] Activate firewall cleanup early enough to cover partial startup.
- [x] Close the backend from application shutdown.
- [x] Ensure repeated close and partial initialization are safe.

## Linux Backend

- [x] Bind required `libnftables` functions with FFM.
- [x] Create a process-unique `peerbanhelper` nftables table.
- [x] Add input/output chains and IPv4/IPv6 sets.
- [x] Implement atomic add, remove, replace, and clear.
- [x] Implement query from the committed managed snapshot.
- [x] Sweep dead-process nftables namespaces at startup.
- [x] Report missing library or capability accurately.

## Windows Backend

- [x] Bind WFP engine, transaction, provider, sublayer, and filter APIs.
- [x] Open a dynamic WFP session for crash cleanup.
- [x] Add IPv4/IPv6 inbound and outbound block filters.
- [x] Implement atomic add, remove, replace, and clear.
- [x] Implement query from the committed managed snapshot.
- [x] Report elevation and service availability accurately.

## macOS Backend

- [x] Probe the PF device through FFM.
- [ ] Create a process-unique PeerBanHelper PF anchor.
- [ ] Install IPv4/IPv6 input and output block rules.
- [ ] Implement atomic list mutations and query.
- [ ] Remove the anchor on close and sweep dead-process anchors at startup.
- [x] Return unsupported with a reason when a safe anchor lifecycle is unavailable.

## Verification

- [x] Add unit tests for all non-privileged logic.
- [x] Add opt-in privileged Linux integration coverage.
- [x] Add opt-in privileged Windows integration coverage.
- [ ] Add macOS PF integration coverage on a disposable host.
- [ ] Verify IPv4 and IPv6 inbound blocking.
- [ ] Verify IPv4 and IPv6 outbound blocking.
- [ ] Verify graceful-exit cleanup.
- [ ] Verify next-start cleanup after forced process termination.
- [ ] Verify unrelated firewall rules are not modified.
- [x] Document required privileges, capability fields, and crash-cleanup limits.
- [ ] Human reviews the final diff and writes the pull request description.
