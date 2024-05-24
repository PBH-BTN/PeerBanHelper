package com.ghostchu.peerbanhelper.module.impl.rule;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.database.DatabaseHelper;
import com.ghostchu.peerbanhelper.database.RuleSubInfo;
import com.ghostchu.peerbanhelper.database.RuleSubLog;
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
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IP黑名单远程订阅模块
 */
@Getter
@Slf4j
public class IPBlackRuleList extends AbstractRuleFeatureModule {

    final private DatabaseHelper db;
    private List<IPBanMatcher> ipBanMatchers;
    private long checkInterval = 86400000; // 默认24小时检查一次
    private ScheduledExecutorService scheduledExecutorService;

    public IPBlackRuleList(PeerBanHelperServer server, YamlConfiguration profile, DatabaseHelper db) {
        super(server, profile);
        this.db = db;
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
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(this::reloadConfig, 0, checkInterval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        StopWatch watch = StopWatch.create("timer");
        watch.start("t1");
        String ip = peer.getAddress().getIp();
        List<IPBanResult> results = new ArrayList<>();
        try (var service = Executors.newVirtualThreadPerTaskExecutor()) {
            ipBanMatchers.forEach(rule -> service.submit(() -> {
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
                log.error(Lang.IP_BAN_RULE_MATCH_ERROR, e);
                return false;
            }
        });
        watch.stop();
        log.debug(Lang.IP_BAN_RULE_MATCH_TIME, watch.getLastTaskTimeNanos());
        if (mr) {
            return new BanResult(this, PeerAction.BAN, ip, String.format(Lang.MODULE_IBL_MATCH_IP_RULE, matchRule.get().ruleName()));
        }
        return new BanResult(this, PeerAction.NO_ACTION, "N/A", "No matches");
    }

    /**
     * Reload the configuration for this module.
     */
    private void reloadConfig() {
        if (null == ipBanMatchers) {
            ipBanMatchers = new ArrayList<>();
        }
        ConfigurationSection config = getConfig();
        // 读取检查间隔
        checkInterval = config.getLong("check-interval", checkInterval);
        // 读取规则
        ConfigurationSection rules = getRuleSubsConfig();
        if (null != rules) {
            for (String ruleId : rules.getKeys(false)) {
                ConfigurationSection rule = rules.getConfigurationSection(ruleId);
                assert rule != null;
                updateRule(rule, Lang.IP_BAN_RULE_UPDATE_TYPE_AUTO);
            }
            log.info(Lang.IP_BAN_RULE_UPDATE_FINISH);
        }
    }

    /**
     * 更新规则
     *
     * @param rule 规则
     */
    public String updateRule(@NotNull ConfigurationSection rule, String updateType) {
        AtomicReference<String> result = new AtomicReference<>();
        String ruleId = rule.getName();
        if (!rule.getBoolean("enabled", false)) {
            // 检查ipBanMatchers是否有对应的规则，有则删除
            ipBanMatchers.removeIf(ele -> ele.getRuleId().equals(ruleId));
            // 未启用跳过更新逻辑
            return Lang.IP_BAN_RULE_DISABLED.replace("{}", ruleId);
        }
        String name = rule.getString("name", ruleId);
        String url = rule.getString("url");
        if (null != url && url.startsWith("http")) {
            // 解析远程订阅
            String ruleFileName = ruleId + ".txt";
            File ruleDir = FileUtil.mkdir(new File(Main.getDataDirectory(), "/sub"));
            File tempFile = new File(ruleDir, "temp_" + ruleFileName);
            File ruleFile = new File(ruleDir, ruleFileName);
            List<IPAddress> ipAddresses = new ArrayList<>();
            List<IPAddress> subnetAddresses = new ArrayList<>();
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
                    int ent_count = 0;
                    if (!tempHash.equals(ruleHash)) {
                        // 规则文件不存在或者规则文件与临时文件sha256不一致则需要更新
                        ent_count = fileToIPList(name, tempFile, ipAddresses, subnetAddresses);
                        // 更新后重命名临时文件
                        FileUtil.rename(tempFile, ruleFileName, true);
                    } else {
                        // 如果一致，但ipBanMatchers没有对应的规则内容，则加载内容
                        if (ipBanMatchers.stream().noneMatch(ele -> ele.getRuleId().equals(ruleId))) {
                            ent_count = fileToIPList(name, tempFile, ipAddresses, subnetAddresses);
                        } else {
                            log.info(Lang.IP_BAN_RULE_NO_UPDATE, name);
                            result.set(Lang.IP_BAN_RULE_NO_UPDATE.replace("{}", name));
                        }
                    }
                    FileUtil.del(tempFile);
                    // ip列表或者subnet列表不为空代表需要更新matcher
                    if (!ipAddresses.isEmpty() || !subnetAddresses.isEmpty()) {
                        // 如果已经存在则更新，否则添加
                        ipBanMatchers.stream().filter(ele -> ele.getRuleId().equals(ruleId)).findFirst().ifPresentOrElse(ele -> {
                            ele.setData(name, ipAddresses, subnetAddresses);
                            log.info(Lang.IP_BAN_RULE_UPDATE_SUCCESS, name);
                            result.set(Lang.IP_BAN_RULE_UPDATE_SUCCESS.replace("{}", name));
                        }, () -> {
                            ipBanMatchers.add(new IPBanMatcher(ruleId, name, ipAddresses, subnetAddresses));
                            log.info(Lang.IP_BAN_RULE_LOAD_SUCCESS, name);
                            result.set(Lang.IP_BAN_RULE_LOAD_SUCCESS.replace("{}", name));
                        });
                    }
                    if (ent_count > 0) {
                        // 更新日志
                        try {
                            db.insertRuleSubLog(ruleId, ent_count, updateType);
                        } catch (SQLException e) {
                            log.error(Lang.IP_BAN_RULE_UPDATE_LOG_ERROR, ruleId, e);
                            result.set(Lang.IP_BAN_RULE_UPDATE_LOG_ERROR.replace("{}", name));
                        }
                    }
                }).join();
            } catch (Exception e) {
                // 加载远程订阅文件出错,尝试从本地缓存中加载
                if (ruleFile.exists()) {
                    // 如果一致，但ipBanMatchers没有对应的规则内容，则加载内容
                    if (ipBanMatchers.stream().noneMatch(ele -> ele.getRuleId().equals(ruleId))) {
                        fileToIPList(name, ruleFile, ipAddresses, subnetAddresses);
                        ipBanMatchers.add(new IPBanMatcher(ruleId, name, ipAddresses, subnetAddresses));
                        log.warn(Lang.IP_BAN_RULE_USE_CACHE, name);
                        result.set(Lang.IP_BAN_RULE_USE_CACHE.replace("{}", name));
                    }
                } else {
                    log.error(Lang.IP_BAN_RULE_LOAD_FAILED, ruleId, e);
                    result.set(Lang.IP_BAN_RULE_LOAD_FAILED.replace("{}", name));
                }
            }
        }
        return result.get();
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
     * @return 加载的行数
     */
    private int fileToIPList(String ruleName, File ruleFile, List<IPAddress> ips, List<IPAddress> subnets) {
        AtomicInteger count = new AtomicInteger();
        FileUtil.readLines(ruleFile, StandardCharsets.UTF_8).forEach(ele -> {
            count.getAndIncrement();
            IPAddress ipAddress = IPAddressUtil.getIPAddress(ele);
            // 判断是否是网段
            List<IPAddress> ipsList = new ArrayList<>();
            if (null != ipAddress.getNetworkPrefixLength()) {
                if (ipAddress.isIPv4Convertible() && ipAddress.getNetworkPrefixLength() >= 20) {
                    // 前缀长度 >= 20 的ipv4网段地址转为精确ip
                    ipAddress.nonZeroHostIterator().forEachRemaining(ipsList::add);
                } else {
                    subnets.add(ipAddress);
                    log.debug(Lang.IP_BAN_RULE_LOAD_CIDR, ruleName, ipAddress);
                }
            } else {
                ipsList.add(ipAddress);
            }
            ipsList.forEach(ip -> {
                if (ip.isIPv4Convertible()) {
                    ip = ip.toIPv4().withoutPrefixLength();
                }
                ips.add(ip);
                log.debug(Lang.IP_BAN_RULE_LOAD_IP, ruleName, ip);
            });
        });
        return count.get();
    }

