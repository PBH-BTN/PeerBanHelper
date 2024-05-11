package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.ipdb.IPDB;
import com.ghostchu.peerbanhelper.ipdb.ipproxy.ProxyResult;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ip2location.IPResult;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

@Getter
@Slf4j
public class IPBlackList extends AbstractRuleFeatureModule {
    private List<IPAddress> ips;
    private List<Integer> ports;
    private Map<Object, Map<String, AtomicLong>> counter;

    public IPBlackList(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public @NotNull String getName() {
        return "IP Blacklist";
    }

    @Override
    public @NotNull String getConfigName() {
        return "ip-address-blocker";
    }

    @Override
    public boolean isCheckCacheable() {
        return true;
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
        this.ips = new ArrayList<>();
        for (String s : getConfig().getStringList("ips")) {
            IPAddress ipAddress = new IPAddressString(s).getAddress();
            if (ipAddress != null) {
                if (ipAddress.isIPv4Convertible()) {
                    ipAddress = ipAddress.toIPv4();
                }
                this.ips.add(ipAddress);
            }
        }
        this.ports = getConfig().getIntList("ports");
        this.counter = new LinkedHashMap<>(ips.size() + ports.size());
    }

    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        PeerAddress peerAddress = peer.getAddress();
        debug(peerAddress);
        if (ports.contains(peerAddress.getPort())) {
            return new BanResult(this, PeerAction.BAN, String.valueOf(peerAddress.getPort()), String.format(Lang.MODULE_IBL_MATCH_PORT, peerAddress.getPort()));
        }
        IPAddress pa = IPAddressUtil.getIPAddress(peerAddress.getIp());
        if (pa.isIPv4Convertible()) {
            pa = pa.toIPv4();
        }
        for (IPAddress ra : ips) {
            if (ra.isIPv4() != pa.isIPv4()) { // 在上面的规则处统一进行过转换，此处可直接进行检查
                continue;
            }
            if (ra.equals(pa) || ra.contains(pa)) {
                return new BanResult(this, PeerAction.BAN, ra.toString(), String.format(Lang.MODULE_IBL_MATCH_IP, ra));
            }
        }
        return new BanResult(this, PeerAction.NO_ACTION, "N/A", "No matches");
    }

