package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.dao.impl.NicTrafficJournalDao;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "nic_traffic_journal", daoClass = NicTrafficJournalDao.class)
public final class NicTrafficJournalEntity {
    @DatabaseField(generatedId = true, index = true)
    private Long id;
    @DatabaseField(index = true, uniqueCombo = true)
    private Long timestamp;
    @DatabaseField(index = true, uniqueCombo = true)
    private String nic;
    @DatabaseField
    private long bytesReceivedAtStart;
    @DatabaseField
    private long bytesReceived;
    @DatabaseField
    private long bytesSentAtStart;
    @DatabaseField
    private long bytesSent;
    @DatabaseField
    private long packetsReceivedAtStart;
    @DatabaseField
    private long packetsReceived;
    @DatabaseField
    private long packetsSentAtStart;
    @DatabaseField
    private long packetsSent;
}
