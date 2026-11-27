package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.banlist.BanlistRuleProviderRegistry;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerImpl;
import com.ghostchu.peerbanhelper.bittorrent.torrent.TorrentImpl;
import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import com.ghostchu.peerbanhelper.event.banlist.BanlistRuleProviderUpdateEvent;
import com.ghostchu.peerbanhelper.event.program.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.StructuredData;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.eventbus.Subscribe;
import inet.ipaddr.IPAddress;
import inet.ipaddr.format.util.AssociativeAddressTrie;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
@Component
public class BanList implements Reloadable {
    private final PrimaryBanList primaryBanList;
    private final PrefilledBanList prefilledBanList;
    private final BanlistRuleProviderRegistry providerRegistry;

    public BanList(PrimaryBanList primaryBanList, PrefilledBanList prefilledBanList,
                   BanlistRuleProviderRegistry providerRegistry) {
        this.primaryBanList = primaryBanList;
        this.prefilledBanList = prefilledBanList;
        this.providerRegistry = providerRegistry;
        Main.getEventBus().register(this);
        Main.getReloadManager().register(this);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        refreshPrefilledBanList();
        return Reloadable.super.reloadModule();
    }

    @Subscribe
    public void onServerStarted(PBHServerStartedEvent event) {
        refreshPrefilledBanList();
    }

    @Subscribe
    public void onBanlistRuleProviderUpdate(BanlistRuleProviderUpdateEvent event) {
        refreshPrefilledBanList();
    }

    @NotNull
    public Map<IPAddress, BanMetadata> toMap() {
        Map<IPAddress, BanMetadata> map = new HashMap<>(prefilledBanList.toMap());
        map.putAll(primaryBanList.toMap());
        return map;
    }

    @NotNull
    public Map<IPAddress, BanMetadata> toPrimaryMap() {
        return primaryBanList.toMap();
    }

    @NotNull
    public Map<IPAddress, BanMetadata> toPrefilledMap() {
        return prefilledBanList.toMap();
    }

    @Nullable
    public BanMetadata add(@NotNull PeerAddress address, @NotNull BanMetadata metadata) {
        return primaryBanList.add(address, metadata);
    }

    @Nullable
    public AssociativeAddressTrie.AssociativeTrieNode<? extends IPAddress, BanMetadata> remove(@NotNull PeerAddress address) {
        return primaryBanList.remove(address);
    }

    @Nullable
    public AssociativeAddressTrie.AssociativeTrieNode<? extends IPAddress, BanMetadata> remove(@NotNull IPAddress address) {
        return primaryBanList.remove(address);
    }

    public int size() {
        return primaryBanList.size() + prefilledBanList.size();
    }

    @NotNull
    public DualIPv4v6AssociativeTries<BanMetadata> copy() {
        var copy = prefilledBanList.copy();
        primaryBanList.forEach(copy::put);
        return copy;
    }

    public void directAccess(boolean writeLock, @NotNull Consumer<DualIPv4v6AssociativeTries<BanMetadata>> consumer) {
        primaryBanList.directAccess(writeLock, consumer);
    }

    @NotNull
    public Set<IPAddress> copyKeySet() {
        Set<IPAddress> ipAddresses = new HashSet<>(prefilledBanList.copyKeySet());
        ipAddresses.addAll(primaryBanList.copyKeySet());
        return ipAddresses;
    }

    @NotNull
    public Set<IPAddress> copyPrimaryKeySet() {
        return primaryBanList.copyKeySet();
    }

    @NotNull
    public Set<IPAddress> copyPrefilledKeySet() {
        return prefilledBanList.copyKeySet();
    }

    @NotNull
    public List<IPAddress> copyValues() {
        List<IPAddress> ipAddresses = new ArrayList<>(prefilledBanList.copyValues());
        ipAddresses.addAll(primaryBanList.copyValues());
        return ipAddresses;
    }

    public boolean contains(@NotNull PeerAddress address) {
        return primaryBanList.contains(address) || prefilledBanList.contains(address);
    }

    public boolean contains(@NotNull IPAddress address) {
        return primaryBanList.contains(address) || prefilledBanList.contains(address);
    }

    @Nullable
    public BanMetadata get(@NotNull PeerAddress address) {
        var metadata = primaryBanList.get(address);
        return metadata != null ? metadata : prefilledBanList.get(address);
    }

    @Nullable
    public BanMetadata get(@NotNull IPAddress address) {
        var metadata = primaryBanList.get(address);
        return metadata != null ? metadata : prefilledBanList.get(address);
    }

    public void addAll(@NotNull Map<IPAddress, BanMetadata> map) {
        primaryBanList.addAll(map);
    }

    public void forEach(@NotNull BiConsumer<? super IPAddress, ? super BanMetadata> action) {
        prefilledBanList.forEach(action);
        primaryBanList.forEach(action);
    }

    public void reset() {
        prefilledBanList.reset();
        primaryBanList.reset();
    }

    public void resetPrimary() {
        primaryBanList.reset();
    }

    public void readStream(@NotNull Consumer<Stream<AssociativeAddressTrie.AssociativeTrieNode<? extends IPAddress, BanMetadata>>> streamConsumer) {
        List<AssociativeAddressTrie.AssociativeTrieNode<? extends IPAddress, BanMetadata>> nodes = new ArrayList<>();
        prefilledBanList.readStream(stream -> stream.forEach(nodes::add));
        primaryBanList.readStream(stream -> stream.forEach(nodes::add));
        streamConsumer.accept(nodes.stream());
    }

