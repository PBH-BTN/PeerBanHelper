package com.ghostchu.peerbanhelper.module.impl.rule.subscription;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.database.dao.impl.EnhancedRuleSubLogDao;
import com.ghostchu.peerbanhelper.database.table.EnhancedRuleSubInfoEntity;
import com.ghostchu.peerbanhelper.database.table.EnhancedRuleSubLogEntity;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.IPBanRuleUpdateType;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.module.impl.rule.subscription.matcher.*;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.MatchResultEnum;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * 增强版规则订阅模块 - 支持多种规则类型
 * Enhanced rule subscription module - supports multiple rule types
 */
@Slf4j
@Component
@Getter
public final class EnhancedRuleSubscriptionModule extends AbstractRuleFeatureModule implements Reloadable {
    private final EnhancedRuleSubLogDao enhancedRuleSubLogDao;
    private final ModuleMatchCache moduleMatchCache;
    private List<EnhancedRuleMatcher> ruleMatchers;
    private long checkInterval = 86400000; // Default 24 hours check interval
    private long banDuration;
    
    @Autowired
    private HTTPUtil httpUtil;
    
    public EnhancedRuleSubscriptionModule(EnhancedRuleSubLogDao enhancedRuleSubLogDao, ModuleMatchCache moduleMatchCache) {
        super();
        this.enhancedRuleSubLogDao = enhancedRuleSubLogDao;
        this.moduleMatchCache = moduleMatchCache;
    }
    
    @Override
    public boolean isConfigurable() {
        return true;
    }
    
    @Override
    public @NotNull String getName() {
        return "Enhanced Rule Subscription";
    }
    
    @Override
    public @NotNull String getConfigName() {
        return "enhanced-rule-subscription";
    }
    
    @Override
    public void onEnable() {
        if (null == ruleMatchers) {
            ruleMatchers = new ArrayList<>();
        }
        
        var config = getConfig();
        checkInterval = config.getLong("check-interval", checkInterval);
        banDuration = config.getLong("ban-duration", 0);
        
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::reloadConfig, 0, checkInterval, TimeUnit.MILLISECONDS);
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
        
        // Test different peer attributes based on rule type
        for (EnhancedRuleMatcher matcher : ruleMatchers) {
            try {
                MatchResult result = null;
                String testContent = null;
                
                switch (matcher.getRuleType()) {
                    case IP_BLACKLIST:
                        testContent = peer.getPeerAddress().getIp();
                        result = matcher.match(testContent);
                        break;
                    case PEER_ID:
                        testContent = peer.getPeerClientName(); // or peer ID if available
                        result = matcher.match(testContent);
                        break;
                    case CLIENT_NAME:
                        testContent = peer.getPeerClientName();
                        result = matcher.match(testContent);
                        break;
                    case SUBSTRING_MATCH:
                        testContent = peer.getPeerClientName();
                        result = matcher.match(testContent);
                        break;
                    case PREFIX_MATCH:
                        testContent = peer.getPeerClientName();
                        result = matcher.match(testContent);
                        break;
                    case EXCEPTION_LIST:
                        // Exception lists should have reverse logic - they allow connections
                        continue;
                    case SCRIPT_ENGINE:
                        // TODO: Implement script engine evaluation
                        continue;
                }
                
                if (result != null && result.result() == MatchResultEnum.TRUE) {
                    return new CheckResult(getClass(),
                            PeerAction.BAN,
                            banDuration,
                            new TranslationComponent(matcher.getRuleName()),
                            new TranslationComponent("Enhanced rule match: " + matcher.getRuleType() + " - " + testContent),
                            null);
                }
            } catch (Exception e) {
                log.error("Error checking enhanced rule: {}", matcher.getRuleName(), e);
            }
        }
        
