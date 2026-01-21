package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerNameRuleSubLogsDao;
import com.ghostchu.peerbanhelper.database.table.PeerNameRuleSubInfoEntity;
import com.ghostchu.peerbanhelper.database.table.PeerNameRuleSubLogEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.IPBanRuleUpdateType;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.module.impl.rule.dto.DataUpdateResultDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.query.Page;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.util.rule.MatchResultEnum;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.util.rule.matcher.PeerNameMatcher;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.j256.ormlite.stmt.SelectArg;
import io.sentry.Sentry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static com.ghostchu.peerbanhelper.Main.DEF_LOCALE;
import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * PeerName黑名单远程订阅模块
 */
@Slf4j
@Component
@Getter
public final class PeerNameBlackRuleList extends AbstractRuleFeatureModule implements Reloadable {
    private final PeerNameRuleSubLogsDao ruleSubLogsDao;
    private final ModuleMatchCache moduleMatchCache;
    private List<PeerNameMatcher> peerNameMatchers;
    private long checkInterval = 86400000; // 默认24小时检查一次
    private long banDuration;
    private ScheduledFuture<?> reloadConfigTask; // 保留此字段用于 changeCheckInterval() 方法
    @Autowired
    private HTTPUtil httpUtil;

    public PeerNameBlackRuleList(PeerNameRuleSubLogsDao ruleSubLogsDao, ModuleMatchCache moduleMatchCache) {
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
        return "PeerName Blacklist Rule List";
    }

