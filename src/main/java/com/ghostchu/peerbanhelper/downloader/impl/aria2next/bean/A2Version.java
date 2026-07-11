package com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class A2Version {
    private String version;
    private List<String> enabledFeatures;
}