        return pass();
    }
    
    /**
     * 重新加载配置
     * Reload configuration
     */
    private void reloadConfig() {
        getCache().invalidateAll();
        try {
            var config = getConfig();
            this.banDuration = config.getLong("ban-duration", 0);
            this.checkInterval = config.getLong("check-interval", checkInterval);
            
            // Load rules from database would go here
            // For now, we'll implement basic functionality
            
            log.info("Enhanced rule subscription configuration reloaded");
            getCache().invalidateAll();
        } catch (Throwable throwable) {
            log.error("Unable to complete enhanced rule subscription reload", throwable);
        }
    }
    
    /**
     * 从远程URL获取规则数据
     * Get rule data from remote URL
     */
    private CompletableFuture<byte[]> getResource(String url) {
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
                    return response.body().bytes();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            throw new IllegalArgumentException("Invalid URL");
        });
    }
    
    /**
     * 解析规则文本为不同类型的规则数据
     * Parse rule text into different types of rule data
     */
    private List<?> parseRuleData(String data, RuleType ruleType) {
        List<String> lines = Arrays.asList(data.split("\n"));
        List<String> rules = new ArrayList<>();
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            rules.add(line);
        }
        
        switch (ruleType) {
            case IP_BLACKLIST:
                return parseIPRules(rules);
            case PEER_ID:
            case CLIENT_NAME:
            case SUBSTRING_MATCH:
            case PREFIX_MATCH:
                return List.of(rules);
            default:
                return List.of(rules);
        }
    }
    
    /**
     * 解析IP规则并返回内存优化的存储结构
     * Parse IP rules and return memory-optimized storage structure
     */
    private List<DualIPv4v6AssociativeTries<String>> parseIPRules(List<String> lines) {
        DualIPv4v6AssociativeTries<String> ipTries = new DualIPv4v6AssociativeTries<>();
        
        for (String line : lines) {
            try {
                // Parse different IP formats
                if (line.contains(",")) {
                    // DAT/eMule format: start,end,level,comment
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        IPAddress start = IPAddressUtil.getIPAddress(parts[0].trim());
                        IPAddress end = IPAddressUtil.getIPAddress(parts[1].trim());
                        int level = Integer.parseInt(parts[2].trim());
                        String comment = parts.length > 3 ? parts[3].trim() : "";
                        
                        if (level < 128 && start != null && end != null) {
                            ipTries.put(start.spanWithRange(end).coverWithPrefixBlock(), comment);
                        }
                    }
                } else {
                    // Simple IP or CIDR format
                    String ip = line;
                    String comment = "";
                    if (line.contains("#")) {
                        String[] parts = line.split("#", 2);
                        ip = parts[0].trim();
                        comment = parts[1].trim();
                    }
                    
                    IPAddress ipAddress = IPAddressUtil.getIPAddress(ip);
                    if (ipAddress != null) {
                        ipTries.put(ipAddress, comment);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse IP rule: {}", line, e);
            }
        }
        
        return List.of(ipTries);
    }
    
    /**
     * 创建对应类型的规则匹配器
     * Create rule matcher for the specified type
     */
    private EnhancedRuleMatcher createMatcher(String ruleId, String ruleName, RuleType ruleType, List<?> data) {
        switch (ruleType) {
            case IP_BLACKLIST:
                @SuppressWarnings("unchecked")
                List<DualIPv4v6AssociativeTries<String>> ipData = (List<DualIPv4v6AssociativeTries<String>>) data;
                return new EnhancedIPMatcher(ruleId, ruleName, ipData);
            case PEER_ID:
                @SuppressWarnings("unchecked")
                List<String> peerIdData = (List<String>) data.get(0);
                return new PeerIdMatcher(ruleId, ruleName, peerIdData);
            case CLIENT_NAME:
                @SuppressWarnings("unchecked")
                List<String> clientData = (List<String>) data.get(0);
                return new ClientNameMatcher(ruleId, ruleName, clientData);
            case SUBSTRING_MATCH:
                @SuppressWarnings("unchecked")
                List<String> substringData = (List<String>) data.get(0);
                return new SubstringMatcher(ruleId, ruleName, substringData);
            case PREFIX_MATCH:
                @SuppressWarnings("unchecked")
                List<String> prefixData = (List<String>) data.get(0);
                return new PrefixMatcher(ruleId, ruleName, prefixData);
            default:
                throw new IllegalArgumentException("Unsupported rule type: " + ruleType);
        }
    }
    
    /**
     * 获取检查间隔
     * Get check interval
     */
    public long getCheckInterval() {
        return checkInterval;
    }
    
    /**
     * 更改检查间隔
     * Change check interval
     */
    public void changeCheckInterval(long checkInterval) throws IOException {
        this.checkInterval = checkInterval;
        getConfig().set("check-interval", checkInterval);
        saveConfig();
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::reloadConfig, 0, checkInterval, TimeUnit.MILLISECONDS);
    }
}