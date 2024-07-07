package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
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

@Slf4j
@Component
public class IPBlackList extends AbstractRuleFeatureModule {
    private List<IPAddress> ips;
    private List<Integer> ports;
    private List<Long> asns;
    private List<String> regions;
    @Autowired
    private JavalinWebContainer webContainer;

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
        this.ips = new ArrayList<>();
        for (String s : getConfig().getStringList("ips")) {
            IPAddress ipAddress = IPAddressUtil.getIPAddress(s);
            this.ips.add(ipAddress);
        }
        this.ports = getConfig().getIntList("ports");
        this.regions = getConfig().getStringList("regions");
        this.asns = getConfig().getLongList("asns");

    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        return getCache().readCache(this, peer.getPeerAddress().getIp(), () -> {
            PeerAddress peerAddress = peer.getPeerAddress();
            if (ports.contains(peerAddress.getPort())) {
                return new CheckResult(getClass(), PeerAction.BAN, String.valueOf(peerAddress.getPort()), String.format(Lang.MODULE_IBL_MATCH_PORT, peerAddress.getPort()));
            }
            IPAddress pa = IPAddressUtil.getIPAddress(peerAddress.getIp());
            for (IPAddress ra : ips) {
                if (ra.equals(pa) || ra.contains(pa)) {
                    return new CheckResult(getClass(), PeerAction.BAN, ra.toString(), String.format(Lang.MODULE_IBL_MATCH_IP, ra));
                }
            }
            try {
                CheckResult ipdbResult = checkIPDB(torrent, peer, ruleExecuteExecutor);
                if (ipdbResult.action() != PeerAction.NO_ACTION) {
                    return ipdbResult;
                }
            } catch (Exception e) {
                log.error(Lang.MODULE_IBL_EXCEPTION_GEOIP, e);
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
                return new CheckResult(getClass(), PeerAction.BAN, String.valueOf(asn), String.format(Lang.MODULE_IBL_MATCH_ASN, asn));
            }
        }
        if (!regions.isEmpty() && geoData.getCountry() != null) {
            String iso = geoData.getCountry().getIso();
            if (regions.contains(iso)) {
                return new CheckResult(getClass(), PeerAction.BAN, String.valueOf(iso), String.format(Lang.MODULE_IBL_MATCH_REGION, iso));
            }
        }
        return new CheckResult(getClass(), PeerAction.NO_ACTION, "N/A", "N/A");
    }

}
