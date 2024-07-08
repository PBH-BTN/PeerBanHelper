package com.ghostchu.peerbanhelper.database.table;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "peer_identity")
public class PeerIdentityEntity {
    @DatabaseField(generatedId = true)
    private Long id;
    @DatabaseField(uniqueCombo = true)
    private String peerId;
    @DatabaseField(uniqueCombo = true)
    private String clientName;
}
