package com.ghostchu.peerbanhelper.btn;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnRule {
    @SerializedName("version")
    private String version;
    @SerializedName("peer_id")
    private Map<String, List<String>> peerIdRules;
    @SerializedName("peer_id_exclude")
    private Map<String, List<String>> excludePeerIdRules;
    @SerializedName("client_name")
    private Map<String, List<String>> clientNameRules;
    @SerializedName("client_name_exclude")
    private Map<String, List<String>> excludeClientNameRules;
    @SerializedName("ip")
    private Map<String, List<String>> ipRules;
    @SerializedName("port")
    private Map<String, List<Integer>> portRules;
}
