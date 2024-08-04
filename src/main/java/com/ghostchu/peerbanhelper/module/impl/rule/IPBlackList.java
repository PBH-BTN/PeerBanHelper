package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.module.impl.webapi.common.SlimMsg;
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

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutorService;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class IPBlackList extends AbstractRuleFeatureModule {
    private Set<IPAddress> ips;
    private Set<Integer> ports;
    private Set<Long> asns;
    private Set<String> regions;
    private Set<String> netTypes;
    @Autowired
    private JavalinWebContainer webContainer;
    private long banDuration;
    private Set<String> cities;

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
                .get("/api/modules/ipblacklist", this::handleWebAPI, Role.USER_READ)
                .post("/api/modules/ipblacklist/ip/test", this::handleIPTest, Role.USER_WRITE)
                .put("/api/modules/ipblacklist/ip", this::handleIPPut, Role.USER_WRITE)
                .delete("/api/modules/ipblacklist/ip", this::handleIPDelete, Role.USER_WRITE)
                .put("/api/modules/ipblacklist/port", this::handlePort, Role.USER_WRITE)
                .delete("/api/modules/ipblacklist/port", this::handlePortDelete, Role.USER_WRITE)
                .put("/api/modules/ipblacklist/asn", this::handleASN, Role.USER_WRITE)
                .delete("/api/modules/ipblacklist/asn", this::handleASNDelete, Role.USER_WRITE)
                .put("/api/modules/ipblacklist/region", this::handleRegion, Role.USER_WRITE)
                .delete("/api/modules/ipblacklist/region", this::handleRegionDelete, Role.USER_WRITE)
                .put("/api/modules/ipblacklist/cities", this::handleCities, Role.USER_WRITE)
                .delete("/api/modules/ipblacklist/cities", this::handleCitiesDelete, Role.USER_WRITE)
        ;//.patch("/api/modules/ipblacklist/nettype", this::handleNetType, Role.USER_WRITE);
    }

    private void handleCitiesDelete(Context context) throws IOException {
        if (cities.removeIf(cities -> cities.equals(context.body()))) {
            context.status(HttpStatus.OK);
        } else {
            context.status(HttpStatus.NOT_FOUND);
        }
        saveConfig();
    }

    private void handleCities(Context context) throws IOException {
        cities.add(context.body());
        saveConfig();
    }

    private void handleNetTypeDelete(Context context) throws IOException {
        if (netTypes.removeIf(netType -> netType.equals(context.body()))) {
            context.status(HttpStatus.OK);
        } else {
            context.status(HttpStatus.NOT_FOUND);
        }
        saveConfig();
    }

    private void handleRegionDelete(Context context) throws IOException {
        if (regions.removeIf(region -> region.equals(context.body()))) {
            context.status(HttpStatus.OK);
        } else {
            context.status(HttpStatus.NOT_FOUND);
        }
        saveConfig();
    }

    private void handleASNDelete(Context context) throws IOException {
        if (asns.removeIf(p -> p == Long.parseLong(context.body()))) {
            context.status(HttpStatus.OK);
        } else {
            context.status(HttpStatus.NOT_FOUND);
        }
        saveConfig();
    }

    private void handlePortDelete(Context context) throws IOException {
        if (ports.removeIf(p -> p == Integer.parseInt(context.body()))) {
            context.status(HttpStatus.OK);
        } else {
            context.status(HttpStatus.NOT_FOUND);
        }
        saveConfig();
    }

    private void handleIPDelete(Context context) throws IOException {
        if (ips.removeIf(ipAddress -> ipAddress.toNormalizedString().equals(context.body()))) {
            context.status(HttpStatus.OK);
        } else {
            context.status(HttpStatus.NOT_FOUND);
        }
        saveConfig();
    }

    private void handleNetType(Context ctx) {

    }

    private void handleRegion(Context ctx) throws IOException {
        regions.add(ctx.body());
        saveConfig();
    }

    private void handleASN(Context ctx) throws IOException {
        long asn = Long.parseLong(ctx.body());
        asns.add(asn);
        saveConfig();
    }


    private void handlePort(Context ctx) throws IOException {
        UserPortAddRequest req = ctx.bodyAsClass(UserPortAddRequest.class);
        if (req.from() > req.to() || req.from() < 1 || req.to() > 65535) {
            throw new IllegalArgumentException(tl(locale(ctx), Lang.IP_BLACKLIST_PUT_PORT_INVALID_RANGE));
        }
        for (int i = req.from(); i <= req.to(); i++) {
            ports.add(i);
        }
        ctx.status(HttpStatus.CREATED);
        saveConfig();
    }

