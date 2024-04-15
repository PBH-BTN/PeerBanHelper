package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
@Slf4j
public class AutoRangeBan extends AbstractFeatureModule {
    private int ipv4Prefix;
    private int ipv6Prefix;

    public AutoRangeBan(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public @NotNull String getName() {
        return "Auto Range Ban";
    }

    @Override
    public @NotNull String getConfigName() {
        return "auto-range-ban";
    }

    @Override
    public boolean needCheckHandshake() {
        return false;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onEnable() {
        reloadConfig();
    }

    @Override
    public void onDisable() {

    }

    private void reloadConfig() {
        this.ipv4Prefix = getConfig().getInt("ipv4");
        this.ipv6Prefix = getConfig().getInt("ipv6");
    }



    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        if(peer.getPeerId() == null || peer.getPeerId().isEmpty()){
            return new BanResult(this,PeerAction.NO_ACTION, "Waiting for Bittorrent handshaking.");
        }
        IPAddress peerAddress = peer.getAddress().getAddress().withoutPrefixLength();
        for (PeerAddress bannedPeer : getServer().getBannedPeers().keySet()) {
            IPAddress bannedAddress = bannedPeer.getAddress().toIPAddress().withoutPrefixLength();

            String addressType = "UNKNOWN";
            if (bannedAddress.isIPv4()) {
                addressType = "IPv4/" + ipv4Prefix;
                bannedAddress = bannedAddress.toPrefixBlock(ipv4Prefix);
            }
            if (bannedAddress.isIPv6()) {
                addressType = "IPv6/" + ipv6Prefix;
                bannedAddress = bannedAddress.toPrefixBlock(ipv6Prefix);
            }
            if (bannedAddress.contains(peerAddress)) {
                return new BanResult(this,PeerAction.BAN, String.format(Lang.ARB_BANNED, peerAddress, bannedPeer.getAddress(), addressType));
            }
        }
        return new BanResult(this,PeerAction.NO_ACTION, "All ok!");
    }



}
