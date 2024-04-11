package com.ghostchu.peerbanhelper.module.impl;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.config.section.ModuleAutoRangeBanConfigSection;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddress;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ExecutorService;

@Slf4j
public class AutoRangeBan extends AbstractFeatureModule<ModuleAutoRangeBanConfigSection> {

    private final PeerBanHelperServer server;

    public AutoRangeBan(PeerBanHelperServer server, ModuleAutoRangeBanConfigSection section) {
        super(section);
        this.server = server;
    }

    @Override
    public String getName() {
        return "Auto Range Ban";
    }

    @Override
    public BanResult shouldBanPeer(Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        if(StringUtils.isEmpty(peer.getPeerId())){
            return new BanResult(this,PeerAction.NO_ACTION, "Waiting for Bittorrent handshaking.");
        }
        IPAddress peerAddress = peer.getAddress().getAddress().withoutPrefixLength();

        int ipv4Prefix = getConfig().getIpv4();
        int ipv6Prefix = getConfig().getIpv4();

        for (PeerAddress bannedPeer : server.getBannedPeers().keySet()) {
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
