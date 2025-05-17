package com.ghostchu.peerbanhelper.database.dao.impl;

import com.ghostchu.peerbanhelper.database.Database;
import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.DownloaderTrafficLimiterEntity;
import com.ghostchu.peerbanhelper.util.lab.Laboratory;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
@Slf4j
public final class DownloaderTrafficLimiterDao extends AbstractPBHDao<DownloaderTrafficLimiterEntity, String> {

    public DownloaderTrafficLimiterDao(@Autowired Database database, @Autowired Laboratory laboratory) throws SQLException {
        super(database.getDataSource(), DownloaderTrafficLimiterEntity.class);
    }

    @Nullable
    public DownloaderTrafficLimiterEntity getDownloaderTrafficLimiterData(String downloaderId) {
        try {
            return queryBuilder().where().idEq(downloaderId).queryForFirst();
        } catch (SQLException e) {
            log.error("Unable retrieve downloader {} last record for traffic limiter data from database", downloaderId, e);
            return null;
        }
    }

    public void setDownloaderTrafficLimiterData(String downloader, long download, long upload, long timestamp) {
        try {
            var entity = queryBuilder().where().idEq(downloader).queryForFirst();
            if (entity == null) {
                entity = new DownloaderTrafficLimiterEntity();
                entity.setDownloader(downloader);
            }
            entity.setDownloadTraffic(download);
            entity.setUploadTraffic(upload);
            entity.setOperationTimestamp(timestamp);
            createOrUpdate(entity);
        } catch (SQLException e) {
            log.error("Unable upsert downloader {} last record for traffic limiter data from database", downloader, e);
        }
    }

    public void removeDownloaderTrafficLimiterData(String downloader) {
        try {
            deleteById(downloader);
        } catch (SQLException e) {
            log.error("Unable remove downloader traffic limiter for {}", downloader, e);
        }
    }
}
