package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.database.dao.impl.RuleSubLogsDao;
import com.ghostchu.peerbanhelper.database.table.RuleSubInfoEntity;
import com.ghostchu.peerbanhelper.database.table.RuleSubLogEntity;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.IPBanRuleUpdateType;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.module.impl.webapi.common.SlimMsg;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.matcher.IPMatcher;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import inet.ipaddr.IPAddress;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

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

import static com.ghostchu.peerbanhelper.Main.DEF_LOCALE;
import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * IP黑名单远程订阅模块
 */
@Slf4j
@Component
@Getter
public class IPBlackRuleList extends AbstractRuleFeatureModule {
    private final RuleSubLogsDao ruleSubLogsDao;
    private List<IPMatcher> ipBanMatchers;
    private long checkInterval = 86400000; // 默认24小时检查一次
    private ScheduledExecutorService scheduledExecutorService;
    private long banDuration;

    public IPBlackRuleList(RuleSubLogsDao ruleSubLogsDao) {
        super();
        this.ruleSubLogsDao = ruleSubLogsDao;
    }

    @Override
    public boolean isConfigurable() {
        return true;
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
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        return getCache().readCachePassOnly(this, peer.getPeerAddress().getIp(), () -> {
            long t1 = System.currentTimeMillis();
            String ip = peer.getPeerAddress().getIp();
            List<IPBanResult> results = new ArrayList<>();
            try (var service = Executors.newVirtualThreadPerTaskExecutor()) {
                ipBanMatchers.forEach(rule -> service.submit(() -> {
                    results.add(new IPBanResult(rule.getRuleName(), rule.match(ip)));
                }));
            }
            AtomicReference<IPBanResult> matchRule = new AtomicReference<>();
            boolean mr = results.stream().anyMatch(ipBanResult -> {
                try {
                    if (ipBanResult == null) return false;
                    boolean match = ipBanResult.matchResult() == MatchResult.TRUE;
                    if (match) {
                        matchRule.set(ipBanResult);
                    }
                    return match;
                } catch (Exception e) {
                    log.error(tlUI(Lang.IP_BAN_RULE_MATCH_ERROR), e);
                    return false;
                }
            });
            long t2 = System.currentTimeMillis();
            log.debug(tlUI(Lang.IP_BAN_RULE_MATCH_TIME, t2 - t1));
            if (mr) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration, new TranslationComponent(ip), new TranslationComponent(Lang.MODULE_IBL_MATCH_IP_RULE, matchRule.get().ruleName(), ip));
            }
            return pass();
        }, true);
    }

    /**
     * Reload the configuration for this module.
     */
    private void reloadConfig() {
        this.banDuration = getConfig().getLong("ban-duration", 0);
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
                updateRule(DEF_LOCALE, rule, IPBanRuleUpdateType.AUTO);
            }
            log.info(tlUI(Lang.IP_BAN_RULE_UPDATE_FINISH));
        }
    }

    /**
     * 更新规则
     *
     * @param rule 规则
     */
    public SlimMsg updateRule(String locale, @NotNull ConfigurationSection rule, IPBanRuleUpdateType updateType) {
        AtomicReference<SlimMsg> result = new AtomicReference<>();
        String ruleId = rule.getName();
        if (!rule.getBoolean("enabled", false)) {
            // 检查ipBanMatchers是否有对应的规则，有则删除
            ipBanMatchers.removeIf(ele -> ele.getRuleId().equals(ruleId));
            // 未启用跳过更新逻辑
            return new SlimMsg(false, tl(locale, Lang.IP_BAN_RULE_DISABLED, ruleId), 400);
        }
        String name = rule.getString("name", ruleId);
        String url = rule.getString("url");
        if (null != url && url.startsWith("http")) {
            // 解析远程订阅
            String ruleFileName = ruleId + ".txt";
            File dir = new File(Main.getDataDirectory(), "/sub");
            dir.mkdirs();
            File tempFile = new File(dir, "temp_" + ruleFileName);
            File ruleFile = new File(dir, ruleFileName);
            List<IPAddress> ipAddresses = new ArrayList<>();
            HTTPUtil.retryableSend(HTTPUtil.getHttpClient(false, null), MutableRequest.GET(url), HttpResponse.BodyHandlers.ofFile(Path.of(tempFile.getPath()))).whenComplete((pathHttpResponse, throwable) -> {
                if (throwable != null) {
                    tempFile.delete();
                    // 加载远程订阅文件出错,尝试从本地缓存中加载
                    if (ruleFile.exists()) {
                        // 如果一致，但ipBanMatchers没有对应的规则内容，则加载内容
                        if (ipBanMatchers.stream().noneMatch(ele -> ele.getRuleId().equals(ruleId))) {
                            try {
                                fileToIPList(ruleFile, ipAddresses);
                                ipBanMatchers.add(new IPMatcher(ruleId, name, ipAddresses));
                                log.warn(tlUI(Lang.IP_BAN_RULE_USE_CACHE, name));
                                result.set(new SlimMsg(false, tl(locale, Lang.IP_BAN_RULE_USE_CACHE, name), 500));
                            } catch (IOException ex) {
                                log.error(tlUI(Lang.IP_BAN_RULE_LOAD_FAILED, name), ex);
                                result.set(new SlimMsg(false, tl(locale, Lang.IP_BAN_RULE_LOAD_FAILED, name), 500));
                            }
                        } else {
                            result.set(new SlimMsg(false, tl(locale, Lang.IP_BAN_RULE_UPDATE_FAILED, name), 500));
                        }
                    } else {
                        // log.error(Lang.IP_BAN_RULE_LOAD_FAILED, name, throwable);
                        result.set(new SlimMsg(false, tl(locale, Lang.IP_BAN_RULE_LOAD_FAILED, name), 500));
                    }
                    throw new RuntimeException(throwable);
                }
                try {
                    HashCode ruleHash = null;
                    HashCode tempHash = Files.asByteSource(tempFile).hash(Hashing.sha256());
                    if (ruleFile.exists()) {
                        ruleHash = Files.asByteSource(ruleFile).hash(Hashing.sha256());
                    }
                    int ent_count = 0;
                    if (!tempHash.equals(ruleHash)) {
                        // 规则文件不存在或者规则文件与临时文件sha256不一致则需要更新
                        ent_count = fileToIPList(tempFile, ipAddresses);
                        // 更新后重命名临时文件
                        ruleFile.delete();
                        tempFile.renameTo(ruleFile);
                    } else {
                        // 如果一致，但ipBanMatchers没有对应的规则内容，则加载内容
                        if (ipBanMatchers.stream().noneMatch(ele -> ele.getRuleId().equals(ruleId))) {
                            ent_count = fileToIPList(tempFile, ipAddresses);
                        } else {
                            log.info(tlUI(Lang.IP_BAN_RULE_NO_UPDATE, name));
                            result.set(new SlimMsg(true, tl(locale, Lang.IP_BAN_RULE_NO_UPDATE, name), 200));
                        }
                        tempFile.delete();
                    }
                    if (ent_count > 0) {
                        // 如果已经存在则更新，否则添加
                        ipBanMatchers.stream().filter(ele -> ele.getRuleId().equals(ruleId)).findFirst().ifPresentOrElse(ele -> {
                            ele.setData(name, ipAddresses);
                            log.info(tlUI(Lang.IP_BAN_RULE_UPDATE_SUCCESS, name));
                            result.set(new SlimMsg(true, tl(locale, Lang.IP_BAN_RULE_UPDATE_SUCCESS, name), 200));
                        }, () -> {
                            ipBanMatchers.add(new IPMatcher(ruleId, name, ipAddresses));
                            log.info(tlUI(Lang.IP_BAN_RULE_LOAD_SUCCESS, name));
                            result.set(new SlimMsg(true, tl(locale, Lang.IP_BAN_RULE_LOAD_SUCCESS, name), 200));
                        });
                        // 更新日志
                        try {
                            ruleSubLogsDao.create(new RuleSubLogEntity(null, ruleId, System.currentTimeMillis(), ent_count, updateType));
                            result.set(new SlimMsg(true, tl(locale, Lang.IP_BAN_RULE_UPDATED, name), 200));
                        } catch (SQLException e) {
                            log.error(tlUI(Lang.IP_BAN_RULE_UPDATE_LOG_ERROR, ruleId), e);
                            result.set(new SlimMsg(false, tl(locale, Lang.IP_BAN_RULE_UPDATE_LOG_ERROR, name), 500));
                        }
                    } else {
                        result.set(new SlimMsg(true, tl(locale, Lang.IP_BAN_RULE_NO_UPDATE, name), 200));
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).join();
        } else {
            result.set(new SlimMsg(false, tl(locale, Lang.IP_BAN_RULE_URL_WRONG, name), 400));
        }
        return result.get();
    }

    /**
     * 读取规则文件并转为IpList
     *
     * @param ruleFile 规则文件
     * @param ips      ip列表
     * @return 加载的行数
     */
    private int fileToIPList(File ruleFile, List<IPAddress> ips) throws IOException {
        AtomicInteger count = new AtomicInteger();
        Files.readLines(ruleFile, StandardCharsets.UTF_8).forEach(ele -> {
            count.getAndIncrement();
            ips.add(IPAddressUtil.getIPAddress(ele));
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
    public RuleSubInfoEntity getRuleSubInfo(String ruleId) throws SQLException {
        ConfigurationSection rules = getRuleSubsConfig();
        if (rules == null) {
            return null;
        }
        ConfigurationSection rule = rules.getConfigurationSection(ruleId);
        if (rule == null) {
            return null;
        }

        Optional<RuleSubLogEntity> first = ruleSubLogsDao.queryByPaging(ruleSubLogsDao.queryBuilder().where().eq("ruleId", ruleId).queryBuilder(), 0, 1).stream().findFirst();
        long lastUpdate = first.map(RuleSubLogEntity::getUpdateTime).orElse(0L);
        int count = first.map(RuleSubLogEntity::getCount).orElse(0);
        return new RuleSubInfoEntity(ruleId, rule.getBoolean("enabled", false), rule.getString("name", ruleId), rule.getString("url"), lastUpdate, count);
    }

    /**
     * 保存规则订阅信息并返回保存后的信息
     *
     * @param ruleSubInfo 规则订阅信息
     * @return 保存后的规则订阅信息
     * @throws IOException 保存异常
     */
    public ConfigurationSection saveRuleSubInfo(@NotNull RuleSubInfoEntity ruleSubInfo) throws IOException {
        ConfigurationSection rules = getRuleSubsConfig();
        String ruleId = ruleSubInfo.getRuleId();
        rules.set(ruleId + ".enabled", ruleSubInfo.isEnabled());
        rules.set(ruleId + ".name", ruleSubInfo.getRuleName());
        rules.set(ruleId + ".url", ruleSubInfo.getSubUrl());
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
    public List<RuleSubLogEntity> queryRuleSubLogs(String ruleId, int pageIndex, int pageSize) throws SQLException {
        var builder = ruleSubLogsDao.queryBuilder();
        if (ruleId != null) {
            builder = builder.where().eq("ruleId", ruleId).queryBuilder();
        }
        return ruleSubLogsDao.queryByPaging(builder, pageIndex, pageSize);
    }

    /**
     * 查询规则订阅日志数量
     *
     * @param ruleId 规则ID
     * @return 规则订阅日志数量
     * @throws SQLException 查询异常
     */
    public long countRuleSubLogs(String ruleId) throws SQLException {
        var builder = ruleSubLogsDao.queryBuilder();
        if (ruleId != null) {
            builder = builder.where().eq("ruleId", ruleId).queryBuilder();
        }
        return ruleSubLogsDao.countOf(builder.setCountOf(true).prepare());
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


