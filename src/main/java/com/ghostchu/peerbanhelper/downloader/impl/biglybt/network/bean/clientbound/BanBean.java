package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.clientbound;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BanBean {
    private List<String> ips;
}
