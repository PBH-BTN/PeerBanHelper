package com.ghostchu.peerbanhelper.btn.ping;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class BtnTrackedPeerPing {
    @SerializedName("tracked_peers")
    private List<BtnTrackedPeer> trackedPeers;

}