    @Override
    public @NotNull String getConfigName() {
        return "peer-name-blocker-rules";
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
        String peerClientName = peer.getClientName();
        if (peerClientName == null || peerClientName.isEmpty()) {
            return pass();
        }

        for (PeerNameMatcher matcher : peerNameMatchers) {
            try {
                var matchResult = matcher.match(peerClientName);
                if (matchResult.result() == MatchResultEnum.TRUE) {
                    return new CheckResult(getClass(),
                            PeerAction.BAN,
                            banDuration,
                            new TranslationComponent(matcher.getRuleName()),
                            new TranslationComponent(Lang.MODULE_PNB_MATCH_PEER_NAME_RULE,
                                    matcher.getRuleName(),
                                    peerClientName,
                                    Optional.ofNullable(matchResult.comment()).orElse(new TranslationComponent("Unknown"))
                            ),
                            StructuredData.create().add("ruleName", matcher.getRuleName()));
                }
            } catch (Exception e) {
                log.error(tlUI(Lang.PEER_NAME_RULE_MATCH_ERROR), e);
                Sentry.captureException(e);
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
            if (null == peerNameMatchers) {
                peerNameMatchers = new ArrayList<>();
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
                        log.info(tlUI(Lang.PEER_NAME_RULE_UPDATE_FINISH));
                    } catch (Exception e) {
                        log.warn(tlUI(Lang.PEER_NAME_RULE_UPDATE_FAILED, rule.getString("name")), e);
                        Sentry.captureException(e);
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
            // 检查peerNameMatchers是否有对应的规则，有则删除
            peerNameMatchers.removeIf(ele -> ele.getRuleId().equals(ruleId));
            // 未启用跳过更新逻辑
            return new StdResp(false, tl(locale, Lang.PEER_NAME_RULE_DISABLED, ruleId), null);
        }
        String url = rule.getString("url");
        if (null != url) {
            // 解析远程订阅
            String ruleFileName = ruleId + ".txt";
            File dir = new File(Main.getDataDirectory(), "/peer-name-sub");
            dir.mkdirs();
            File ruleFile = new File(dir, ruleFileName);
            List<Map.Entry<Pattern, String>> patterns = new ArrayList<>();
            getResource(url)
                    .whenComplete((dataUpdateResultDTO, throwable) -> {
                        if (throwable != null) {
                            // 加载远程订阅文件出错,尝试从本地缓存中加载
                            if (ruleFile.exists()) {
                                // 如果一致，但peerNameMatchers没有对应的规则内容，则加载内容
                                if (peerNameMatchers.stream().noneMatch(ele -> ele.getRuleId().equals(ruleId))) {
                                    try {
                                        fileToPatternList(ruleFile, patterns);
                                        peerNameMatchers.add(new PeerNameMatcher(ruleId, name, List.of(patterns)));
                                        log.warn(tlUI(Lang.PEER_NAME_RULE_USE_CACHE, name));
                                        result.set(new StdResp(false, tl(locale, Lang.PEER_NAME_RULE_USE_CACHE, name), null));
                                    } catch (IOException ex) {
                                        log.error(tlUI(Lang.PEER_NAME_RULE_LOAD_FAILED, name), ex);
                                        result.set(new StdResp(false, tl(locale, Lang.PEER_NAME_RULE_LOAD_FAILED, name), null));
                                    }
                                } else {
                                    result.set(new StdResp(false, tl(locale, Lang.PEER_NAME_RULE_UPDATE_FAILED, name), null));
                                }
                            } else {
                                result.set(new StdResp(false, tl(locale, Lang.PEER_NAME_RULE_LOAD_FAILED, name), null));
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
                                ent_count = stringToPatternList(new String(dataUpdateResultDTO.data(), StandardCharsets.UTF_8), patterns);
                                // 更新后保存文件
                                Files.write(dataUpdateResultDTO.data(), ruleFile);
                            } else {
                                // 如果一致，但peerNameMatchers没有对应的规则内容，则加载内容
                                if (peerNameMatchers.stream().noneMatch(ele -> ele.getRuleId().equals(ruleId))) {
                                    ent_count = stringToPatternList(new String(dataUpdateResultDTO.data(), StandardCharsets.UTF_8), patterns);
                                } else {
                                    log.info(tlUI(Lang.PEER_NAME_RULE_NO_UPDATE, name));
                                    result.set(new StdResp(true, tl(locale, Lang.PEER_NAME_RULE_NO_UPDATE, name), null));
                                }
                            }
                            if (ent_count > 0) {
                                // 如果已经存在则更新，否则添加
                                peerNameMatchers.stream().filter(ele -> ele.getRuleId().equals(ruleId)).findFirst().ifPresentOrElse(ele -> {
                                    ele.setData(name, List.of(patterns));
                                    moduleMatchCache.invalidateAll();
                                    log.info(tlUI(Lang.PEER_NAME_RULE_UPDATE_SUCCESS, name));
                                    result.set(new StdResp(true, tl(locale, Lang.PEER_NAME_RULE_UPDATE_SUCCESS, name), null));
                                }, () -> {
                                    peerNameMatchers.add(new PeerNameMatcher(ruleId, name, List.of(patterns)));
                                    log.info(tlUI(Lang.PEER_NAME_RULE_LOAD_SUCCESS, name));
                                    result.set(new StdResp(true, tl(locale, Lang.PEER_NAME_RULE_LOAD_SUCCESS, name), null));
                                });
                                // 更新日志
                                try {
                                    ruleSubLogsDao.create(new PeerNameRuleSubLogEntity(null, ruleId, System.currentTimeMillis(), ent_count, updateType));
                                    result.set(new StdResp(true, tl(locale, Lang.PEER_NAME_RULE_UPDATED, name), null));
                                } catch (SQLException e) {
                                    log.error(tlUI(Lang.PEER_NAME_RULE_UPDATE_LOG_ERROR, ruleId), e);
                                    result.set(new StdResp(false, tl(locale, Lang.PEER_NAME_RULE_UPDATE_LOG_ERROR, name), null));
                                }
                            } else {
                                result.set(new StdResp(true, tl(locale, Lang.PEER_NAME_RULE_NO_UPDATE, name), null));
                            }
                        } catch (IOException e) {
                            Sentry.captureException(e);
                            throw new RuntimeException(e);
                        } finally {
                            moduleMatchCache.invalidateAll();
                        }
                    }).join();
        } else {
            result.set(new StdResp(false, tl(locale, Lang.PEER_NAME_RULE_URL_WRONG, name), null));
        }
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
        }, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * 读取规则文件并转为Pattern列表
     *
     * @param ruleFile 规则文件
     * @param patterns pattern列表
     * @return 加载的行数
     */
    private int fileToPatternList(File ruleFile, List<Map.Entry<Pattern, String>> patterns) throws IOException {
        AtomicInteger count = new AtomicInteger();
        StringJoiner sj = new StringJoiner("\n");
        var lines = Files.readLines(ruleFile, StandardCharsets.UTF_8);
        for (String ele : lines) {
            if (ele.isBlank()) continue;
            if (ele.startsWith("#")) {
                // add into sj but without hashtag prefix
                sj.add(ele.substring(1).trim());
                continue;
            }
            try {
                var parsed = parseRuleLine(ele, sj.toString());
                if (parsed != null) {
                    count.getAndIncrement();
                    patterns.add(parsed);
                }
            } catch (Exception e) {
                log.error("Unable parse rule: {}", ele, e);
                Sentry.captureException(e);
            } finally {
                sj = new StringJoiner("\n");
            }
        }
        return count.get();
    }

    /**
     * 读取规则文本并转为Pattern列表
     *
     * @param data     规则文本
     * @param patterns pattern列表
     * @return 加载的行数
     */
    private int stringToPatternList(String data, List<Map.Entry<Pattern, String>> patterns) {
        AtomicInteger count = new AtomicInteger();
        StringJoiner sj = new StringJoiner("\n");
        for (String ele : data.split("\n")) {
            if (ele.isBlank()) continue;
            if (ele.startsWith("#")) {
                // add into sj but without hashtag prefix
                sj.add(ele.substring(1).trim());
                continue;
            }
            try {
                var parsed = parseRuleLine(ele, sj.toString());
                if (parsed != null) {
                    count.getAndIncrement();
                    patterns.add(parsed);
                }
            } catch (Exception e) {
                log.error("Unable parse rule: {}", ele, e);
                Sentry.captureException(e);
            } finally {
                sj = new StringJoiner("\n");
            }
        }
        return count.get();
    }

    /**
     * 解析规则行
     * 支持格式:
     * - 纯正则: .*Xunlei.*
     * - 带行尾注释: BitComet/\d+\.\d+ #匹配BitComet
     *
     * @param line           规则行
     * @param preReadComment 预读的注释
     * @return Pattern和注释的Entry
     */
    private Map.Entry<Pattern, String> parseRuleLine(String line, String preReadComment) {
        String regex;
        String comment = preReadComment;

        // 检查是否有行尾注释
        int commentIndex = line.indexOf(" #");
        if (commentIndex > 0) {
            regex = line.substring(0, commentIndex).trim();
            comment = line.substring(commentIndex + 2).trim();
        } else {
            regex = line.trim();
        }

        if (regex.isEmpty()) {
            return null;
        }

        try {
            Pattern pattern = Pattern.compile(regex);
            return new AbstractMap.SimpleEntry<>(pattern, comment.isEmpty() ? regex : comment);
        } catch (Exception e) {
            log.error("Invalid regex pattern: {}", regex, e);
            Sentry.captureException(e);
            return null;
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
    public PeerNameRuleSubInfoEntity getRuleSubInfo(String ruleId) throws SQLException {
        ConfigurationSection rules = getRuleSubsConfig();
        if (rules == null) {
            return null;
        }
        ConfigurationSection rule = rules.getConfigurationSection(ruleId);
        if (rule == null) {
            return null;
        }

        var result = ruleSubLogsDao.queryByPaging(ruleSubLogsDao.queryBuilder().orderBy("id", false).where().eq("ruleId", new SelectArg(ruleId)).queryBuilder(), new Pageable(1, 1)).getResults();
        Optional<PeerNameRuleSubLogEntity> first = result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
        long lastUpdate = first.map(PeerNameRuleSubLogEntity::getUpdateTime).orElse(0L);
        int count = first.map(PeerNameRuleSubLogEntity::getCount).orElse(0);
        return new PeerNameRuleSubInfoEntity(ruleId, rule.getBoolean("enabled", false), rule.getString("name", ruleId), rule.getString("url"), lastUpdate, count);
    }

    /**
     * 保存规则订阅信息并返回保存后的信息
     *
     * @param ruleSubInfo 规则订阅信息
     * @return 保存后的规则订阅信息
     * @throws IOException 保存异常
     */
    public ConfigurationSection saveRuleSubInfo(@NotNull PeerNameRuleSubInfoEntity ruleSubInfo) throws IOException {
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
     * @throws SQLException 查询异常
     */
    public Page<PeerNameRuleSubLogEntity> queryRuleSubLogs(String ruleId, Pageable pageable) throws SQLException {
        var builder = ruleSubLogsDao.queryBuilder().orderBy("updateTime", false);
        if (ruleId != null) {
            builder = builder.where().eq("ruleId", new SelectArg(ruleId)).queryBuilder();
        }
        return ruleSubLogsDao.queryByPaging(builder, pageable);
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
            builder = builder.where().eq("ruleId", new SelectArg(ruleId)).queryBuilder();
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
        // 取消旧的定时任务
        cancelScheduledTask(reloadConfigTask);
        this.checkInterval = checkInterval;
        getConfig().set("check-interval", checkInterval);
        saveConfig();
        // 重新注册新的定时任务
        reloadConfigTask = registerScheduledTask(this::reloadConfig, 0, checkInterval, TimeUnit.MILLISECONDS);
    }
}
