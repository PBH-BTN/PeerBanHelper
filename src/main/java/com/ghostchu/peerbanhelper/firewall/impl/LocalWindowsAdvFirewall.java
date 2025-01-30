package com.ghostchu.peerbanhelper.firewall.impl;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.firewall.Firewall;
import inet.ipaddr.IPAddress;

import java.io.IOException;
import java.util.Collections;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class LocalWindowsAdvFirewall extends CommandBasedImpl implements Firewall {
    private static final String PBH_GUID = new UUID(7355608L, 1145141919810L).toString();
    private static final String CMD_TEST_START = """
            New-NetFirewallRule -Id "peerbanhelper-test" -DisplayName "PeerBanHelperTest"
            """;
    private static final String CMD_TEST_END = """
            New-NetFirewallRule -Id "peerbanhelper-test" -DisplayName "PeerBanHelperTest"
            """;
    private static final String CMD_REMOVE_FIREWALL_RULE = """
            Remove-NetFirewallDynamicKeywordAddress -Id {%s}
            """;
    private static final String CMD_NEW_INBOUND_FIREWALL_RULE = """
            New-NetFirewallRule -Id "peerbanhelper-inbound" -DisplayName "PeerBanHelper AdvFirewall Filter (Inbound)" -Direction Inbound -Action Block -RemoteDynamicKeywordAddresses "{%s}"
            """;
    private static final String CMD_REMOVE_INBOUND_FIREWALL_RULE = """
            Remove-NetFirewallRule -Id "peerbanhelper-inbound"
            """;
    private static final String CMD_NEW_OUTBOUND_FIREWALL_RULE = """
            New-NetFirewallRule Id "peerbanhelper-outbound" -DisplayName “PeerBanHelper AdvFirewall Filter (Outbound)” -Direction Outbound -Action Block -RemoteDynamicKeywordAddresses "{%s}"
            """;
    private static final String CMD_REMOVE_OUTBOUND_FIREWALL_RULE = """
            Remove-NetFirewallRule -Id "peerbanhelper-outbound"
            """;
    private static final String CMD_CREATE_DYNAMIC_FIREWALL_KEYWORD = """
            New-NetFirewallDynamicKeywordAddress -Id "{%s}" -Keyword "PBH-BanList" -Addresses "%s"
            """;
    private static final String CMD_DELETE_DYNAMIC_FIREWALL_KEYWORD = """
            Remove-NetFirewallDynamicKeywordAddress -Id "{%s}"
            """;
    private final PeerBanHelperServer server;


    public LocalWindowsAdvFirewall(PeerBanHelperServer server) {
        this.server = server;
    }

    @Override
    public String getName() {
        return "Windows AdvFirewall (DynamicKeywordAddress)";
    }

    @Override
    public boolean isApplicable() {
        try {
            invokeCommand(CMD_TEST_START, Collections.emptyMap());
            invokeCommand(CMD_TEST_END, Collections.emptyMap());
        } catch (IOException | ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
        return false;
    }

    @Override
    public boolean ban(IPAddress address) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        invokeCommand(String.format(CMD_DELETE_DYNAMIC_FIREWALL_KEYWORD, PBH_GUID), Collections.emptyMap());
        invokeCommand(String.format(CMD_CREATE_DYNAMIC_FIREWALL_KEYWORD, PBH_GUID, getAllAddress()), Collections.emptyMap());
        return true;
    }

    @Override
    public boolean unban(IPAddress address) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        invokeCommand(String.format(CMD_DELETE_DYNAMIC_FIREWALL_KEYWORD, PBH_GUID), Collections.emptyMap());
        invokeCommand(String.format(CMD_CREATE_DYNAMIC_FIREWALL_KEYWORD, PBH_GUID, getAllAddress()), Collections.emptyMap());
        return true;
    }

    @Override
    public boolean reset() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        invokeCommand(String.format(CMD_DELETE_DYNAMIC_FIREWALL_KEYWORD, PBH_GUID), Collections.emptyMap());
        invokeCommand(CMD_REMOVE_INBOUND_FIREWALL_RULE, Collections.emptyMap());
        invokeCommand(CMD_REMOVE_OUTBOUND_FIREWALL_RULE, Collections.emptyMap());
        invokeCommand(String.format(CMD_NEW_INBOUND_FIREWALL_RULE, PBH_GUID), Collections.emptyMap());
        invokeCommand(String.format(CMD_NEW_OUTBOUND_FIREWALL_RULE, PBH_GUID), Collections.emptyMap());
        invokeCommand(String.format(CMD_CREATE_DYNAMIC_FIREWALL_KEYWORD, PBH_GUID, getAllAddress()), Collections.emptyMap());
        return true;
    }

    @Override
    public boolean load() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        return reset();
    }

    @Override
    public boolean unload() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        invokeCommand(String.format(CMD_DELETE_DYNAMIC_FIREWALL_KEYWORD, PBH_GUID), Collections.emptyMap());
        invokeCommand(CMD_REMOVE_OUTBOUND_FIREWALL_RULE, Collections.emptyMap());
        invokeCommand(CMD_REMOVE_INBOUND_FIREWALL_RULE, Collections.emptyMap());
        return true;
    }

    private String getAllAddress() {
        StringJoiner joiner = new StringJoiner(",");
        server.getBannedPeers().keySet().forEach(pa -> joiner.add(pa.getAddress().toString()));
        return joiner.toString();
    }
}
