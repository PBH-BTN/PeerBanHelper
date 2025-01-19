package com.ghostchu.peerbanhelper.database.table;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "torrents")
public final class TorrentEntity {
    @DatabaseField(generatedId = true, index = true)
    private Long id;
    @DatabaseField(canBeNull = false, uniqueIndex = true)
    private String infoHash;
    @DatabaseField(canBeNull = false)
    private String name;
    @DatabaseField(canBeNull = false)
    private Long size;
    @Nullable
    @DatabaseField()
    private Boolean privateTorrent;
}
