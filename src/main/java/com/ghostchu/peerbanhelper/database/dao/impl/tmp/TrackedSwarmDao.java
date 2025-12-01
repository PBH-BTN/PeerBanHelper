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

    public int upsert(TrackedSwarmEntity newData) throws SQLException {
        TrackedSwarmEntity lastData = queryBuilder()
                .where()
                .eq("ip", newData.getIp())
                .and()
                .eq("port", newData.getPort())
                .and()
                .eq("infoHash", newData.getInfoHash())
                .and()
                .eq("downloader", newData.getDownloader()).queryForFirst();
        if (lastData != null) {
            long newDownloaded;
            long newUploaded;
            if (newData.getDownloadedOffset() < lastData.getDownloadedOffset()
                    || newData.getUploadedOffset() < lastData.getUploadedOffset()) {
                newDownloaded = newData.getDownloadedOffset() - lastData.getDownloadedOffset();
                newUploaded = newData.getUploadedOffset() - lastData.getUploadedOffset();
            }else{
                newDownloaded = newData.getDownloadedOffset();
                newUploaded = newData.getUploadedOffset();
            }
            lastData.setDownloaded(lastData.getDownloaded() + newDownloaded);
            lastData.setUploaded(lastData.getUploaded() + newUploaded);
            lastData.setDownloadedOffset(newData.getDownloadedOffset());
            lastData.setUploadedOffset(newData.getUploadedOffset());
            lastData.setClientName(newData.getClientName());
            lastData.setPeerId(newData.getPeerId());
            lastData.setLastFlags(newData.getLastFlags());
            lastData.setLastTimeSeen(newData.getLastTimeSeen());
            return update(lastData);
        } else {
            newData.setUploaded(newData.getUploaded());
            newData.setDownloaded(newData.getDownloaded());
            return create(newData);
        }
    }
}
