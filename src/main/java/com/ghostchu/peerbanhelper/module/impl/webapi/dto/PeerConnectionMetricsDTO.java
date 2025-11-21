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
    private long totalConnections; // 总 IP 数量
    private long incomingConnections; // 传输 IP 数
    private long remoteRefuseTransferToClient; // Peer 拒绝上传数量
    private long remoteAcceptTransferToClient; // Peer 接受上传数量
    private long localRefuseTransferToPeer; // 下载器拒绝上传数量
    private long localAcceptTransferToPeer; // 下载器接受上传数量
    private long localNotInterested; // 下载器不敢兴趣数量
    private long questionStatus; // 未知状态
    private long optimisticUnchoke; // 乐观解锁数量
    private long fromDHT; // 来自 DHT 的 Peer 数量
    private long fromPEX; // 来自 PeX 的 Peer 数量
    private long fromLSD; // 来自 LSD 的 Peer 数量
    private long fromTrackerOrOther; // 来自 Tracker 或手动添加的 Peer 数量
    private long rc4Encrypted; // 启用 RC4 加密的 Peer 数量
    private long plainTextEncrypted; // 明文传输加密的 Peer 数量
    private long utpSocket; // 使用 uTP 协议的 Peer 数量 
    private long tcpSocket; // 使用 TCP (BT) 协议的 Peer 数量

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