    /**
     * 获取规则订阅配置
     *
     * @return 规则订阅配置
     */
    public ConfigurationSection getRuleSubsConfig() {
        return getConfig().getConfigurationSection("rules");
    }

    /**
     * 获取规则订阅信息
     *
     * @param ruleId 规则ID
     * @return 规则订阅信息
     */
    public RuleSubInfo getRuleSubInfo(String ruleId) throws SQLException {
        ConfigurationSection rules = getRuleSubsConfig();
        if (rules == null) {
            return null;
        }
        ConfigurationSection rule = rules.getConfigurationSection(ruleId);
        if (rule == null) {
            return null;
        }
        Optional<RuleSubLog> first = db.queryRuleSubLogs(ruleId, 0, 1).stream().findFirst();
        long lastUpdate = first.map(RuleSubLog::updateTime).orElse(0L);
        int count = first.map(RuleSubLog::count).orElse(0);
        return new RuleSubInfo(ruleId, rule.getBoolean("enabled", false), rule.getString("name", ruleId), rule.getString("url"), lastUpdate, count);
    }

    /**
     * 保存规则订阅信息并返回保存后的信息
     *
     * @param ruleSubInfo 规则订阅信息
     * @return 保存后的规则订阅信息
     * @throws IOException 保存异常
     */
    public ConfigurationSection saveRuleSubInfo(@NotNull RuleSubInfo ruleSubInfo) throws IOException {
        ConfigurationSection rules = getRuleSubsConfig();
        String ruleId = ruleSubInfo.ruleId();
        rules.set(ruleId + ".enabled", ruleSubInfo.enabled());
        rules.set(ruleId + ".name", ruleSubInfo.ruleName());
        rules.set(ruleId + ".url", ruleSubInfo.subUrl());
        saveConfig();
        return rules.getConfigurationSection(ruleId);
    }

