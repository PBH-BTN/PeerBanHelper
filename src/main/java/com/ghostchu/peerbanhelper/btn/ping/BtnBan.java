package com.ghostchu.peerbanhelper.btn.ping;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class BtnBan {
    @SerializedName("btn_ban")
    private boolean btnBan;
    @SerializedName("ban_unique_id")
    private String banUniqueId;
    @SerializedName("module")
    private String module;
    @SerializedName("rule")
    private String rule;
    @SerializedName("peer")
    private BtnPeer peer;
}
