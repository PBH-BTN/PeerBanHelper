package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.dao.impl.EnhancedRuleSubInfoDao;
import com.ghostchu.peerbanhelper.module.impl.rule.subscription.RuleType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "enhanced_rule_sub_info", daoClass = EnhancedRuleSubInfoDao.class)
public final class EnhancedRuleSubInfoEntity {
    @DatabaseField(id = true, index = true)
    private String ruleId;
    
    @DatabaseField
    private boolean enabled;
    
    @DatabaseField()
    private String ruleName;
    
    @DatabaseField
    private String subUrl;
    
    @DatabaseField
    private String ruleType; // stores RuleType.getCode()
    
    @DatabaseField
    private long lastUpdate;
    
    @DatabaseField
    private int entCount;
    
    @DatabaseField
    private String description; // optional description for the rule
    
    /**
     * Get rule type as enum
     */
    public RuleType getRuleTypeEnum() {
        return RuleType.fromCode(ruleType);
    }
    
    /**
     * Set rule type from enum
     */
    public void setRuleTypeEnum(RuleType ruleType) {
        this.ruleType = ruleType.getCode();
    }
}