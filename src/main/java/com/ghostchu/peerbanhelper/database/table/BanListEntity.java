package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "banlist")
public class BanListEntity {
    @DatabaseField(generatedId = true)
    private Long id;
    @DatabaseField(canBeNull = false, dataType = DataType.SERIALIZABLE)
    private PeerAddress address;
    @DatabaseField(canBeNull = false, dataType = DataType.SERIALIZABLE)
    private BanMetadata metadata;
}
