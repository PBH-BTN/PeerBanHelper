package com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class A2Version {

    /**
     * enabledFeatures
     */
    @JsonProperty("enabledFeatures")
    private List<String> enabledFeatures;
    /**
     * product
     */
    @JsonProperty("product")
    private String product;
    /**
     * rpcVersion
     */
    @JsonProperty("rpcVersion")
    private String rpcVersion;
    /**
     * version
     */
    @JsonProperty("version")
    private String version;
}
