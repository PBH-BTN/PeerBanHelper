package com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class A2SetBtPeerBlocklist {
    @JsonProperty("id")
    private String id;
    @JsonProperty("jsonrpc")
    private String jsonrpc;
    @JsonProperty("result")
    private ResultDTO result;

    @NoArgsConstructor
    @Data
    public static class ResultDTO {
        @JsonProperty("revision")
        private Integer revision;
        @JsonProperty("ruleCount")
        private Integer ruleCount;
    }
}
