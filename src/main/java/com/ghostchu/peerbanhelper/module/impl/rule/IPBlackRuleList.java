package com.ghostchu.peerbanhelper.module.impl.rule;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.service.RuleSubLogService;
import com.ghostchu.peerbanhelper.databasent.table.RuleSubInfoEntity;
import com.ghostchu.peerbanhelper.databasent.table.RuleSubLogEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.IPBanRuleUpdateType;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.module.impl.rule.dto.DataUpdateResultDTO;
import com.ghostchu.peerbanhelper.module.impl.rule.dto.IPBanResultDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.TimeUtil;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskManager;
import com.ghostchu.peerbanhelper.util.backgroundtask.FunctionalBackgroundTask;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.util.rule.MatchResultEnum;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.util.rule.matcher.IPMatcher;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import io.sentry.Sentry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
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
public final class IPBlackRuleList extends AbstractRuleFeatureModule implements Reloadable {
    private final RuleSubLogService ruleSubLogsDao;
    private final ModuleMatchCache moduleMatchCache;
    private List<IPMatcher> ipBanMatchers;
    private long checkInterval = 86400000; // 默认24小时检查一次
    private long banDuration;
    private ScheduledFuture<?> reloadConfigTask; // 保留此字段用于 changeCheckInterval() 方法
    @Autowired
    private HTTPUtil httpUtil;
    private boolean preloadBanList;
    @Autowired
    private BackgroundTaskManager backgroundTaskManager;

    public IPBlackRuleList(RuleSubLogService ruleSubLogsDao, ModuleMatchCache moduleMatchCache) {
        super();
        this.ruleSubLogsDao = ruleSubLogsDao;
        this.moduleMatchCache = moduleMatchCache;
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
        reloadConfigTask = registerScheduledTask(this::reloadConfig, 0, checkInterval, TimeUnit.MILLISECONDS);
        Main.getReloadManager().register(this);
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
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
        if (isHandShaking(peer)) {
            return handshaking();
        }
        String ip = peer.getPeerAddress().getIp();
        List<IPBanResultDTO> results = new ArrayList<>();
        ipBanMatchers.forEach(rule -> results.add(new IPBanResultDTO(rule.getRuleName(), rule.match(ip))));
        for (IPBanResultDTO ipBanResultDTO : results) {
            try {
                if (ipBanResultDTO == null) return pass();
                boolean match = ipBanResultDTO.matchResult().result() == MatchResultEnum.TRUE;
                if (match) {
                    return new CheckResult(getClass(),
                            PeerAction.BAN,
                            banDuration,
                            new TranslationComponent(ipBanResultDTO.ruleName()),
                            new TranslationComponent(Lang.MODULE_IBL_MATCH_IP_RULE,
                                    ipBanResultDTO.ruleName(),
                                    ip,
                                    Optional.ofNullable(ipBanResultDTO.matchResult().comment()).orElse(new TranslationComponent(Lang.MODULE_IBL_COMMENT_UNKNOWN))
                            ),
                            StructuredData.create().add("ruleName", ipBanResultDTO.ruleName()));
                }
            } catch (Exception e) {
                log.error(tlUI(Lang.IP_BAN_RULE_MATCH_ERROR), e);
                return pass();
            }
        }
        return pass();
    }

