package com.ghostchu.peerbanhelper.btn.ping;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class BtnPeerPing {
    @SerializedName("populate_time")
    private long populateTime;
    @SerializedName("peers")
    private List<BtnPeer> peers;

}
