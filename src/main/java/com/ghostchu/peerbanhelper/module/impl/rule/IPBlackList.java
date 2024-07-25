package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.Address;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class IPBlackList extends AbstractRuleFeatureModule {
    private List<IPAddress> ips;
    private List<Integer> ports;
    private List<Long> asns;
    private List<String> regions;
    private List<String> netTypes;
    @Autowired
    private JavalinWebContainer webContainer;
    private long banDuration;

    @Override
    public @NotNull String getName() {
        return "IP Blacklist";
    }

    @Override
    public @NotNull String getConfigName() {
        return "ip-address-blocker";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onEnable() {
        reloadConfig();
        webContainer.javalin()
                .get("/api/modules/" + getConfigName(), this::handleWebAPI, Role.USER_READ);
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    private void handleWebAPI(Context ctx) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ip", ips.stream().map(Address::toString).toList());
        map.put("port", ports);
        map.put("asn", asns);
        map.put("region", regions);
        ctx.status(HttpStatus.OK);
        ctx.json(map);
    }

    @Override
    public void onDisable() {

    }

    private void reloadConfig() {
        this.banDuration = getConfig().getLong("ban-duration", 0);
        this.ips = new ArrayList<>();
        for (String s : getConfig().getStringList("ips")) {
            IPAddress ipAddress = IPAddressUtil.getIPAddress(s);
            this.ips.add(ipAddress);
        }
        this.ports = getConfig().getIntList("ports");
        this.regions = getConfig().getStringList("regions");
        this.asns = getConfig().getLongList("asns");
        this.netTypes = new ArrayList<>();
        // GeoCN 字段名就是中文
        if (getConfig().getBoolean("net-type.wideband")) {
            this.netTypes.add("宽带");
        }
        if (getConfig().getBoolean("net-type.base-station")) {
            this.netTypes.add("基站");
        }
        if (getConfig().getBoolean("net-type.government-and-enterprise-line")) {
            this.netTypes.add("政企专线");
        }
        if (getConfig().getBoolean("net-type.business-platform")) {
            this.netTypes.add("业务平台");
        }
        if (getConfig().getBoolean("net-type.backbone-network")) {
            this.netTypes.add("骨干网");
        }
        if (getConfig().getBoolean("net-type.ip-private-network")) {
            this.netTypes.add("IP专网");
        }
        if (getConfig().getBoolean("net-type.internet-cafe")) {
            this.netTypes.add("网吧");
        }
        if (getConfig().getBoolean("net-type.iot")) {
            this.netTypes.add("物联网");
        }
        if (getConfig().getBoolean("net-type.datacenter")) {
            this.netTypes.add("数据中心");
        }
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        return getCache().readCacheButWritePassOnly(this, peer.getPeerAddress().getIp(), () -> {
            PeerAddress peerAddress = peer.getPeerAddress();
            if (ports.contains(peerAddress.getPort())) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.IP_BLACKLIST_PORT_RULE), new TranslationComponent(Lang.MODULE_IBL_MATCH_PORT, String.valueOf(peerAddress.getPort())));
            }
            IPAddress pa = IPAddressUtil.getIPAddress(peerAddress.getIp());
            for (IPAddress ra : ips) {
                if (ra.equals(pa) || ra.contains(pa)) {
                    return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.IP_BLACKLIST_CIDR_RULE, ra.toString()), new TranslationComponent(Lang.MODULE_IBL_MATCH_IP, ra.toString()));
                }
            }
            try {
                CheckResult ipdbResult = checkIPDB(torrent, peer, ruleExecuteExecutor);
                if (ipdbResult.action() != PeerAction.NO_ACTION) {
                    return ipdbResult;
                }
            } catch (Exception e) {
                log.error(tlUI(Lang.MODULE_IBL_EXCEPTION_GEOIP), e);
            }
            return pass();
        }, true);
    }

    private CheckResult checkIPDB(Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
        if (regions.isEmpty() && asns.isEmpty()) {
            return pass();
        }
        var geoData = getServer().queryIPDB(peer.getPeerAddress()).geoData().get();
        if (geoData == null) {
            return pass();
        }
        if (!asns.isEmpty() && geoData.getAs() != null) {
            Long asn = geoData.getAs().getNumber();
            if (asns.contains(asn)) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.IP_BLACKLIST_ASN_RULE, String.valueOf(asn)), new TranslationComponent(Lang.MODULE_IBL_MATCH_ASN, String.valueOf(asn)));
            }
        }
        if (!regions.isEmpty() && geoData.getCountry() != null) {
            String iso = geoData.getCountry().getIso();
            if (regions.contains(iso)) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.IP_BLACKLIST_REGION_RULE, iso), new TranslationComponent(Lang.MODULE_IBL_MATCH_REGION, iso));
            }
        }
        if (!netTypes.isEmpty() && geoData.getNetwork() != null && geoData.getNetwork().getNetType() != null) {
            String netType = geoData.getNetwork().getNetType();
            if (netTypes.contains(netType)) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.IP_BLACKLIST_NETTYPE_RULE, netType), new TranslationComponent(Lang.MODULE_IBL_MATCH_IP, netType));
            }
        }
        return pass();
    }

}