    /**
     * Reload the configuration for this module.
     */
    private void reloadConfig() {
        getCache().invalidateAll();
        try {
            this.banDuration = getConfig().getLong("ban-duration", 0);
            this.preloadBanList = getConfig().getBoolean("preload-banlist", false);
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
                    try {
                        updateRule(DEF_LOCALE, rule, IPBanRuleUpdateType.AUTO);
                        log.info(tlUI(Lang.IP_BAN_RULE_UPDATE_FINISH));
                    } catch (Exception e) {
                        log.warn(tlUI(Lang.IP_BAN_RULE_UPDATE_FAILED, rule.getString("name")), e);
                    }
                }
            }
            getCache().invalidateAll();
        } catch (Throwable throwable) {
            log.error("Unable to complete scheduled tasks", throwable);
            Sentry.captureException(throwable);
        }
    }

    /**
     * 更新规则
     *
     * @param rule 规则
     */
    public StdResp updateRule(String locale, @NotNull ConfigurationSection rule, IPBanRuleUpdateType updateType) {
        AtomicReference<StdResp> result = new AtomicReference<>();
        String ruleId = rule.getName();
        String name = rule.getString("name", ruleId);
        if (name.contains(".")) {
            throw new IllegalArgumentException("Illegal character (.) in name: " + name);
        }
        if (!rule.getBoolean("enabled", false)) {
            // 检查ipBanMatchers是否有对应的规则，有则删除
            ipBanMatchers.removeIf(ele -> ele.getRuleId().equals(ruleId));
            // 未启用跳过更新逻辑
            return new StdResp(false, tl(locale, Lang.IP_BAN_RULE_DISABLED, ruleId), null);
        }
        backgroundTaskManager.addTaskAsync(new FunctionalBackgroundTask(new TranslationComponent(Lang.MODULE_IP_BLACK_RULE_LIST_UPDATING_RULE_TITLE), (task, callback) -> {
            String url = rule.getString("url");
            if (null != url) {
                // 解析远程订阅
                String ruleFileName = ruleId + ".txt";
                File dir = new File(Main.getDataDirectory(), "/sub");
                dir.mkdirs();
                File ruleFile = new File(dir, ruleFileName);
                DualIPv4v6AssociativeTries<String> ipAddresses = new DualIPv4v6AssociativeTries<>();
                getResource(url)
                        .whenComplete((dataUpdateResultDTO, throwable) -> {
                            if (throwable != null) {
                                // 加载远程订阅文件出错,尝试从本地缓存中加载
                                if (ruleFile.exists()) {
                                    // 如果一致，但ipBanMatchers没有对应的规则内容，则加载内容
                                    if (ipBanMatchers.stream().noneMatch(ele -> ele.getRuleId().equals(ruleId))) {
                                        try {
                                            fileToIPList(ruleFile, ipAddresses);
                                            ipBanMatchers.add(new IPMatcher(ruleId, name, List.of(ipAddresses)));
                                            log.warn(tlUI(Lang.IP_BAN_RULE_USE_CACHE, name));
                                            result.set(new StdResp(false, tl(locale, Lang.IP_BAN_RULE_USE_CACHE, name), null));
                                        } catch (IOException ex) {
                                            log.error(tlUI(Lang.IP_BAN_RULE_LOAD_FAILED, name), ex);
                                            result.set(new StdResp(false, tl(locale, Lang.IP_BAN_RULE_LOAD_FAILED, name), null));
                                        }
                                    } else {
                                        result.set(new StdResp(false, tl(locale, Lang.IP_BAN_RULE_UPDATE_FAILED, name), null));
                                    }
                                } else {
                                    // log.error(Lang.IP_BAN_RULE_LOAD_FAILED, name, throwable);
                                    result.set(new StdResp(false, tl(locale, Lang.IP_BAN_RULE_LOAD_FAILED, name), null));
                                }
                                throw new RuntimeException(throwable);
                            }
                            try {
                                HashCode ruleHash = null;
                                HashCode tempHash = Hashing.sha256().hashBytes(dataUpdateResultDTO.data());
                                if (ruleFile.exists()) {
                                    ruleHash = Files.asByteSource(ruleFile).hash(Hashing.sha256());
                                }
                                int ent_count = 0;
                                if (!tempHash.equals(ruleHash)) {
                                    // 规则文件不存在或者规则文件与临时文件sha256不一致则需要更新
                                    ent_count = stringToIPList(new String(dataUpdateResultDTO.data(), StandardCharsets.UTF_8), ipAddresses);
                                    // 更新后重命名临时文件
                                    Files.write(dataUpdateResultDTO.data(), ruleFile);
                                } else {
                                    // 如果一致，但ipBanMatchers没有对应的规则内容，则加载内容
                                    if (ipBanMatchers.stream().noneMatch(ele -> ele.getRuleId().equals(ruleId))) {
                                        ent_count = stringToIPList(new String(dataUpdateResultDTO.data(), StandardCharsets.UTF_8), ipAddresses);
                                    } else {
                                        log.info(tlUI(Lang.IP_BAN_RULE_NO_UPDATE, name));
                                        result.set(new StdResp(true, tl(locale, Lang.IP_BAN_RULE_NO_UPDATE, name), null));
                                    }
                                }
                                if (ent_count > 0) {
                                    // 如果已经存在则更新，否则添加
                                    ipBanMatchers.stream().filter(ele -> ele.getRuleId().equals(ruleId)).findFirst().ifPresentOrElse(ele -> {
                                        ele.setData(name, List.of(ipAddresses));
                                        moduleMatchCache.invalidateAll();
                                        log.info(tlUI(Lang.IP_BAN_RULE_UPDATE_SUCCESS, name));
                                        result.set(new StdResp(true, tl(locale, Lang.IP_BAN_RULE_UPDATE_SUCCESS, name), null));
                                    }, () -> {
                                        ipBanMatchers.add(new IPMatcher(ruleId, name, List.of(ipAddresses)));
                                        log.info(tlUI(Lang.IP_BAN_RULE_LOAD_SUCCESS, name));
                                        result.set(new StdResp(true, tl(locale, Lang.IP_BAN_RULE_LOAD_SUCCESS, name), null));
                                    });
                                    // 更新日志
                                    ruleSubLogsDao.save(new RuleSubLogEntity(null, ruleId, OffsetDateTime.now(), ent_count, updateType));
                                    result.set(new StdResp(true, tl(locale, Lang.IP_BAN_RULE_UPDATED, name), null));
                                } else {
                                    result.set(new StdResp(true, tl(locale, Lang.IP_BAN_RULE_NO_UPDATE, name), null));
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } finally {
                                moduleMatchCache.invalidateAll();
                            }
                        }).join();
            } else {
                result.set(new StdResp(false, tl(locale, Lang.IP_BAN_RULE_URL_WRONG, name), null));
            }
        })).join();
        return result.get();
    }

    private CompletableFuture<DataUpdateResultDTO> getResource(String url) {
        return CompletableFuture.supplyAsync(() -> {
            URI uri;
            try {
                uri = new URI(url);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            if (uri.getScheme().startsWith("http")) {
                OkHttpClient client = httpUtil.newBuilder().build();
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    return new DataUpdateResultDTO(response.code(), null, response.body().bytes());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            throw new IllegalArgumentException("Invalid URL");
        });
    }


    /**
     * 读取规则文件并转为IpList
     *
     * @param ruleFile 规则文件
     * @param ips      ip列表
     * @return 加载的行数
     */
    private int fileToIPList(File ruleFile, DualIPv4v6AssociativeTries<String> ips) throws IOException {
        return stringToIPList(new String(Files.toByteArray(ruleFile), StandardCharsets.UTF_8), ips);
    }

    /**
     * 读取规则文本并转为IpList
     *
     * @param data 规则文本
     * @param ips  ip列表
     * @return 加载的行数
     */
    private int stringToIPList(String data, DualIPv4v6AssociativeTries<String> ips) {
        AtomicInteger count = new AtomicInteger();
        StringJoiner sj = new StringJoiner("\n");
        for (String ele : data.split("\n")) {
            if (ele.isBlank()) continue;
            if (ele.startsWith("#")) {
                // add into sj but without hashtag prefix
                sj.add(ele.substring(1));
                continue;
            }
            if (ele.startsWith("//")) {
                // add into sj but without double slash prefix
                sj.add(ele.substring(2));
                continue;
            }
            try {
                var parsedIp = parseRuleLine(ele, sj.toString());
                if (parsedIp != null) {
                    count.getAndIncrement();
                    ips.put(parsedIp.getKey(), parsedIp.getValue());
                }
            } catch (Exception e) {
                log.error("Unable parse rule: {}", ele, e);
            } finally {
                sj = new StringJoiner("\n");
            }
        }
        return count.get();
    }

    private Map.Entry<IPAddress, @Nullable String> parseRuleLine(String ele, String preReadComment) {
        // 检查是否是 DAT/eMule 格式
        // 016.000.000.000 , 016.255.255.255 , 200 , Yet another organization
        // 032.000.000.000 , 032.255.255.255 , 200 , And another
        if (ele.contains(",")) {
            var spilted = ele.split(",");
            if (spilted.length < 3) {
                return null;
            }
            IPAddress start = IPAddressUtil.getIPAddress(spilted[0]);
            IPAddress end = IPAddressUtil.getIPAddress(spilted[1]);
            int level = Integer.parseInt(spilted[2]);
            String comment = spilted.length > 3 ? spilted[3] : preReadComment;
            if (level >= 128) return null;
            if (start == null || end == null) return null;
            return Map.entry(start.spanWithRange(end).coverWithPrefixBlock(), comment);
        } else {
            // ip #end-line-comment or ip //end-line-comment
            String ip;
            String comment = null;

            // Check for // comment first (to avoid conflict with protocol://)
            int doubleSlashIndex = ele.indexOf("//");
            int hashIndex = ele.indexOf("#");

            // Determine which comment delimiter appears first
            int commentIndex = -1;
            if (doubleSlashIndex != -1 && hashIndex != -1) {
                commentIndex = Math.min(doubleSlashIndex, hashIndex);
            } else if (doubleSlashIndex != -1) {
                commentIndex = doubleSlashIndex;
            } else if (hashIndex != -1) {
                commentIndex = hashIndex;
            }

            if (commentIndex != -1) {
                ip = ele.substring(0, commentIndex);
                comment = ele.substring(commentIndex + (ele.charAt(commentIndex) == '#' ? 1 : 2));
                return Map.entry(IPAddressUtil.getIPAddress(ip), Optional.ofNullable(comment).orElse(preReadComment));
            } else {
                return Map.entry(IPAddressUtil.getIPAddress(ele), preReadComment);
            }
        }
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
    public RuleSubInfoEntity getRuleSubInfo(String ruleId) {
        ConfigurationSection rules = getRuleSubsConfig();
        if (rules == null) {
            return null;
        }
        ConfigurationSection rule = rules.getConfigurationSection(ruleId);
        if (rule == null) {
            return null;
        }
        var result = ruleSubLogsDao.getLastLog(ruleId);
        OffsetDateTime lastUpdate = result == null ? TimeUtil.zeroOffsetDateTime : result.getUpdateTime();
        int count = result == null ? 0 : result.getCount();
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
        String ruleId = ruleSubInfo.getRuleId().trim();
        if (ruleId.contains(".")) {
            throw new IllegalArgumentException("Character '.' is not allowed.");
        }
        rules.set(ruleId + ".enabled", ruleSubInfo.isEnabled());
        rules.set(ruleId + ".name", ruleSubInfo.getRuleName().trim());
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
     * @param ruleId 规则ID
     * @return 规则订阅日志
     */
    public Page<RuleSubLogEntity> queryRuleSubLogs(String ruleId, Pageable pageable) {
        return (Page<RuleSubLogEntity>) ruleSubLogsDao.getLogs(pageable.toPage(), ruleId);
    }

    /**
     * 查询规则订阅日志数量
     *
     * @param ruleId 规则ID
     * @return 规则订阅日志数量
     */
    public long countRuleSubLogs(String ruleId) {
        return ruleSubLogsDao.countLogs(ruleId);
    }

    /**
     * 更改检查间隔
     * 会立即触发一次更新
     *
     * @param checkInterval 检查间隔
     * @throws IOException 保存异常
     */
    public void changeCheckInterval(long checkInterval) throws IOException {
        // 取消旧的定时任务
        cancelScheduledTask(reloadConfigTask);
        this.checkInterval = checkInterval;
        getConfig().set("check-interval", checkInterval);
        saveConfig();
        // 重新注册新的定时任务
        reloadConfigTask = registerScheduledTask(this::reloadConfig, 0, checkInterval, TimeUnit.MILLISECONDS);
    }

    private void preloadBanList() {

    }
}


