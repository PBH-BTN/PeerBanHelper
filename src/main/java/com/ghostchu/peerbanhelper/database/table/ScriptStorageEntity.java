package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.dao.impl.ScriptStorageDao;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "script_storage", daoClass = ScriptStorageDao.class)
public final class ScriptStorageEntity {
    @DatabaseField(id = true)
    private String key;
    @DatabaseField()
    private String value;
}