//    private void handlePortTest(Context ctx) {
//        UserPortAddRequest req = ctx.bodyAsClass(UserPortAddRequest.class);
//        if (req.from() > req.to() || req.from() < 1 || req.to() > 65535) {
//            ctx.status(HttpStatus.BAD_REQUEST);
//            ctx.json(new UserPortTestResult(false, tl(locale(ctx), Lang.IP_BLACKLIST_PUT_PORT_INVALID_RANGE), 0, null));
//            return;
//        }
//        int count = req.from() - req.to();
//        if (req.from() == req.to()) {
//            count += 1;
//        } else {
//            count += 2;
//        }
//        if (req.from() < 1024) {
//            ctx.json(new UserPortTestResult(true, null, count, tl(locale(ctx), Lang.IP_BLACKLIST_PUT_PORT_PRIVILEGED_PORT_TIPS)));
//        } else {
//            ctx.json(new UserPortTestResult(true, null, count, null));
//        }
//    }

    private void handleIPPut(Context context) throws IOException {
        IPAddress ipAddress = parseIPAddressFromReq(context);
        ips.add(ipAddress);
        context.status(HttpStatus.CREATED);
        saveConfig();
    }

    private void handleIPTest(Context context) throws IOException {
        IPAddress ipAddress = parseIPAddressFromReq(context);
        IPAddress lower = ipAddress.getLower().withoutPrefixLength();
        IPAddress upper = ipAddress.getUpper().withoutPrefixLength();
        UserIPTestResult testResult = new UserIPTestResult(
                lower.toFullString(),
                upper.toFullString(),
                ipAddress.toNormalizedString(),
                ipAddress.getCount().toString());
        context.json(testResult);
        saveConfig();
    }

    private IPAddress parseIPAddressFromReq(Context ctx) throws IllegalArgumentException {
        UserIPRuleAddRequest req = ctx.bodyAsClass(UserIPRuleAddRequest.class);
        if ((req.ipRange() == null) && (req.cidr() == null)) {
            throw new IllegalArgumentException(tl(locale(ctx), Lang.IP_BLACKLIST_PUT_IP_INVALID_ARG));
        }
        if (req.ipRange() == null) {
            IPAddress ipAddress = IPAddressUtil.getIPAddress(req.cidr());
            if (ipAddress == null) {
                throw new IllegalArgumentException(tl(locale(ctx), Lang.IP_BLACKLIST_PUT_IP_INVALID_IP));
            }
            return ipAddress;
        } else {
            IPAddress from = IPAddressUtil.getIPAddress(req.ipRange().from());
            IPAddress to = IPAddressUtil.getIPAddress(req.ipRange().to());
            if (from == null || to == null) {
                throw new IllegalArgumentException(tl(locale(ctx), Lang.IP_BLACKLIST_PUT_IP_INVALID_IP));
            }
            return from.coverWithPrefixBlock(to);
        }
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
        map.put("cities", cities);
        map.put("netType", netTypes);
        ctx.status(HttpStatus.OK);
        ctx.json(map);
    }

    @Override
    public void onDisable() {

    }

    @Override
    public void saveConfig() throws IOException {
        getConfig().set("ips", ips.stream().map(Address::toString).toList());
        getConfig().set("ports", List.copyOf(ports));
        getConfig().set("asns", List.copyOf(asns));
        getConfig().set("region", List.copyOf(regions));
        getConfig().set("cities", List.copyOf(cities));
        super.saveConfig();
    }

    private void reloadConfig() {
        this.banDuration = getConfig().getLong("ban-duration", 0);
        this.ips = new HashSet<>();
        for (String s : getConfig().getStringList("ips")) {
            IPAddress ipAddress = IPAddressUtil.getIPAddress(s);
            this.ips.add(ipAddress);
        }
        this.ports = new HashSet<>(getConfig().getIntList("ports"));
        this.regions = new HashSet<>(getConfig().getStringList("regions"));
        this.asns = new HashSet<>(getConfig().getLongList("asns"));
        this.cities = new HashSet<>(getConfig().getStringList("cities"));
        this.netTypes = new HashSet<>();
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
        if (!cities.isEmpty() && geoData.getCity() != null && geoData.getCity().getName() != null) {
            String fullCityName = geoData.getCity().getName();
            for (String s : cities) {
                if (fullCityName.contains(s)) {
                    return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.IP_BLACKLIST_CITY_RULE, s), new TranslationComponent(Lang.MODULE_IBL_MATCH_IP, s));
                }
            }
        }
        return pass();
    }

    public record UserIPTestResult(
            String from,
            String to,
            String generatedCidr,
            String count
    ) {

    }

    public record UserPortAddRequest(
            int from,
            int to
    ) {

    }

    public record UserIPRuleAddRequest(
            String cidr,
            UserIPRuleAddRequestIpRange ipRange
    ) {

    }

    public record UserIPRuleAddRequestIpRange(
            String from,
            String to
    ) {

    }

}
