package com.ghostchu.peerbanhelper.btn;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BtnRule {
    private String version;
    private Map<String, List<String>> peerIdRules;
    private Map<String, List<String>> clientNameRules;
    private Map<String, List<String>> ipRules;
    private Map<String, List<Integer>> portRules;
}
