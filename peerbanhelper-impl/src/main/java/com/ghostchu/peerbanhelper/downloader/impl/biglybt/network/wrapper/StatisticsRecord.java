package com.ghostchu.peerbanhelper.downloader.impl.biglybt.network.wrapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor
public final class StatisticsRecord {
    private Long overallDataBytesReceived;
    private Long overallDataBytesSent;
    private Long sessionUptimeSeconds;
    private Integer dataReceiveRate;
    private Integer protocolReceiveRate;
    private Integer dataAndProtocolReceiveRate;
    private Integer dataSendRate;
    private Integer protocolSendRate;
    private Integer dataAndProtocolSendRate;
    private Long dataBytesReceived;
    private Long protocolBytesReceived;
    private Long dataBytesSent;
    private Long protocolBytesSent;
    private Long smoothedReceiveRate;
    private Long smoothedSendRate;
}
