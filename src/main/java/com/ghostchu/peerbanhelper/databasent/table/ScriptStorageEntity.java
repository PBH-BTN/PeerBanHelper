package com.ghostchu.peerbanhelper.databasent.table;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("script_storage")
public final class ScriptStorageEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "key")
    private String key;
    @TableField(value = "value")
    private String value;
}
