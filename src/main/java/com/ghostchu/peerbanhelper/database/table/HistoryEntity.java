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
@DatabaseTable(tableName = "history")
public final class HistoryEntity {
    @DatabaseField(generatedId = true)
    private Long id;
    @DatabaseField(canBeNull = false)
    private Timestamp banAt;
    @DatabaseField(canBeNull = false)
    private Timestamp unbanAt;
    @DatabaseField(canBeNull = false)
    private String ip;
    @DatabaseField(canBeNull = false)
    private Integer port;
    @DatabaseField(canBeNull = false, foreign = true, foreignAutoCreate = true, foreignAutoRefresh = true)
    private PeerIdentityEntity peerIdentity;
    @DatabaseField
    private Long peerUploaded;
    @DatabaseField
    private Long peerDownloaded;
    @DatabaseField(canBeNull = false)
    private Double peerProgress;
    @DatabaseField(canBeNull = false, foreign = true, foreignAutoCreate = true, foreignAutoRefresh = true)
    private TorrentEntity torrent;
    @DatabaseField(canBeNull = false, foreign = true, foreignAutoCreate = true, foreignAutoRefresh = true)
    private RuleEntity rule;
    @DatabaseField(canBeNull = false)
    private String description;
    @DatabaseField
    private String flags;
}
