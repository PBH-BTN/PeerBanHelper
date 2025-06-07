package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.dao.impl.BanListDao;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "banlist", daoClass = BanListDao.class)
public final class BanListEntity {
    @DatabaseField(id = true, index = true)
    private String address;
    @DatabaseField(canBeNull = false)
    private String metadata;
}
