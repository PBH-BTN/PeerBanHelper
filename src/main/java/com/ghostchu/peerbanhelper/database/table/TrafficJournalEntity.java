package com.ghostchu.peerbanhelper.database.table;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "traffic_journal_v2")
public final class TrafficJournalEntity {
    @DatabaseField(id = true, index = true, uniqueCombo = true)
    private Long timestamp;
    @DatabaseField(uniqueCombo = true)
    private String downloader;
    @DatabaseField
    private long dataOverallUploaded;
    @DatabaseField
    private long dataOverallDownloaded;
    @DatabaseField
    private long protocolOverallUploaded;
    @DatabaseField
    private long protocolOverallDownloaded;
}
