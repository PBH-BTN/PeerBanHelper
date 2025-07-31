package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.dao.impl.EnhancedRuleSubLogDao;
import com.ghostchu.peerbanhelper.module.IPBanRuleUpdateType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "enhanced_rule_sub_log", daoClass = EnhancedRuleSubLogDao.class)
public final class EnhancedRuleSubLogEntity {
    @DatabaseField(generatedId = true)
    private Long id;
    
    @DatabaseField(index = true)
    private String ruleId;
    
    @DatabaseField
    private long updateTime;
    
    @DatabaseField
    private int count;
    
    @DatabaseField
    private IPBanRuleUpdateType updateType;
    
    @DatabaseField
    private String ruleType; // stores RuleType.getCode()
    
    @DatabaseField(canBeNull = true)
    private String errorMessage; // optional error message if update failed
}