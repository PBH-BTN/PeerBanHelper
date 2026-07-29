package com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class A2SetBtPeerBlocklist {
    @JsonProperty("revision")
    private Integer revision;
    @JsonProperty("ruleCount")
    private Integer ruleCount;
}
