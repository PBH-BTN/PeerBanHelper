package com.ghostchu.peerbanhelper.database.table;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "dht_records")
public final class DHTRecordEntity {
    @DatabaseField(id = true, index = true, canBeNull = false, columnDefinition = "VARCHAR(100)")
    private String peerId;
    @DatabaseField(canBeNull = false, dataType = DataType.BYTE_ARRAY)
    private byte[] raw;
    @DatabaseField(canBeNull = false)
    private long sequence;
    @DatabaseField(canBeNull = false)
    private long ttlNanos;
    @DatabaseField(canBeNull = false)
    private long expiryUTC;
    @DatabaseField(canBeNull = false, columnDefinition = "VARCHAR(10240)")
    private String val;
}
