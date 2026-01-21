package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.MetadataEntity;
import com.j256.ormlite.support.ConnectionSource;
import io.sentry.Sentry;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component

public final class MetadataDao extends AbstractPBHDao<MetadataEntity, String> {
    public MetadataDao(@Autowired ConnectionSource database) throws SQLException {
        super(database, MetadataEntity.class);
    }

    @Nullable
    public String get(String key) {
        return getOrDefault(key, null);
    }

    public String getOrDefault(String key, @Nullable String defaultValue) {
        try {
            MetadataEntity entity = queryForId(key);
            if (entity != null) {
                return entity.getValue();
            } else {
                return defaultValue;
            }
        } catch (SQLException e) {
            Sentry.captureException(e);
            throw new RuntimeException(e);
        }
    }

    public int set(String key, @Nullable String value) {
        try {
            if (idExists(key)) {
                if (value == null) {
                    return deleteById(key);
                } else {
                    MetadataEntity entity = new MetadataEntity();
                    entity.setKey(key);
                    entity.setValue(value);
                    return update(entity);
                }
            } else {
                MetadataEntity entity = new MetadataEntity();
                entity.setKey(key);
                entity.setValue(value);
                return create(entity);
            }
        } catch (SQLException e) {
            Sentry.captureException(e);
            throw new RuntimeException(e);
        }
    }
}