    private void debug(PeerAddress peerAddress) {
        IPAddress address = peerAddress.getAddress();
        IPDB ipdb = getServer().getIpdb();
        if (ipdb == null) return;
        IPResult geoip = ipdb.queryGeoIP(address);
        ProxyResult proxy = ipdb.queryProxy(address);
        if (geoip != null) {
            log.info("GeoIP: {} - {}", address, geoip);
        }
        if (geoip != null) {
            log.info("Proxy: {} - {}", address, proxy);
        }


        String[] ips = """
                1.180.24.48
                1.180.24.49
                1.180.24.50
                1.180.24.51
                1.180.24.52
                1.180.24.53
                1.180.24.54
                1.180.24.55
                1.180.24.56
                1.180.24.57
                1.180.24.58
                1.180.24.77
                1.180.24.78
                1.180.24.85
                1.180.24.86
                1.180.24.87
                1.180.24.95
                1.180.24.96
                1.180.24.97
                1.180.24.100
                1.180.24.220
                1.180.24.222
                1.180.24.242
                1.180.24.245
                1.180.25.17
                1.180.25.84
                1.180.25.94
                1.180.25.95
                1.180.25.97
                1.180.25.202
                1.180.25.203
                1.180.25.210
                1.180.25.246
                1.180.25.247
                1.180.25.248
                36.102.218.138
                36.102.218.146
                36.102.218.147
                36.102.218.148
                36.102.218.149
                36.102.218.150
                36.102.218.171
                36.102.218.172
                36.102.218.173
                36.102.218.174
                36.102.218.175
                36.102.218.176
                36.102.218.177
                36.102.218.178
                36.102.218.179
                36.102.218.195
                36.102.218.196
                36.102.218.197
                36.102.218.216
                36.102.218.217
                36.102.218.222
                36.102.218.223
                112.45.16.196
                112.45.16.197
                112.45.16.201
                112.45.16.202
                112.45.16.203
                112.45.16.204
                112.45.16.205
                112.45.16.216
                112.45.16.217
                112.45.20.227
                112.45.20.228
                112.45.20.229
                112.45.20.233
                112.45.20.234
                112.45.20.235
                112.45.20.236
                112.45.20.248
                112.45.20.249
                112.45.20.250
                112.45.20.251
                112.45.20.252
                119.53.104.244
                119.53.105.75
                119.53.105.84
                119.53.106.133
                119.53.107.25
                119.53.107.69
                119.53.108.6
                119.53.108.84
                119.53.108.160
                119.53.109.13
                119.53.109.70
                119.53.109.104
                119.53.110.20
                119.53.110.80
                119.53.110.149
                119.53.110.229
                119.53.111.5
                119.53.111.74
                119.53.111.190
                119.53.112.13
                122.224.33.14
                122.224.33.15
                122.224.33.16
                122.224.33.17
                123.184.152.39
                123.184.152.81
                123.184.152.82
                123.184.152.83
                123.184.152.84
                123.184.152.85
                123.184.152.86
                123.184.152.87
                123.184.152.88
                123.184.152.89
                123.184.152.90
                123.184.152.96
                123.184.152.97
                123.184.152.98
                123.184.152.99
                123.184.152.100
                123.184.152.101
                123.184.152.102
                123.184.152.103
                123.184.152.104
                123.184.152.132
                123.184.152.133
                123.184.152.242
                123.184.152.243
                123.184.152.244
                123.184.152.245
                123.184.152.246
                123.184.152.247
                123.184.152.248
                123.184.152.249
                123.187.27.190
                123.187.27.194
                123.187.27.198
                123.187.27.202
                123.187.27.206
                123.187.27.210
                123.187.27.214
                123.187.27.218
                123.187.27.222
                123.187.27.226
                123.187.27.230
                123.187.27.234
                123.187.27.238
                123.187.27.242
                123.187.27.246
                123.187.27.250
                123.187.27.254
                123.187.29.6
                123.187.29.10
                123.187.29.14
                123.187.29.18
                123.187.29.22
                123.187.29.26
                123.187.29.30
                123.187.29.34
                123.187.29.38
                123.187.29.42
                123.187.29.46
                175.19.1.149
                175.19.2.27
                175.19.2.51
                175.19.2.55
                175.19.2.117
                175.19.2.195
                175.19.2.213
                175.19.2.242
                175.19.2.243
                175.19.2.249
                175.19.3.2
                175.19.3.8
                175.19.3.39
                175.19.3.65
                175.19.3.78
                175.19.3.83
                175.19.3.128
                175.19.3.200
                175.19.3.209
                175.19.3.244
                175.19.8.9
                175.19.8.13
                175.19.8.27
                175.19.8.47
                175.19.8.77
                175.19.8.87
                175.19.8.106
                175.19.8.136
                175.19.8.137
                175.19.8.141
                175.19.8.151
                175.19.8.173
                175.19.8.176
                175.19.9.71
                175.19.9.96
                175.19.9.115
                175.19.9.165
                175.19.9.178
                175.19.9.190
                175.19.9.212
                175.19.10.103
                175.19.11.229
                211.103.112.139
                218.7.138.16
                218.7.138.17
                218.7.138.18
                218.7.138.19
                218.7.138.20
                218.7.138.21
                218.7.138.22
                218.7.138.23
                218.7.138.24
                218.7.138.25
                218.7.138.26
                218.7.138.27
                218.7.138.28
                218.7.138.29
                221.9.12.96
                221.9.12.117
                221.9.12.118
                221.9.12.178
                221.9.13.92
                221.9.14.10
                221.9.14.143
                221.9.14.226
                221.9.15.1
                221.9.16.221
                221.9.17.220
                221.9.18.18
                221.9.18.78
                221.9.19.120
                221.11.96.66
                221.11.96.67
                221.11.96.68
                221.11.96.71
                221.11.96.72
                221.11.96.73
                221.11.96.74
                221.203.3.12
                221.203.3.15
                221.203.3.16
                221.203.3.38
                221.203.6.47
                221.203.6.48
                221.203.6.49
                221.203.6.50
                221.203.6.51
                221.203.6.52
                221.203.6.53
                221.203.6.54
                221.203.6.55
                221.203.6.56
                221.203.6.57
                221.203.6.58
                221.203.6.60
                221.203.6.61
                221.203.6.62
                """.split("\n");
        for (String ip : ips) {
            log.info("Test {}: {}", ip, ipdb.queryGeoIP(address));
        }
    }

}
