package com.ghostchu.peerbanhelper.database.dao.impl.tmp;

import com.ghostchu.peerbanhelper.database.dao.AbstractPBHDao;
import com.ghostchu.peerbanhelper.database.table.tmp.TrackedSwarmEntity;
import com.ghostchu.peerbanhelper.util.query.Page;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.j256.ormlite.support.ConnectionSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component
public final class TrackedSwarmDao extends AbstractPBHDao<TrackedSwarmEntity, Long> {


    public TrackedSwarmDao(@Autowired ConnectionSource source) throws SQLException {
        super(source, TrackedSwarmEntity.class);
    }

    public Page<TrackedSwarmEntity> getPendingSubmitTrackedPeers(Pageable pageable, long idAfterThan) throws SQLException {
        var queryBuilder = queryBuilder().where()
                .gt("id", idAfterThan)
                .queryBuilder()
                .orderBy("id", true);
        return queryByPaging(queryBuilder, pageable);
    }

    public int upsert(TrackedSwarmEntity entity) throws SQLException {
        TrackedSwarmEntity inDatabase = queryBuilder()
                .where()
                .eq("ip", entity.getIp())
                .and()
                .eq("port", entity.getPort())
                .and()
                .eq("infoHash", entity.getInfoHash())
                .and()
                .eq("downloader", entity.getDownloader()).queryForFirst();
        if (inDatabase != null) {
            if (inDatabase.getDownloadedOffset() < entity.getDownloadedOffset() ||
                    inDatabase.getUploadedOffset() < entity.getUploadedOffset()) {
                // 如果数据库中的偏移量小于当前提交的偏移量，则更新偏移量
                inDatabase.setDownloaded(inDatabase.getDownloaded() + entity.getDownloadedOffset());
                inDatabase.setUploaded(inDatabase.getUploaded() + entity.getUploadedOffset());
            } else {
                inDatabase.setDownloadedOffset(entity.getDownloadedOffset());
                inDatabase.setUploadedOffset(entity.getUploadedOffset());
            }
            inDatabase.setClientName(entity.getClientName());
            inDatabase.setPeerId(entity.getPeerId());
            inDatabase.setLastFlags(entity.getLastFlags());
            inDatabase.setLastTimeSeen(entity.getLastTimeSeen());
            return update(entity);
        } else {
            return create(entity);
        }
    }
}
