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
@TableName("metadata")
public final class MetadataEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "k")
    private String k;
    @TableField(value = "v")
    private String v;

    public String getKey() {
        return k;
    }

    public String getValue() {
        return v;
    }

    public void setKey(String k) {
        this.k = k;
    }

    public void setValue(String v) {
        this.v = v;
    }
}
