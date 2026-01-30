package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
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
import java.net.InetAddress;
import java.util.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component

public final class IPBlackList extends AbstractRuleFeatureModule implements Reloadable {
    private Set<IPAddress> ips;
    private Set<Integer> ports;
    private Set<Long> asns;
    private Set<String> regions;
    private Set<String> networkType;
    @Autowired
    private JavalinWebContainer webContainer;
    private long banDuration;
    private Set<String> cities;
    @Autowired
    private IPDBManager iPDBManager;

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
                .put("/api/modules/ipblacklist/netType", this::handleNetTypePut, Role.USER_WRITE)
        ;//.patch("/api/modules/ipblacklist/nettype", this::handleNetType, Role.USER_WRITE);
        Main.getReloadManager().register(this);
    }

    private void handleNetTypePut(@NotNull Context context) {
        String[] inputNetTypes = context.bodyAsClass(String[].class);
        this.networkType = new HashSet<>(Arrays.asList(inputNetTypes));
        try {
            saveConfig();
            getCache().invalidateAll();
            context.status(HttpStatus.OK);
            context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        } catch (IOException e) {
            log.error("Unable to save config file", e);
            context.status(HttpStatus.INTERNAL_SERVER_ERROR);
            context.json(new StdResp(false, "Unable to save config file", null));
        }
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
            case "netType" -> map.put("netType", networkType);
            default -> {
                ctx.status(HttpStatus.NOT_FOUND);
                ctx.json(new StdResp(false, "Illegal pathParams: ruleType not acceptable.", null));
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
        getConfig().set("net-type", List.copyOf(networkType));
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
        this.networkType = new HashSet<>(getConfig().getStringList("net-type"));
        getCache().invalidateAll();
    }

    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
        if (isHandShaking(peer)) {
            return handshaking();
        }
        return getCache().readCacheButWritePassOnly(this, peer.getPeerAddress().getIp(), () -> {
            PeerAddress peerAddress = peer.getPeerAddress();
            if (ports.contains(peerAddress.getPort())) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.IP_BLACKLIST_PORT_RULE), new TranslationComponent(Lang.MODULE_IBL_MATCH_PORT, String.valueOf(peerAddress.getPort())),
                        StructuredData.create().add("type", "port").add("rule", peerAddress.getPort()));
            }
            IPAddress pa = IPAddressUtil.getIPAddress(peerAddress.getIp());
            for (IPAddress ra : ips) {
                if (ra.equals(pa) || ra.contains(pa)) {
                    return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.IP_BLACKLIST_CIDR_RULE, ra.toString()), new TranslationComponent(Lang.MODULE_IBL_MATCH_IP, ra.toString()),
                            StructuredData.create().add("type", "ip").add("rule", ra.toNormalizedString()));
                }
            }
            try {
                CheckResult ipdbResult = checkIPDB(peer.getPeerAddress().getAddress().toInetAddress());
                if (ipdbResult.action() != PeerAction.NO_ACTION) {
                    return ipdbResult;
                }
            } catch (Exception e) {
                log.error(tlUI(Lang.MODULE_IBL_EXCEPTION_GEOIP), e);
            }
            return pass();
        }, true);
    }

    private CheckResult checkIPDB(InetAddress addr) {
        if (regions.isEmpty() && asns.isEmpty()) {
            return pass();
        }
        var geoData = iPDBManager.queryIPDB(addr).geoData().get();
        if (geoData == null) {
            return pass();
        }
        if (!asns.isEmpty() && geoData.getAs() != null) {
            Long asn = geoData.getAs().getNumber();
            if (asns.contains(asn)) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.IP_BLACKLIST_ASN_RULE, String.valueOf(asn)), new TranslationComponent(Lang.MODULE_IBL_MATCH_ASN, String.valueOf(asn)),
                        StructuredData.create().add("type", "asn").add("rule", asn));
            }
        }
        if (!regions.isEmpty() && geoData.getCountry() != null) {
            String iso = geoData.getCountry().getIso();
            if (regions.contains(iso)) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.IP_BLACKLIST_REGION_RULE, iso), new TranslationComponent(Lang.MODULE_IBL_MATCH_REGION, iso),
                        StructuredData.create().add("type", "iso").add("rule", iso));
            }
        }
        if (networkType != null && geoData.getNetwork() != null && geoData.getNetwork().getNetType() != null) {
            String netType = geoData.getNetwork().getNetType();
            boolean hit = switch (netType) {
                case "宽带" -> networkType.contains("wideband");
                case "基站" -> networkType.contains("baseStation");
                case "政企专线" -> networkType.contains("governmentAndEnterpriseLine");
                case "业务平台" -> networkType.contains("businessPlatform");
                case "骨干网" -> networkType.contains("backboneNetwork");
                case "IP 专网", "IP专网" -> networkType.contains("ipPrivateNetwork");
                case "网吧" -> networkType.contains("internetCafe");
                case "物联网" -> networkType.contains("iot");
                case "数据中心" -> networkType.contains("dataCenter") || networkType.contains("datacenter"); // fe workaround
                default -> false;
            };
            if (hit) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.IP_BLACKLIST_NETTYPE_RULE, netType), new TranslationComponent(Lang.MODULE_IBL_MATCH_IP, netType),
                        StructuredData.create().add("type", "netTypes").add("rule", netType));
            }
        }
        if (!cities.isEmpty() && geoData.getCity() != null && geoData.getCity().getName() != null) {
            String fullCityName = geoData.getCity().getName();
            for (String s : cities) {
                if (fullCityName.contains(s)) {
                    return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(Lang.IP_BLACKLIST_CITY_RULE, s), new TranslationComponent(Lang.MODULE_IBL_MATCH_IP, s),
                            StructuredData.create().add("type", "city").add("rule", s));
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
