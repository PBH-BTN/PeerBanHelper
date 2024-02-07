package com.ghostchu.peerbanhelper.module.impl;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.util.List;

public class IPBlackList extends AbstractFeatureModule {
    public IPBlackList(YamlConfiguration profile) {
        super(profile);
    }

    @Override
    public String getName() {
        return "IP Blacklist";
    }

    @Override
    public String getConfigName() {
        return "ip-address-blocker";
    }

    @Override
    public BanResult shouldBanPeer(Torrent torrent, Peer peer) {
        List<String> ips = getConfig().getStringList("ips");
        List<Integer> ports = getConfig().getIntList("ports");
        PeerAddress peerAddress = peer.getAddress();
        if (ports.contains(peerAddress.getPort())) {
            return new BanResult(true, "在限制的端口范围内");
        }
        for (String ip : ips) {
            if (peerAddress.getIp().equals(ip.trim())) {
                return new BanResult(true, "匹配 IP 规则：" + ip);
            }
            try {
                IPAddress address = new IPAddressString(ip).toAddress();
                if (address.contains(new IPAddressString(peerAddress.getIp()).toAddress())) {
                    return new BanResult(true, "匹配 IP 规则：" + ip);
                }
            } catch (AddressStringException ignored) {}
        }
        return new BanResult(false, "无匹配");
    }
}
