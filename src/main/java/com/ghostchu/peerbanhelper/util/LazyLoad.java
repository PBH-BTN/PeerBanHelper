package com.ghostchu.peerbanhelper.util;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;
public class LazyLoad<T> {
    @Nullable
    private Supplier<T> loader;
    @Nullable
    private T content;

    public LazyLoad(@Nullable Supplier<T> loader) {
        this.loader = loader;
    }

    @Nullable
    public T get() {
        if (loader == null) {
            return content;
        }
        this.content = loader.get();
        this.loader = null;
        return content;
    }

    @Override
    public String toString() {
        T dat = get();
        return dat == null ? "null" : dat.toString();
    }
}
