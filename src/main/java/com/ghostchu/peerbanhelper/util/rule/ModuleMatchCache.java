package com.ghostchu.peerbanhelper.util.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.event.BtnRuleUpdateEvent;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.RuleFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.rule.BtnNetworkOnline;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.eventbus.Subscribe;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ModuleMatchCache {
    public final Map<RuleFeatureModule, Cache<Wrapper, Boolean>> CACHE_POOL = new ConcurrentHashMap<>();
    private final long banDuration;
    private final PeerBanHelperServer server;

    public ModuleMatchCache(PeerBanHelperServer server, long banDuration) {
        this.server = server;
        this.banDuration = banDuration;
        Main.getEventBus().register(this);
    }

    public boolean shouldSkipCheck(RuleFeatureModule module, Torrent torrent, PeerAddress peerAddress, boolean writeCache) {
        Wrapper wrapper = new Wrapper(torrent.getId(), peerAddress);
        Cache<Wrapper, Boolean> ruleCacheZone = CACHE_POOL.get(module);
        if (ruleCacheZone == null) {
            ruleCacheZone = CacheBuilder.newBuilder()
                    .expireAfterAccess(banDuration, TimeUnit.MILLISECONDS)
                    .recordStats()
                    .build();
            CACHE_POOL.put(module, ruleCacheZone);
        }
        Boolean cached = ruleCacheZone.getIfPresent(wrapper);
        boolean result = cached != null;
        if (writeCache) {
            ruleCacheZone.put(wrapper, result);
        }
        return result;
    }

    @Subscribe
    public void onBtnRuleUpdated(BtnRuleUpdateEvent event) {
        for (FeatureModule featureModule : CACHE_POOL.keySet()) {
            if (featureModule.getClass().equals(BtnNetworkOnline.class)) {
                CACHE_POOL.remove(featureModule);
                return;
            }
        }
    }

    public void close() {
        Main.getEventBus().unregister(this);
    }

    public record Wrapper(String torrentId, PeerAddress address) {

    }
}
