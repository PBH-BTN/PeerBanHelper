package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.dao.impl.MetadataDao;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "metadata", daoClass = MetadataDao.class)
public final class MetadataEntity {
    @DatabaseField(id = true, index = true)
    private String key;
    @DatabaseField
    private String value;
}
