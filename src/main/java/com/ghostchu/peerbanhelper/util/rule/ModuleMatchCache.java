package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.BtnRuleUpdateEvent;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.module.RuleFeatureModule;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ModuleMatchCache {
    public final Cache<String, CheckResult> CACHE_POOL = CacheBuilder
            .newBuilder()
            .maximumWeight(ExternalSwitch.parseLong("pbh.moduleMatchCache.weight", 50000L))
            .weigher((key, value) -> {
                if (value == AbstractRuleFeatureModule.HANDSHAKING_CHECK_RESULT
                    || value == AbstractRuleFeatureModule.TEAPOT_CHECK_RESULT
                    || value == AbstractRuleFeatureModule.OK_CHECK_RESULT) {
                    return 1;
                }
                return 5;
            })
            .softValues()
            .expireAfterAccess(ExternalSwitch.parseLong("pbh.modulematchcache.timeout", 600000), TimeUnit.MILLISECONDS)
            .build();

    public ModuleMatchCache() {
        Main.getEventBus().register(this);
    }

    public CheckResult readCache(RuleFeatureModule module, String cacheKey, Callable<CheckResult> resultSupplier, boolean writeCache) {
        String _cacheKey = module.getConfigName() + '@' + cacheKey;
        if (writeCache) {
            try {
                return CACHE_POOL.get(_cacheKey, resultSupplier);
            } catch (ExecutionException e) {
                log.error("Unable to get cache value from cache, the resultSupplier throws unexpected exception", e);
                return null;
            }
        } else {
            return CACHE_POOL.getIfPresent(_cacheKey);
        }
    }

    // 只缓存 PeerAction 为 BAN 以外的结果
    public CheckResult readCacheButWritePassOnly(RuleFeatureModule module, String cacheKey, Callable<CheckResult> resultSupplier, boolean writeCache) {
        String _cacheKey = module.getConfigName() + '@' + cacheKey;
        var cached = CACHE_POOL.getIfPresent(_cacheKey);
        if (cached == null) {
            try {
                cached = resultSupplier.call();
            } catch (Exception e) {
                log.warn("Unable to compute result", e);
            }
        }
        if (writeCache && cached != null && cached.action() != PeerAction.BAN) {
            CACHE_POOL.put(_cacheKey, cached);
        }
        return cached;

    }

    @Subscribe
    public void onBtnRuleUpdated(BtnRuleUpdateEvent event) {
        CACHE_POOL.invalidateAll();
    }

    public void invalidateAll() {
        CACHE_POOL.invalidateAll();
    }

    public void close() {
        Main.getEventBus().unregister(this);
    }

}
