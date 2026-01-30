package com.ghostchu.peerbanhelper.databasent.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@TableName(value = "rule_sub_info", autoResultMap = true)
public final class RuleSubInfoEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "rule_id", type = IdType.INPUT)
    private String ruleId;
    @TableField(value = "enabled")
    private boolean enabled;
    @TableField(value = "rule_name")
    private String ruleName;
    @TableField(value = "sub_url")
    private String subUrl;
    @TableField(value = "last_update")
    private OffsetDateTime lastUpdate;
    @TableField(value = "ent_count")
    private int entCount;
}
