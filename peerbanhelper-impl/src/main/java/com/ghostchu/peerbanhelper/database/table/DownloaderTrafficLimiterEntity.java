package com.ghostchu.peerbanhelper.database.table;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "downloader_traffic_limiter", daoClass = DownloaderTrafficLimiterEntity.class)
public final class DownloaderTrafficLimiterEntity {
    @DatabaseField(id = true, index = true, uniqueCombo = true)
    private String downloader;
    @DatabaseField
    private long uploadTraffic;
    @DatabaseField
    private long downloadTraffic;
    @DatabaseField
    private long operationTimestamp;
}
