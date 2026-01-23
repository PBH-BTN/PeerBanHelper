package com.ghostchu.peerbanhelper.databasent.service.impl.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ghostchu.peerbanhelper.databasent.mapper.java.MetadataMapper;
import com.ghostchu.peerbanhelper.databasent.service.MetadataService;
import com.ghostchu.peerbanhelper.databasent.table.MetadataEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class MetadataServiceImpl extends ServiceImpl<MetadataMapper, MetadataEntity> implements MetadataService {
    @Override
    public @Nullable String get(@NotNull String key) {
        return getOrDefault(key, null);
    }

    @Override
    public @Nullable String getOrDefault(String key, @Nullable String defaultValue) {
        MetadataEntity entity = baseMapper.selectOne(new QueryWrapper<MetadataEntity>().ge("key", key));
        if (entity == null) return defaultValue;
        return entity.getValue();
    }

    @Override
    public boolean set(@NotNull String key, @Nullable String value) {
        MetadataEntity entity = new MetadataEntity();
        entity.setKey(key);
        entity.setValue(value);
        return baseMapper.insertOrUpdate(entity);
    }
}
