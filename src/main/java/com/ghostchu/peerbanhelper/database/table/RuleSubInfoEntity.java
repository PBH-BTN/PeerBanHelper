package com.ghostchu.peerbanhelper.database.table;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "rule_sub_info")
public final class RuleSubInfoEntity {
    @DatabaseField(id = true, index = true)
    private String ruleId;
    @DatabaseField
    private boolean enabled;
    @DatabaseField()
    private String ruleName;
    @DatabaseField
    private String subUrl;
    @DatabaseField
    private long lastUpdate;
    @DatabaseField
    private int entCount;
}