    /**
     * 删除规则订阅信息
     *
     * @param ruleId 规则ID
     * @throws IOException 删除异常
     */
    public void deleteRuleSubInfo(String ruleId) throws IOException {
        ConfigurationSection rules = getRuleSubsConfig();
        rules.set(ruleId, null);
        saveConfig();
    }

    /**
     * 查询规则订阅日志
     *
     * @param ruleId    规则ID
     * @param pageIndex 页码
     * @param pageSize  每页数量
     * @return 规则订阅日志
     * @throws SQLException 查询异常
     */
    public List<RuleSubLog> queryRuleSubLogs(String ruleId, int pageIndex, int pageSize) throws SQLException {
        return db.queryRuleSubLogs(ruleId, pageIndex, pageSize);
    }

    /**
     * 查询规则订阅日志数量
     *
     * @param ruleId 规则ID
     * @return 规则订阅日志数量
     * @throws SQLException 查询异常
     */
    public int countRuleSubLogs(String ruleId) throws SQLException {
        return db.countRuleSubLogs(ruleId);
    }

    /**
     * 更改检查间隔
     * 会立即触发一次更新
     *
     * @param checkInterval 检查间隔
     * @throws IOException 保存异常
     */
    public void changeCheckInterval(long checkInterval) throws IOException {
        this.checkInterval = checkInterval;
        getConfig().set("check-interval", checkInterval);
        saveConfig();
        if (null != scheduledExecutorService) {
            scheduledExecutorService.shutdown();
        }
        scheduledExecutorService = Executors.newScheduledThreadPool(1, r -> Thread.ofVirtual().name("IPBlackRuleList - Update Thread").unstarted(r));
        scheduledExecutorService.scheduleAtFixedRate(this::reloadConfig, 0, checkInterval, TimeUnit.MILLISECONDS);
    }

    record IPBanResult(String ruleName, MatchResult matchResult) {
    }
}


