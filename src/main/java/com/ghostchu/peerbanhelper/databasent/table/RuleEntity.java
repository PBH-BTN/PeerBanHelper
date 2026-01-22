package com.ghostchu.peerbanhelper.databasent.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ghostchu.peerbanhelper.databasent.converter.TranslationComponentTypeHandler;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@TableName("rules")
public final class RuleEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField(value = "module_id")
    private Long moduleId;
    @TableField(value = "rule", typeHandler = TranslationComponentTypeHandler.class)
    private TranslationComponent rule;
}
