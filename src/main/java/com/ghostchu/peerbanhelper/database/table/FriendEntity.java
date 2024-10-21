package com.ghostchu.peerbanhelper.database.table;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "friend")
public final class FriendEntity {
    @DatabaseField(id = true, index = true)
    private String peerId;
    @DatabaseField(dataType = DataType.BYTE_ARRAY)
    private byte[] pubKey;
    @DatabaseField(canBeNull = false)
    private Timestamp lastAttemptConnectTime;
    @DatabaseField(canBeNull = false)
    private Timestamp lastCommunicationTime;
    @DatabaseField(canBeNull = false)
    private String lastRecordedPBHVersion;
    @DatabaseField(canBeNull = false)
    private String lastRecordedConnectionStatus;
}
