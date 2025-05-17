package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.TranslationComponentPersistener;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "history", daoClass = HistoryEntity.class)
public final class HistoryEntity {
    @DatabaseField(generatedId = true, index = true)
    private Long id;
    @DatabaseField(canBeNull = false, index = true)
    private Timestamp banAt;
    @DatabaseField(canBeNull = false)
    private Timestamp unbanAt;
    @DatabaseField(canBeNull = false, index = true)
    private String ip;
    @DatabaseField(canBeNull = false)
    private Integer port;
    @DatabaseField
    private String peerId;
    @DatabaseField
    private String peerClientName;
    @DatabaseField
    private Long peerUploaded;
    @DatabaseField
    private Long peerDownloaded;
    @DatabaseField(canBeNull = false)
    private Double peerProgress;
    @DatabaseField(canBeNull = false)
    private Double downloaderProgress;
    @DatabaseField(canBeNull = false, foreign = true, foreignAutoCreate = true, foreignAutoRefresh = true)
    private TorrentEntity torrent;
    @DatabaseField(canBeNull = false, foreign = true, foreignAutoCreate = true, foreignAutoRefresh = true)
    private RuleEntity rule;
    @DatabaseField(canBeNull = false, persisterClass = TranslationComponentPersistener.class)
    private TranslationComponent description;
    @DatabaseField
    private String flags;
    @DatabaseField(canBeNull = false)
    private String downloader;
}
