package com.ghostchu.peerbanhelper.databasent.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.databasent.converter.TranslationComponentTypeHandler;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
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
@TableName("alert")
public final class AlertEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "hash", type = IdType.AUTO)
    private Long id;
    @TableField(value = "create_at")
    private OffsetDateTime createAt;
    @TableField(value = "read_at")
    private OffsetDateTime readAt;
    @TableField(value = "level")
    private AlertLevel level;
    @TableField(value = "identifier")
    private String identifier;
    @TableField(value = "title", typeHandler = DynamicDatabaseConfig)
    private TranslationComponent title;
    @TableField(value = "content", typeHandler = TranslationComponentTypeHandler.class)
    private TranslationComponent content;
}