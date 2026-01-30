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
@TableName(value = "banlist", autoResultMap = true)
public final class BanListEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "address")
    private String address;
    @TableField(value = "metadata")
    private String metadata;
}
