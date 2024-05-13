package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

/**
 * IP黑名单远程订阅模块
 */
@Getter
@Slf4j
public class IPBlackRuleList extends AbstractRuleFeatureModule {
    private List<IPBanRule> ipsList;
    private HttpClient httpClient;

    public IPBlackRuleList(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
        setupHttpClient();
    }

    @Override
    public boolean isConfigurable() {
        return true;
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
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        PeerAddress peerAddress = peer.getAddress();
        IPAddress pa = IPAddressUtil.getIPAddress(peerAddress.getIp());
        if (pa.isIPv4Convertible()) {
            pa = pa.toIPv4();
        }
        BanResult res = null;
        out:
        for (IPBanRule ele : ipsList) {
            List<IPAddress> ips = ele.ips();
            for (IPAddress ra : ips) {
                if (ra.isIPv4() != pa.isIPv4()) { // 在上面的规则处统一进行过转换，此处可直接进行检查
                    continue;
                }
                if (ra.equals(pa) || ra.contains(pa)) {
                    res = new BanResult(this, PeerAction.BAN, ra.toString(), String.format(Lang.MODULE_IBL_MATCH_IP_RULE, ele.name()));
                    break out;
                }
            }
        }
        if (res != null) {
            return res;
        }
        return new BanResult(this, PeerAction.NO_ACTION, "N/A", "No matches");
    }

    @Override
    public @NotNull String getName() {
        return "IP Blacklist Rule List";
    }

    @Override
    public @NotNull String getConfigName() {
        return "ip-address-blocker-rules";
    }

    @Override
    public void onEnable() {
        reloadConfig();
    }

    /**
     * Reload the configuration for this module.
     */
    private void reloadConfig() {
        ipsList = new ArrayList<>();
        ConfigurationSection config = getConfig();
        ConfigurationSection rules = config.getConfigurationSection("rules");
        if (null != rules) {
            for (String ruleId : rules.getKeys(false)) {
                try {
                    ConfigurationSection rule = rules.getConfigurationSection(ruleId);
                    assert rule != null;
                    String name = rule.getString("name");
                    String url = rule.getString("url");
                    List<IPAddress> ipAddresses = new ArrayList<>();
                    if (null != url && url.startsWith("http")) {
                        // 解析远程订阅
                        HttpResponse<Stream<String>> ipList = HTTPUtil.retryableSend(httpClient, MutableRequest.GET(url), HttpResponse.BodyHandlers.ofLines()).join();
                        ipList.body().forEach(ele -> {
                            IPAddress ipAddress = new IPAddressString(ele).getAddress();
                            if (ipAddress != null) {
                                if (ipAddress.isIPv4Convertible()) {
                                    ipAddress = ipAddress.toIPv4();
                                }
                                ipAddresses.add(ipAddress);
                                log.debug("IPBan rule {} load ip : {}", name, ele);
                            }
                        });
                    }
                    ipsList.add(new IPBanRule(ruleId, name, ipAddresses));
                    log.info("IPBan rule {} load success: ", name);
                } catch (Exception e) {
                    log.error("IPBan rule {} load failed: ", ruleId, e);
                }
            }
        }
    }

    @Override
    public void onDisable() {

    }

    private void setupHttpClient() {
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.httpClient = Methanol
                .newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .userAgent(Main.getUserAgent())
                .defaultHeader("Content-Type", "application/octet-stream;charset=utf-8")
                .requestTimeout(Duration.ofMinutes(1))
                .connectTimeout(Duration.ofSeconds(10))
                .cookieHandler(cm).build();
    }
}

/**
 * IP黑名单规则
 */
record IPBanRule(String id, String name, List<IPAddress> ips) {
}
