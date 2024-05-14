package com.ghostchu.peerbanhelper.module.impl.rule;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.matcher.IPBanMatcher;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * IP黑名单远程订阅模块
 */
@Getter
@Slf4j
public class IPBlackRuleList extends AbstractRuleFeatureModule {
    private List<IPBanMatcher> bannedIps;
    private HttpClient httpClient;
    private long checkInterval = 86400000; // 默认24小时检查一次
    private long lastCheckTime = 0;

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
        if (System.currentTimeMillis() - lastCheckTime > checkInterval) {
            // 若超过检查间隔则重新加载配置
            reloadConfig();
        }
        StopWatch watch = StopWatch.create("timer");
        watch.start("t1");
        String ip = peer.getAddress().getIp();
        List<CompletableFuture<MatchResult>> fetchPeerFutures = new ArrayList<>(bannedIps.size());
        bannedIps.forEach(rule -> fetchPeerFutures.add(CompletableFuture.supplyAsync(() -> rule.match(ip))));
        CompletableFuture.allOf(fetchPeerFutures.toArray(new CompletableFuture[0])).join();
        BanResult result = null;
        try {
            for (int i = 0; i < fetchPeerFutures.size(); i++) {
                if (fetchPeerFutures.get(i).get() == MatchResult.TRUE) {
                    result = new BanResult(this, PeerAction.BAN, ip, String.format(Lang.MODULE_IBL_MATCH_IP_RULE, bannedIps.get(i).getRuleName()));
                    break;
                }
            }
        } catch (Exception e) {
            log.error("IP黑名单订阅规则匹配异常", e);
        }
        watch.stop();
        log.debug("匹配IP黑名单订阅规则花费时间：{}", watch.getLastTaskTimeNanos());
        if (result != null) {
            return result;
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
        if (null == bannedIps) {
            bannedIps = new ArrayList<>();
        }
        ConfigurationSection config = getConfig();
        // 读取检查间隔
        checkInterval = config.getLong("check-interval", checkInterval);
        // 读取规则
        ConfigurationSection rules = config.getConfigurationSection("rules");
        if (null != rules) {
            for (String ruleId : rules.getKeys(false)) {
                ConfigurationSection rule = rules.getConfigurationSection(ruleId);
                assert rule != null;
                String name = rule.getString("name");
                String url = rule.getString("url");
                List<IPAddress> ipAddresses = new ArrayList<>();
                if (null != url && url.startsWith("http")) {
                    // 解析远程订阅
                    Digester digester = new Digester(DigestAlgorithm.SHA256);
                    String ruleFileName = ruleId + ".txt";
                    File tempFile = new File(Main.getDataDirectory(), "temp_" + ruleId + ".txt");
                    File ruleFile = new File(Main.getDataDirectory(), ruleFileName);
                    try {
                        HTTPUtil.retryableSend(httpClient, MutableRequest.GET(url), HttpResponse.BodyHandlers.ofFile(Path.of(tempFile.getPath()))).whenComplete((pathHttpResponse, throwable) -> {
                            if(!ruleFile.exists() || !digester.digestHex(tempFile).equals(digester.digestHex(ruleFile))){
                                // 规则文件不存在或者规则文件与临时文件sha256不一致则需要更新
                                fileToIPList(name, tempFile, ipAddresses);
                                // 更新后重命名临时文件
                                FileUtil.rename(tempFile, ruleFileName, true);
                            }else{
                                // 如果一致，但bannedIps没有对应的规则内容，则加载内容
                                if (bannedIps.stream().noneMatch(ele -> ele.getRuleId().equals(ruleId))) {
                                    fileToIPList(name, tempFile, ipAddresses);
                                }else{
                                    log.info("IP黑名单订阅规则 {} 未发生更新", name);
                                }
                            }
                            FileUtil.del(tempFile);
                        }).join();
                        // ip列表不为空代表需要更新matcher
                        if(!ipAddresses.isEmpty()){
                            // 如果已经存在则更新，否则添加
                            bannedIps.stream().filter(ele -> ele.getRuleId().equals(ruleId)).findFirst().ifPresentOrElse(ele -> {
                                ele.setData(name, ipAddresses);
                                log.info("IP黑名单订阅规则 {} 更新成功", name);
                            }, () -> {
                                bannedIps.add(new IPBanMatcher(ruleId, name, ipAddresses));
                                log.info("IP黑名单订阅规则 {} 加载成功", name);
                            });
                        }
                    } catch (Exception e) {
                        // 加载远程订阅文件出错,尝试从本地缓存中加载
                        if (ruleFile.exists()) {
                            // 如果一致，但bannedIps没有对应的规则内容，则加载内容
                            if (bannedIps.stream().noneMatch(ele -> ele.getRuleId().equals(ruleId))) {
                                fileToIPList(name, ruleFile, ipAddresses);
                                bannedIps.add(new IPBanMatcher(ruleId, name, ipAddresses));
                                log.warn("IP黑名单订阅规则 {} 订阅失败，使用本地缓存加载成功", name);
                            }
                        } else {
                            log.error("IP黑名单订阅规则 {} 加载失败", ruleId, e);
                        }
                    }
                }
            }
            log.info("IP黑名单规则订阅完毕");
        }
        lastCheckTime = System.currentTimeMillis();
    }

    /**
     * 读取规则文件并转为IpList
     *
     * @param ruleName    规则名称
     * @param ruleFile    规则文件
     * @param ipAddresses ipList
     */
    private void fileToIPList(String ruleName, File ruleFile, List<IPAddress> ipAddresses) {
        FileUtil.readLines(ruleFile, StandardCharsets.UTF_8).forEach(ele -> {
            IPAddress ipAddress = new IPAddressString(ele).getAddress();
            if (ipAddress != null) {
                if (ipAddress.isIPv4Convertible()) {
                    ipAddress = ipAddress.toIPv4();
                }
                ipAddresses.add(ipAddress);
                log.debug("IP黑名单订阅规则 {} 加载IP : {}", ruleName, ele);
            }
        });
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
