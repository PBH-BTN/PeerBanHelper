package com.ghostchu.peerbanhelper.database.table;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "traffic_journey")
public final class TrafficJournalEntity {
    @DatabaseField(id = true, unique = true, index = true)
    private Long timestamp;
    @DatabaseField
    private long uploaded;
    @DatabaseField
    private long downloaded;
}
