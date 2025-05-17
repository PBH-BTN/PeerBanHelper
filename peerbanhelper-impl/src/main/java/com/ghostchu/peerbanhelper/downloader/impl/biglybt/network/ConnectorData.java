package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class ConnectorData {
    private String software;
    private String version;
    private String abbrev;
}
