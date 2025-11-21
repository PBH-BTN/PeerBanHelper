package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.dao.impl.PCBAddressDao;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "pcb_address", daoClass = PCBAddressDao.class)
public final class PCBAddressEntity {
    @DatabaseField(generatedId = true)
    private Long id;
    @DatabaseField(canBeNull = false, uniqueCombo = true, index = true)
    private String ip;
    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private int port;
    @DatabaseField(canBeNull = false, uniqueCombo = true, index = true)
    private String torrentId;
    @DatabaseField(canBeNull = false)
    private double lastReportProgress;
    @DatabaseField
    private long lastReportUploaded;
    @DatabaseField
    private long trackingUploadedIncreaseTotal;
    @DatabaseField(canBeNull = false)
    private int rewindCounter;
    @DatabaseField(canBeNull = false)
    private int progressDifferenceCounter;
    @DatabaseField(canBeNull = false, index = true)
    private Timestamp firstTimeSeen;
    @DatabaseField(canBeNull = false, index = true)
    private Timestamp lastTimeSeen;
    @DatabaseField(canBeNull = false, uniqueCombo = true, index = true)
    private String downloader;
    @DatabaseField(canBeNull = false)
    private Timestamp banDelayWindowEndAt;
    @DatabaseField(canBeNull = false)
    private long fastPcbTestExecuteAt;
    @DatabaseField(canBeNull = false)
    private long lastTorrentCompletedSize;
}
