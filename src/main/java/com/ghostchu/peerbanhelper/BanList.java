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
    private final Map<PeerAddress, BanMetadata> delegate = Collections.synchronizedMap(new HashMap<>());

    @Nullable
    public BanMetadata add(@NotNull PeerAddress address, @NotNull BanMetadata metadata){
        return delegate.put(address, metadata);
    }

    @Nullable
    public BanMetadata remove(@NotNull PeerAddress address){
        return delegate.remove(address);
    }

    public int size(){
        return delegate.size();
    }

    @NotNull
    public Map<PeerAddress, BanMetadata> copy() {
        return Map.copyOf(delegate);
    }

    @NotNull
    public Map<PeerAddress, BanMetadata> directAccess() {
        return delegate;
    }

    @NotNull
    public Set<PeerAddress> copyKeySet(){
        return Set.copyOf(delegate.keySet());
    }

    @NotNull
    public Set<PeerAddress> directAccessKeySet(){
        return delegate.keySet();
    }

    public boolean contains(@NotNull PeerAddress address){
        return delegate.containsKey(address);
    }

    @Nullable
    public BanMetadata get(@NotNull PeerAddress address){
        return delegate.get(address);
    }

    public void addAll(@NotNull Map<PeerAddress, BanMetadata> map){
        delegate.putAll(map);
    }

    public void forEach(@NotNull  BiConsumer<? super PeerAddress, ? super BanMetadata> action){
        delegate.forEach(action);
    }

    public void reset(){
        delegate.clear();
    }
}
