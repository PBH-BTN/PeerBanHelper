package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.module.IPBanRuleUpdateType;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "rule_sub_log")
public class RuleSubLogEntity {
    @DatabaseField(generatedId = true)
    private Long id;
    @DatabaseField
    private String ruleId;
    @DatabaseField
    private long updateTime;
    @DatabaseField
    private int count;
    @DatabaseField(dataType = DataType.ENUM_NAME)
    private IPBanRuleUpdateType updateType;
}
