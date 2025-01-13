package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
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
@DatabaseTable(tableName = "alert")
public final class AlertEntity {
    @DatabaseField(generatedId = true)
    private Long id;
    @DatabaseField(canBeNull = false, index = true)
    private Timestamp createAt;
    @DatabaseField(index = true)
    private Timestamp readAt;
    @DatabaseField(canBeNull = false, index = true)
    private AlertLevel level;
    @DatabaseField(canBeNull = false, index = true)
    private String identifier;
    @DatabaseField(canBeNull = false, persisterClass = TranslationComponentPersistener.class)
    private TranslationComponent title;
    @DatabaseField(canBeNull = false, persisterClass = TranslationComponentPersistener.class)
    private TranslationComponent content;
}