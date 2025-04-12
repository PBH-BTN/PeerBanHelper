package com.ghostchu.peerbanhelper.btn.ping;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class BtnPeerHistoryPing {
    @SerializedName("peers")
    private List<BtnPeerHistory> peers;

}
