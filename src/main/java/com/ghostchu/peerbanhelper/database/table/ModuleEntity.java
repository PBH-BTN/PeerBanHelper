package com.ghostchu.peerbanhelper.database.table;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "modules")
public final class ModuleEntity {
    @DatabaseField(generatedId = true)
    private Long id;
    @DatabaseField(unique = true, index = true)
    private String name;
}
