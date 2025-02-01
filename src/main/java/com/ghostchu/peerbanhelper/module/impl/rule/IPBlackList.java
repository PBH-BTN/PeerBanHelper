package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import inet.ipaddr.Address;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
@IgnoreScan
public final class IPBlackList extends AbstractRuleFeatureModule implements Reloadable {
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
                .get("/api/modules/ipblacklist/{ruleType}", this::handleWebAPI, Role.USER_READ)
                .post("/api/modules/ipblacklist/ip/test", this::handleIPTest, Role.USER_WRITE)
                .put("/api/modules/ipblacklist/ip", this::handleIPPut, Role.USER_WRITE)
                .delete("/api/modules/ipblacklist/ip", this::handleIPDelete, Role.USER_WRITE)
                .put("/api/modules/ipblacklist/port", this::handlePort, Role.USER_WRITE)
                .delete("/api/modules/ipblacklist/port", this::handlePortDelete, Role.USER_WRITE)
                .put("/api/modules/ipblacklist/asn", this::handleASN, Role.USER_WRITE)
                .delete("/api/modules/ipblacklist/asn", this::handleASNDelete, Role.USER_WRITE)
                .put("/api/modules/ipblacklist/region", this::handleRegion, Role.USER_WRITE)
                .delete("/api/modules/ipblacklist/region", this::handleRegionDelete, Role.USER_WRITE)
                .put("/api/modules/ipblacklist/city", this::handleCities, Role.USER_WRITE)
                .delete("/api/modules/ipblacklist/city", this::handleCitiesDelete, Role.USER_WRITE)
        ;//.patch("/api/modules/ipblacklist/nettype", this::handleNetType, Role.USER_WRITE);
        Main.getReloadManager().register(this);
    }

    private void handleCitiesDelete(Context context) throws IOException {
        if (cities.removeIf(city -> city.equals(context.bodyAsClass(UserCityRequest.class).city()))) {
            //context.status(HttpStatus.OK);
            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        } else {
            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        }
        saveConfig();
        getCache().invalidateAll();
    }

    private void handleCities(Context context) throws IOException {
        var city = context.bodyAsClass(UserCityRequest.class).city();
        if (city == null || city.isBlank())
            throw new IllegalArgumentException("Argument city cannot be null or blank");
        cities.add(city);
        context.status(HttpStatus.CREATED);
        context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        saveConfig();
        getCache().invalidateAll();
    }
//
//    private void handleNetTypeDelete(Context context) throws IOException {
//        if (netTypes.removeIf(netType -> netType.equals(context.))) {
//            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY),null));
//        } else {
//            context.status(HttpStatus.NOT_FOUND);
//            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY),null));
//        }
//        saveConfig();
//    }

    private void handleRegionDelete(Context context) throws IOException {
        if (regions.removeIf(region -> region.equals(context.bodyAsClass(UserRegionRequest.class).region()))) {
            //context.status(HttpStatus.OK);
            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        } else {
            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        }
        saveConfig();
        getCache().invalidateAll();
    }

    private void handleASNDelete(Context context) throws IOException {
        if (asns.removeIf(p -> p == context.bodyAsClass(UserASNRequest.class).asn())) {
            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        } else {
            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        }
        saveConfig();
        getCache().invalidateAll();
    }

    private void handlePortDelete(Context context) throws IOException {
        if (ports.removeIf(p -> p == context.bodyAsClass(UserPortRequest.class).port())) {
            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        } else {
            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        }
        saveConfig();
        getCache().invalidateAll();
    }

    private void handleIPDelete(Context context) throws IOException {
        var parsed = IPAddressUtil.getIPAddress(context.bodyAsClass(UserIPRequest.class).ip());
        if (parsed == null)
            throw new IllegalArgumentException("Argument ip parse failed");
        if (ips.removeIf(ipAddress -> ipAddress.equals(parsed))) {
            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        } else {
            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        }
        saveConfig();
        getCache().invalidateAll();
    }


    private void handleRegion(Context ctx) throws IOException {
        var region = ctx.bodyAsClass(UserRegionRequest.class).region();
        if (region == null || region.isBlank())
            throw new IllegalArgumentException("Argument region cannot be null or blank");
        regions.add(region);
        ctx.status(HttpStatus.CREATED);
        ctx.json(new StdResp(true, tl(locale(ctx), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        saveConfig();
        getCache().invalidateAll();
    }

    private void handleASN(Context ctx) throws IOException {
        UserASNRequest asn = ctx.bodyAsClass(UserASNRequest.class);
        asns.add(asn.asn());
        ctx.status(HttpStatus.CREATED);
        ctx.json(new StdResp(true, tl(locale(ctx), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        saveConfig();
        getCache().invalidateAll();
    }


    private void handlePort(Context ctx) throws IOException {
        UserPortRequest req = ctx.bodyAsClass(UserPortRequest.class);
        ports.add(req.port());
        ctx.status(HttpStatus.CREATED);
        ctx.json(new StdResp(true, tl(locale(ctx), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        saveConfig();
        getCache().invalidateAll();
    }

    private void handleIPPut(Context context) throws IOException {
        IPAddress ipAddress = parseIPAddressFromReq(context);
        ips.add(ipAddress);
        context.status(HttpStatus.CREATED);
        context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        saveConfig();
        getCache().invalidateAll();
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
        context.json(new StdResp(true, null, testResult));
        saveConfig();
        getCache().invalidateAll();
    }

    private IPAddress parseIPAddressFromReq(Context ctx) throws IllegalArgumentException {
        UserIPRequest req = ctx.bodyAsClass(UserIPRequest.class);
        IPAddress ipAddress = IPAddressUtil.getIPAddress(req.ip());
        if (ipAddress == null) {
            throw new IllegalArgumentException(tl(locale(ctx), Lang.IP_BLACKLIST_PUT_IP_INVALID_IP));
        }
        return ipAddress;
    }


    @Override
    public boolean isThreadSafe() {
        return true;
    }

    private void handleWebAPI(Context ctx) {
        Map<String, Object> map = new LinkedHashMap<>();
        switch (ctx.pathParam("ruleType")) {
            case "ip" -> map.put("ip", ips.stream().map(Address::toString).toList());
            case "port" -> map.put("port", ports);
            case "asn" -> map.put("asn", asns);
            case "region" -> map.put("region", regions);
            case "city" -> map.put("city", cities);
            case "netType" -> map.put("netType", netTypes);
            default -> {
                ctx.status(HttpStatus.NOT_FOUND);
                return;
            }
        }
        ctx.status(HttpStatus.OK);
        ctx.json(new StdResp(true, null, map));
    }

    @Override
    public void onDisable() {
        Main.getReloadManager().unregister(this);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    @Override
    public void saveConfig() throws IOException {
        getConfig().set("ips", ips.stream().map(Address::toString).toList());
        getConfig().set("ports", List.copyOf(ports));
        getConfig().set("asns", List.copyOf(asns));
        getConfig().set("regions", List.copyOf(regions));
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
        getCache().invalidateAll();
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        if (isHandShaking(peer)) {
            return handshaking();
        }
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

    public record UserPortRequest(
            int port
    ) {

    }

    public record UserIPRequest(
            String ip
    ) {

    }

    public record UserIPRuleAddRequestIpRange(
            String from,
            String to
    ) {

    }

    public record UserASNRequest(
            long asn
    ) {

    }

    public record UserRegionRequest(
            String region
    ) {

    }

    public record UserCityRequest(
            String city
    ) {

    }


}
