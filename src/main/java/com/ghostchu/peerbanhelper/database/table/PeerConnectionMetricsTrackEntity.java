package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.dao.impl.PeerConnectionMetricsTrackDao;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "peer_connection_metrics_track", daoClass = PeerConnectionMetricsTrackDao.class)
public final class PeerConnectionMetricsTrackEntity {
    @DatabaseField(generatedId = true)
    private Long id;
    @DatabaseField(canBeNull = false, index = true, uniqueCombo = true)
    private Timestamp timeframeAt;
    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private String downloader;
    @DatabaseField(canBeNull = false, index = true, foreign = true, foreignAutoCreate = true, uniqueCombo = true, foreignAutoRefresh = true)
    private TorrentEntity torrent;
    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private String address;
    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private int port;
    @DatabaseField
    private String peerId;
    @DatabaseField
    private String clientName;
    @DatabaseField
    private String lastFlags;

}
