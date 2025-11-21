package com.ghostchu.peerbanhelper.btn.ping.legacy;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class LegacyBtnBan {
    @SerializedName("btn_ban")
    private boolean btnBan;
    @SerializedName("ban_unique_id")
    private String banUniqueId;
    @SerializedName("module")
    private String module;
    @SerializedName("rule")
    private String rule;
    @SerializedName("peer")
    private LegacyBtnPeer peer;
    @SerializedName("structured_data")
    private String structuredData;
}