package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.dao.impl.PeerNameRuleSubInfoDao;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "peer_name_rule_sub_info", daoClass = PeerNameRuleSubInfoDao.class)
public final class PeerNameRuleSubInfoEntity {
    @DatabaseField(id = true, index = true)
    private String ruleId;
    @DatabaseField
    private boolean enabled;
    @DatabaseField
    private String ruleName;
    @DatabaseField
    private String subUrl;
    @DatabaseField
    private long lastUpdate;
    @DatabaseField
    private int entCount;
}
