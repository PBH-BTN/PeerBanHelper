package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

@Component
public class BanList {
    private final Map<PeerAddress, BanMetadata> BAN_LIST = Collections.synchronizedMap(new HashMap<>());

    @Nullable
    public BanMetadata add(@NotNull PeerAddress address, @NotNull BanMetadata metadata){
        return BAN_LIST.put(address, metadata);
    }

    @Nullable
    public BanMetadata remove(@NotNull PeerAddress address){
        return BAN_LIST.remove(address);
    }

    public int size(){
        return BAN_LIST.size();
    }

    @NotNull
    public Map<PeerAddress, BanMetadata> copyBanList() {
        return Map.copyOf(BAN_LIST);
    }

    @NotNull
    public Map<PeerAddress, BanMetadata> directAccessBanList() {
        return BAN_LIST;
    }

    @NotNull
    public Set<PeerAddress> copyKeySet(){
        return Set.copyOf(BAN_LIST.keySet());
    }

    @NotNull
    public Set<PeerAddress> directAccessKeySet(){
        return BAN_LIST.keySet();
    }

    public boolean contains(@NotNull PeerAddress address){
        return BAN_LIST.containsKey(address);
    }

    @Nullable
    public BanMetadata get(@NotNull PeerAddress address){
        return BAN_LIST.get(address);
    }

    public void addAll(@NotNull Map<PeerAddress, BanMetadata> map){
        BAN_LIST.putAll(map);
    }

    public void forEach(@NotNull  BiConsumer<? super PeerAddress, ? super BanMetadata> action){
        BAN_LIST.forEach(action);
    }

    public void reset(){
        BAN_LIST.clear();
    }
}
