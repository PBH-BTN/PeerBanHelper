package com.ghostchu.peerbanhelper.databasent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ghostchu.peerbanhelper.databasent.table.MetadataEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MetadataService extends IService<MetadataEntity> {
    @Nullable String get(@NotNull String key);

    @Nullable String getOrDefault(@NotNull String key, @Nullable String defaultValue);

    boolean set(@NotNull String key, @Nullable String value);
}
