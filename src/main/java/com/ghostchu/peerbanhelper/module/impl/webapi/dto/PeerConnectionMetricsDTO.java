package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.database.table.PeerConnectionMetricsEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PeerConnectionMetricsDTO {
    private long key;
    private long totalConnections;
    private long incomingConnections;
    private long remoteRefuseTransferToClient;
    private long remoteAcceptTransferToClient;
    private long localRefuseTransferToPeer;
    private long localAcceptTransferToPeer;
    private long localNotInterested;
    private long questionStatus;
    private long optimisticUnchoke;
    private long fromDHT;
    private long fromPEX;
    private long fromLSD;
    private long fromTrackerOrOther;
    private long rc4Encrypted;
    private long plainTextEncrypted;
    private long utpSocket;
    private long tcpSocket;

    public static PeerConnectionMetricsDTO from(@NotNull PeerConnectionMetricsEntity peerConnectionMetricsEntity){
        return new PeerConnectionMetricsDTO(
                peerConnectionMetricsEntity.getTimeframeAt().getTime(),
                peerConnectionMetricsEntity.getTotalConnections(),
                peerConnectionMetricsEntity.getIncomingConnections(),
                peerConnectionMetricsEntity.getRemoteRefuseTransferToClient(),
                peerConnectionMetricsEntity.getRemoteAcceptTransferToClient(),
                peerConnectionMetricsEntity.getLocalRefuseTransferToPeer(),
                peerConnectionMetricsEntity.getLocalAcceptTransferToPeer(),
                peerConnectionMetricsEntity.getLocalNotInterested(),
                peerConnectionMetricsEntity.getQuestionStatus(),
                peerConnectionMetricsEntity.getOptimisticUnchoke(),
                peerConnectionMetricsEntity.getFromDHT(),
                peerConnectionMetricsEntity.getFromPEX(),
                peerConnectionMetricsEntity.getFromLSD(),
                peerConnectionMetricsEntity.getFromTrackerOrOther(),
                peerConnectionMetricsEntity.getRc4Encrypted(),
                peerConnectionMetricsEntity.getPlainTextEncrypted(),
                peerConnectionMetricsEntity.getUtpSocket(),
                peerConnectionMetricsEntity.getTcpSocket()
        );
    }
}
