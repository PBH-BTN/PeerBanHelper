package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.BtnRuleUpdateEvent;
import com.ghostchu.peerbanhelper.module.CheckResult;
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
            .maximumSize(15000)
            .softValues()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    public ModuleMatchCache() {
        Main.getEventBus().register(this);
    }

    public CheckResult readCache(RuleFeatureModule module, String cacheKey, Callable<CheckResult> resultSupplier, boolean writeCache) {
        String _cacheKey = module.getConfigName() + "@" + cacheKey;
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
