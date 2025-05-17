package com.ghostchu.peerbanhelper.database.table;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "traffic_journal_v3", daoClass = TrafficJournalEntity.class)
public final class TrafficJournalEntity {
    @DatabaseField(generatedId = true, index = true)
    private Long id;
    @DatabaseField(index = true, uniqueCombo = true)
    private Long timestamp;
    @DatabaseField(index = true, uniqueCombo = true)
    private String downloader;
    @DatabaseField
    private long dataOverallUploadedAtStart;
    @DatabaseField
    private long dataOverallUploaded;
    @DatabaseField
    private long dataOverallDownloadedAtStart;
    @DatabaseField
    private long dataOverallDownloaded;
    @DatabaseField
    private long protocolOverallUploadedAtStart;
    @DatabaseField
    private long protocolOverallUploaded;
    @DatabaseField
    private long protocolOverallDownloadedAtStart;
    @DatabaseField
    private long protocolOverallDownloaded;
}
