package com.ghostchu.peerbanhelper.database.table;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "peer_records")
public final class PeerRecordEntity {
    @DatabaseField(generatedId = true)
    private Long id;
    @DatabaseField(canBeNull = false, index = true, uniqueCombo = true)
    private String address;
    @DatabaseField(canBeNull = false, index = true, foreign = true, foreignAutoCreate = true, uniqueCombo = true)
    private TorrentEntity torrent;
    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private String downloader;
    @DatabaseField
    private String peerId;
    @DatabaseField
    private String clientName;
    @DatabaseField(canBeNull = false)
    private long uploaded;
    @DatabaseField(canBeNull = false)
    private long uploadedOffset;
    @DatabaseField(canBeNull = false)
    private long downloaded;
    @DatabaseField(canBeNull = false)
    private long downloadedOffset;
    @DatabaseField
    private String lastFlags;
    @DatabaseField(canBeNull = false)
    private Timestamp firstTimeSeen;
    @DatabaseField(canBeNull = false)
    private Timestamp lastTimeSeen;

}
