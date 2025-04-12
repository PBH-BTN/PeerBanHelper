package com.ghostchu.peerbanhelper.database.table.tmp;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "tmp_tracked_peers")
public final class TrackedPeerEntity { // 需要创建为临时表
    @DatabaseField(generatedId = true, index = true)
    private Long id;
    @DatabaseField(canBeNull = false, index = true, uniqueCombo = true)
    private String ip;
    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private int port;
    @DatabaseField(canBeNull = false, index = true, uniqueCombo = true)
    private String infoHash;
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
