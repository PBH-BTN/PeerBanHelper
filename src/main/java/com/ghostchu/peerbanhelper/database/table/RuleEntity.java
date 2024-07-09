package com.ghostchu.peerbanhelper.database.table;

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
    @DatabaseField(generatedId = true)
    private Long id;
    @DatabaseField(canBeNull = false, foreign = true, foreignAutoCreate = true, foreignAutoRefresh = true, uniqueCombo = true)
    private ModuleEntity module;
    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private String rule;
}
