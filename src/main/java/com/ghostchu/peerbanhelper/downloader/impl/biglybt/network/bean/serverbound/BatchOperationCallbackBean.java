package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.bean.serverbound;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BatchOperationCallbackBean {
    private int success;
    private int failed;
}
