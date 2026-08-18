package com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class A2SetBtPeerBlocklist {

    /**
     * disconnectedPeers
     */
    @JsonProperty("disconnectedPeers")
    private Integer disconnectedPeers;
    /**
     * removedPeers
     */
    @JsonProperty("removedPeers")
    private Integer removedPeers;
    /**
     * revision
     */
    @JsonProperty("revision")
    private Integer revision;
    /**
     * ruleCount
     */
    @JsonProperty("ruleCount")
    private Integer ruleCount;
}
