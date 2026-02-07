package com.ghostchu.peerbanhelper.btn.ability.impl;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.AbstractBtnAbility;
import com.ghostchu.peerbanhelper.databasent.service.MetadataService;
import com.ghostchu.peerbanhelper.event.btn.BtnRuleUpdateEvent;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.URLUtil;
import com.ghostchu.peerbanhelper.util.backgroundtask.FunctionalBackgroundTask;
import com.ghostchu.peerbanhelper.util.rule.matcher.IPMatcher;
import com.google.gson.JsonObject;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class BtnAbilityIPAllowList extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final String endpoint;
    private final long randomInitialDelay;
    @Getter
    private final IPMatcher ipMatcher = new IPMatcher("btn-ip-allowlist", "Empty IP Allowlist", List.of(new DualIPv4v6AssociativeTries<>()));
    private final MetadataService metadataDao;
    private final boolean powCaptcha;
    private String ruleVersion = "initial";

    public BtnAbilityIPAllowList(BtnNetwork btnNetwork, MetadataService metadataDao, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.metadataDao = metadataDao;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
        this.powCaptcha = ability.has("pow_captcha") && ability.get("pow_captcha").getAsBoolean();
        setLastStatus(true, new TranslationComponent(Lang.BTN_STAND_BY));
    }

    private void loadCacheFile() {
        String cacheVersion = metadataDao.get("btn.ability.ip_allowlist.cache.version");
        String cacheValue = metadataDao.get("btn.ability.ip_allowlist.cache.value");
        if (cacheValue != null) {
            DualIPv4v6AssociativeTries<String> associativeTries = new DualIPv4v6AssociativeTries<>();
            var loaded = stringToIPList(cacheValue, associativeTries);
            ipMatcher.setData("BTN AllowList (Local Cache)", List.of(associativeTries));
            ruleVersion = cacheVersion;
            log.debug("[BTN AllowList] Loaded {} IP rules from cache file. Cached version: {}", loaded, cacheVersion);
        }
    }

    @Override
    public String getName() {
        return "BtnAbilityIPAllowList";
    }

    @Override
    public TranslationComponent getDisplayName() {
        return new TranslationComponent(Lang.BTN_ABILITY_IP_ALLOWLIST_TITLE);
    }

    @Override
    public TranslationComponent getDescription() {
        return new TranslationComponent(Lang.BTN_ABILITY_IP_ALLOWLIST_DESCRIPTION,
                ruleVersion,
                ipMatcher.size()
        );
    }

    @Override
    public void load() {
        try {
            loadCacheFile();
            setLastStatus(true, new TranslationComponent(Lang.BTN_ABILITY_IP_ALLOWLIST_LOADED_FROM_CACHE, ruleVersion, ipMatcher.size()));
        } catch (Exception e) {
            log.error(tlUI(Lang.BTN_ABILITY_IP_ALLOWLIST_LOAD_FAILED_FROM_CACHE), e);
            setLastStatus(false, new TranslationComponent(e.getClass().getName() + ": " + e.getMessage()));
        }
        btnNetwork.getScheduler().scheduleWithFixedDelay(this::updateRule, ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void updateRule() {
        btnNetwork.getBackgroundTaskManager().addTaskAsync(new FunctionalBackgroundTask(new TranslationComponent(Lang.BTN_ABILITY_ALLOW_LIST_SYNC_SERVER), (task, callback) -> {
            String version = Objects.requireNonNullElse(ruleVersion, "initial");
            String url = URLUtil.appendUrl(endpoint, Map.of("rev", version));
            Request.Builder request = new Request.Builder()
                    .url(url)
                    .get();
            if (powCaptcha) btnNetwork.gatherAndSolveCaptchaBlocking(request, "ip_allowlist");
            try (Response response = btnNetwork.getHttpClient().newCall(request.build()).execute()) {
                if (response.code() == 204) {
                    setLastStatus(true, new TranslationComponent(Lang.BTN_ABILITY_IP_ALLOWLIST_LOADED_FROM_REMOTE_NO_CHANGES, version, ipMatcher.size()));
                    return;
                }
                String responseBody = response.body().string();
                if (!response.isSuccessful()) {
                    log.error(tlUI(Lang.BTN_REQUEST_FAILS, response.code() + " - " + responseBody));
                    setLastStatus(false, new TranslationComponent(Lang.BTN_HTTP_ERROR, response.code(), responseBody));
                } else {
                    DualIPv4v6AssociativeTries<String> associativeTries = new DualIPv4v6AssociativeTries<>();
                    var loaded = stringToIPList(responseBody, associativeTries);
                    this.ipMatcher.setData("BTN AllowList (Remote)", List.of(associativeTries));
                    Main.getEventBus().post(new BtnRuleUpdateEvent());
                    ruleVersion = response.header("X-BTN-ContentVersion", "unknown");
                    metadataDao.set("btn.ability.ip_allowlist.cache.version", ruleVersion);
                    metadataDao.set("btn.ability.ip_allowlist.cache.value", responseBody);
                    log.info(tlUI(Lang.BTN_ABILITY_IP_ALLOWLIST_LOADED_FROM_REMOTE, ruleVersion, loaded));
                    setLastStatus(true, new TranslationComponent(Lang.BTN_ABILITY_IP_ALLOWLIST_LOADED_FROM_REMOTE, ruleVersion, loaded));
                    btnNetwork.getModuleMatchCache().invalidateAll();
                }
            } catch (Exception e) {
                log.error(tlUI(Lang.BTN_REQUEST_FAILS), e);
                setLastStatus(false, new TranslationComponent(Lang.BTN_UNKNOWN_ERROR, e.getClass().getName() + ": " + e.getMessage()));
            }
        })).join();
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
            // ip #end-line-comment
            String ip;
            if (ele.contains("#")) {
                ip = ele.substring(0, ele.indexOf("#"));
                String comment = null;
                if (ele.contains("#")) {
                    comment = ele.substring(ele.indexOf("#") + 1);
                }
                return Map.entry(IPAddressUtil.getIPAddress(ip), Optional.ofNullable(comment).orElse(preReadComment));
            } else {
                return Map.entry(IPAddressUtil.getIPAddress(ele), preReadComment);
            }
        }
    }

    @Override
    public void unload() {

    }
}
