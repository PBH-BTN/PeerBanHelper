package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.TranslationComponentPersistener;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "rules")
public final class RuleEntity {
    @DatabaseField(generatedId = true, index = true)
    private Long id;
    @DatabaseField(canBeNull = false, foreign = true, foreignAutoCreate = true, foreignAutoRefresh = true, uniqueCombo = true)
    private ModuleEntity module;
    @DatabaseField(canBeNull = false, uniqueCombo = true, persisterClass = TranslationComponentPersistener.class)
    private TranslationComponent rule;
}
