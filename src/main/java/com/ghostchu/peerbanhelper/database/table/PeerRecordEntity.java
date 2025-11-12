package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "peer_records", daoClass = PeerRecordDao.class)
public final class PeerRecordEntity {
    @DatabaseField(generatedId = true)
    private Long id;
    @DatabaseField(canBeNull = false, index = true, uniqueCombo = true)
    private String address;
    @DatabaseField(canBeNull = false, index = true, uniqueCombo = true, defaultValue = "0")
    private int port;
    @DatabaseField(canBeNull = false, index = true, foreign = true, foreignAutoCreate = true, uniqueCombo = true, foreignAutoRefresh = true)
    private TorrentEntity torrent;
    @DatabaseField(canBeNull = false, uniqueCombo = true, index = true)
    private String downloader;
    @DatabaseField(index = true)
    private String peerId;
    @DatabaseField(index = true)
    private String clientName;
    @DatabaseField(canBeNull = false)
    private long uploaded;
    @DatabaseField(canBeNull = false)
    private long uploadedOffset;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long uploadSpeed;
    @DatabaseField(canBeNull = false)
    private long downloaded;
    @DatabaseField(canBeNull = false)
    private long downloadedOffset;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long downloadSpeed;
    @DatabaseField
    private String lastFlags;
    @DatabaseField(canBeNull = false, index = true)
    private Timestamp firstTimeSeen;
    @DatabaseField(canBeNull = false, index = true)
    private Timestamp lastTimeSeen;
}
