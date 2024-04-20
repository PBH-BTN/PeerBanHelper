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
    @SerializedName("peer_id_rules")
    private Map<String, List<String>> peerIdRules;
    @SerializedName("client_name_rules")
    private Map<String, List<String>> clientNameRules;
    @SerializedName("ip_rules")
    private Map<String, List<String>> ipRules;
    @SerializedName("port_rules")
    private Map<String, List<Integer>> portRules;
}
