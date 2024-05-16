package com.ghostchu.peerbanhelper.module.impl.rule;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
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
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.matcher.IPBanMatcher;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.common.hash.Hashing;
import inet.ipaddr.IPAddress;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IP黑名单远程订阅模块
 */
@Getter
@Slf4j
public class IPBlackRuleList extends AbstractRuleFeatureModule {
    private List<IPBanMatcher> bannedIps;
    private long checkInterval = 86400000; // 默认24小时检查一次

    public IPBlackRuleList(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
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
        StopWatch watch = StopWatch.create("timer");
        watch.start("t1");
        String ip = peer.getAddress().getIp();
        List<IPBanResult> results = new ArrayList<>();
        try (var service = Executors.newVirtualThreadPerTaskExecutor()) {
            bannedIps.forEach(rule -> service.submit(() -> {
                results.add(new IPBanResult(rule.getRuleName(), rule.match(ip)));
            }));
        }
        AtomicReference<IPBanResult> matchRule = new AtomicReference<>();
        boolean mr = results.stream().anyMatch(ipBanResult -> {
            try {
                boolean match = ipBanResult.matchResult() == MatchResult.TRUE;
                if (match) {
                    matchRule.set(ipBanResult);
                }
                return match;
            } catch (Exception e) {
                log.error("IP黑名单订阅规则匹配异常", e);
                return false;
            }
        });
        watch.stop();
        log.debug("匹配IP黑名单订阅规则花费时间：{}", watch.getLastTaskTimeNanos());
        if (mr) {
            return new BanResult(this, PeerAction.BAN, ip, String.format(Lang.MODULE_IBL_MATCH_IP_RULE, matchRule.get().ruleName()));
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
        ConfigurationSection config = getConfig();
        // 读取检查间隔
        checkInterval = config.getLong("check-interval", checkInterval);
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(this::reloadConfig, 0, checkInterval, java.util.concurrent.TimeUnit.MILLISECONDS);
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
                List<IPAddress> subnetAddresses = new ArrayList<>();
                if (null != url && url.startsWith("http")) {
                    // 解析远程订阅
                    String ruleFileName = ruleId + ".txt";
                    File ruleDir = FileUtil.mkdir(new File(Main.getDataDirectory(), "/sub"));
                    File tempFile = new File(ruleDir, "temp_" + ruleFileName);
                    File ruleFile = new File(ruleDir, ruleFileName);
                    try {
                        HTTPUtil.retryableSend(HTTPUtil.getHttpClient(false, null), MutableRequest.GET(url), HttpResponse.BodyHandlers.ofFile(Path.of(tempFile.getPath()))).whenComplete((pathHttpResponse, throwable) -> {
                            if (throwable != null) {
                                FileUtil.del(tempFile);
                                throw new RuntimeException(throwable);
                            }
                            String tempHash = Hashing.sha256().hashBytes(FileUtil.readBytes(tempFile)).toString();
                            String ruleHash = null;
                            if (ruleFile.exists()) {
                                ruleHash = Hashing.sha256().hashBytes(FileUtil.readBytes(ruleFile)).toString();
                            }
                            if (!tempHash.equals(ruleHash)) {
                                // 规则文件不存在或者规则文件与临时文件sha256不一致则需要更新
                                fileToIPList(name, tempFile, ipAddresses, subnetAddresses);
                                // 更新后重命名临时文件
                                FileUtil.rename(tempFile, ruleFileName, true);
                            } else {
                                // 如果一致，但bannedIps没有对应的规则内容，则加载内容
                                if (bannedIps.stream().noneMatch(ele -> ele.getRuleId().equals(ruleId))) {
                                    fileToIPList(name, tempFile, ipAddresses, subnetAddresses);
                                } else {
                                    log.info("IP黑名单订阅规则 {} 未发生更新", name);
                                }
                            }
                            FileUtil.del(tempFile);
                        }).join();
                        // ip列表或者subnet列表不为空代表需要更新matcher
                        if (!ipAddresses.isEmpty() || !subnetAddresses.isEmpty()) {
                            // 如果已经存在则更新，否则添加
                            bannedIps.stream().filter(ele -> ele.getRuleId().equals(ruleId)).findFirst().ifPresentOrElse(ele -> {
                                ele.setData(name, ipAddresses, subnetAddresses);
                                log.info("IP黑名单订阅规则 {} 更新成功", name);
                            }, () -> {
                                bannedIps.add(new IPBanMatcher(ruleId, name, ipAddresses, subnetAddresses));
                                log.info("IP黑名单订阅规则 {} 加载成功", name);
                            });
                        }
                    } catch (Exception e) {
                        // 加载远程订阅文件出错,尝试从本地缓存中加载
                        if (ruleFile.exists()) {
                            // 如果一致，但bannedIps没有对应的规则内容，则加载内容
                            if (bannedIps.stream().noneMatch(ele -> ele.getRuleId().equals(ruleId))) {
                                fileToIPList(name, ruleFile, ipAddresses, subnetAddresses);
                                bannedIps.add(new IPBanMatcher(ruleId, name, ipAddresses, subnetAddresses));
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
    }

    /**
     * 读取规则文件并转为IpList
     * 其中ipv4网段地址转为精确ip
     * 考虑到ipv6分配地址通常是/64，所以ipv6网段不转为精确ip
     *
     * @param ruleName 规则名称
     * @param ruleFile 规则文件
     * @param ips      精确ip列表
     * @param subnets  网段列表
     */
    private void fileToIPList(String ruleName, File ruleFile, List<IPAddress> ips, List<IPAddress> subnets) {
        FileUtil.readLines(ruleFile, StandardCharsets.UTF_8).forEach(ele -> {
            IPAddress ipAddress = IPAddressUtil.getIPAddress(ele);
            // 判断是否是网段
            List<IPAddress> ipsList = new ArrayList<>();
            if (null != ipAddress.getNetworkPrefixLength()) {
                if (ipAddress.isIPv4Convertible() && ipAddress.getNetworkPrefixLength() >= 20) {
                    // 前缀长度 >= 20 的ipv4网段地址转为精确ip
                    ipAddress.nonZeroHostIterator().forEachRemaining(ipsList::add);
                } else {
                    subnets.add(ipAddress);
                    log.debug("IP黑名单订阅规则 {} 加载CIDR : {}", ruleName, ipAddress);
                }
            } else {
                ipsList.add(ipAddress);
            }
            ipsList.forEach(ip -> {
                if (ip.isIPv4Convertible()) {
                    ip = ip.toIPv4().withoutPrefixLength();
                }
                ips.add(ip);
                log.debug("IP黑名单订阅规则 {} 加载精确IP : {}", ruleName, ip);
            });

        });
    }

    @Override
    public void onDisable() {
    }
}

record IPBanResult(String ruleName, MatchResult matchResult) {
}