    public PrimaryBanList getPrimaryBanList() {
        return primaryBanList;
    }

    public PrefilledBanList getPrefilledBanList() {
        return prefilledBanList;
    }

    public synchronized void refreshPrefilledBanList() {
        boolean enabled = Main.getMainConfig().getBoolean("banlist-prefill.enabled", false);
        Map<IPAddress, AggregatedRule> aggregatedRules = enabled ? collectPrefilledRules() : Map.of();
        Map<IPAddress, BanMetadata> newBanList = createPrefilledBanMetadata(aggregatedRules);
        if (!hasPrefilledChanges(newBanList)) {
            return;
        }
        int oldSize = prefilledBanList.size();
        prefilledBanList.replaceAll(newBanList);
        markNextBanWaveAsFullUpdate();
        if (enabled) {
            log.info("Prefilled ban list refreshed: {} addresses from {} providers",
                    newBanList.size(), providerRegistry.getProviders().size());
        } else {
            log.info("Prefilled ban list disabled; cleared {} addresses", oldSize);
        }
    }

    private Map<IPAddress, AggregatedRule> collectPrefilledRules() {
        Map<IPAddress, AggregatedRule> merged = new LinkedHashMap<>();
        for (var provider : providerRegistry.getProviders()) {
            try {
                var providerName = provider.getBanlistProviderName();
                provider.getBanlist().forEach((address, rule) -> {
                    if (address == null) {
                        return;
                    }
                    merged.computeIfAbsent(address.toPrefixBlock(), ignored -> new AggregatedRule())
                            .add(providerName, rule);
                });
            } catch (Exception e) {
                log.warn("Unable to load ban list from provider {}", provider.getClass().getName(), e);
            }
        }
        return merged;
    }

    private Map<IPAddress, BanMetadata> createPrefilledBanMetadata(Map<IPAddress, AggregatedRule> rules) {
        Map<IPAddress, BanMetadata> result = new LinkedHashMap<>();
        OffsetDateTime now = OffsetDateTime.now();
        for (var entry : rules.entrySet()) {
            IPAddress address = entry.getKey();
            AggregatedRule rule = entry.getValue();
            String addressString = address.toCompressedString();
            PeerAddress peerAddress = new PeerAddress(addressString, 0, addressString);
            var torrent = new TorrentImpl("prefilled-banlist", "Prefilled Ban List",
                    "prefilled-banlist", 0, 0, 0, 0, 0, false);
            var peer = new PeerImpl(peerAddress, "-PBHPRE-".getBytes(StandardCharsets.ISO_8859_1),
                    "Prefilled Ban List", 0, 0, 0, 0, 0, null, false);
            String providerNames = String.join(", ", rule.providers());
            String ruleText = rule.rules().isEmpty() ? providerNames : String.join("\n", rule.rules());
            String randomId = UUID.nameUUIDFromBytes((addressString + "|" + providerNames)
                    .getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
            BanMetadata metadata = new BanMetadata(
                    "prefilled-banlist",
                    randomId,
                    new DownloaderBasicInfo("prefilled-banlist", "Prefilled Ban List", "prefilled"),
                    now,
                    OffsetDateTime.MAX,
                    false,
                    false,
                    false,
                    torrent,
                    peer,
                    new TranslationComponent(Lang.BANLIST_PREFILLED_RULE, ruleText),
                    new TranslationComponent(Lang.BANLIST_PREFILLED_DESCRIPTION, providerNames),
                    StructuredData.create()
                            .add("type", "prefilled")
                            .add("providers", providerNames)
            );
            result.put(address, metadata);
        }
        return result;
    }

    private boolean hasPrefilledChanges(Map<IPAddress, BanMetadata> newBanList) {
        Map<IPAddress, BanMetadata> oldBanList = prefilledBanList.toMap();
        if (!oldBanList.keySet().equals(newBanList.keySet())) {
            return true;
        }
        for (var entry : newBanList.entrySet()) {
            BanMetadata oldMetadata = oldBanList.get(entry.getKey());
            if (oldMetadata == null
                    || translationChanged(oldMetadata.getRule(), entry.getValue().getRule())
                    || translationChanged(oldMetadata.getDescription(), entry.getValue().getDescription())) {
                return true;
            }
        }
        return false;
    }

    private boolean translationChanged(TranslationComponent oldComponent, TranslationComponent newComponent) {
        return !Objects.equals(oldComponent.getKey(), newComponent.getKey())
                || !Objects.deepEquals(oldComponent.getParams(), newComponent.getParams());
    }

    private void markNextBanWaveAsFullUpdate() {
        var server = Main.getServer();
        if (server != null) {
            server.getDownloaderServer().getNeedReApplyBanList().set(true);
        }
    }

    private static final class AggregatedRule {
        private final Set<String> providers = new LinkedHashSet<>();
        private final Set<String> rules = new LinkedHashSet<>();

        private void add(String provider, @Nullable String rule) {
            if (provider != null && !provider.isBlank()) {
                providers.add(provider);
            }
            if (rule != null && !rule.isBlank()) {
                rules.add(rule);
            }
        }

        private Set<String> providers() {
            return providers;
        }

        private Set<String> rules() {
            return rules;
        }
    }
}
