package com.ghostchu.peerbanhelper.banlist;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public final class BanlistRuleProviderRegistry {
    private final CopyOnWriteArrayList<BanlistRuleProvider> providers = new CopyOnWriteArrayList<>();

    public void register(@NotNull BanlistRuleProvider provider) {
        if (!providers.contains(provider)) {
            providers.add(provider);
        }
    }

    @NotNull
    public List<BanlistRuleProvider> getProviders() {
        return List.copyOf(providers);
    }
}
