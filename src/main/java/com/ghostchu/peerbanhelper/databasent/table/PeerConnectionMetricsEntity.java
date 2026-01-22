package com.ghostchu.peerbanhelper.databasent.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@TableName("peer_connection_metrics")
public final class PeerConnectionMetricsEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    @TableField(value = "timeframe_at")
    private OffsetDateTime timeframeAt;
    @TableField(value = "downloader")
    private String downloader;
    @TableField(value = "total_connections")
    private long totalConnections;
    @TableField(value = "incoming_connections")
    private long incomingConnections;
    @TableField(value = "remote_refuse_transfer_to_client")
    private long remoteRefuseTransferToClient;
    @TableField(value = "remote_accept_transfer_to_client")
    private long remoteAcceptTransferToClient;
    @TableField(value = "local_refuse_transfer_to_peer")
    private long localRefuseTransferToPeer;
    @TableField(value = "local_accept_transfer_to_peer")
    private long localAcceptTransferToPeer;
    @TableField(value = "local_not_interested")
    private long localNotInterested;
    @TableField(value = "question_status")
    private long questionStatus;
    @TableField(value = "optimistic_unchoke")
    private long optimisticUnchoke;
    @TableField(value = "from_dht")
    private long fromDHT;
    @TableField(value = "from_pex")
    private long fromPEX;
    @TableField(value = "from_lsd")
    private long fromLSD;
    @TableField(value = "from_tracker_or_other")
    private long fromTrackerOrOther;
    @TableField(value = "rc4_encrypted")
    private long rc4Encrypted;
    @TableField(value = "plain_text_encrypted")
    private long plainTextEncrypted;
    @TableField(value = "utp_socket")
    private long utpSocket;
    @TableField(value = "tcp_socket")
    private long tcpSocket;

    public void merge(@NotNull PeerConnectionMetricsEntity appender) {
        this.totalConnections += appender.totalConnections;
        this.incomingConnections += appender.incomingConnections;
        this.remoteRefuseTransferToClient += appender.remoteRefuseTransferToClient;
        this.remoteAcceptTransferToClient += appender.remoteAcceptTransferToClient;
        this.localRefuseTransferToPeer += appender.localRefuseTransferToPeer;
        this.localAcceptTransferToPeer += appender.localAcceptTransferToPeer;
        this.questionStatus += appender.questionStatus;
        this.optimisticUnchoke += appender.optimisticUnchoke;
        this.fromDHT += appender.fromDHT;
        this.fromPEX += appender.fromPEX;
        this.fromLSD += appender.fromLSD;
        this.fromTrackerOrOther += appender.fromTrackerOrOther;
        this.rc4Encrypted += appender.rc4Encrypted;
        this.plainTextEncrypted += appender.plainTextEncrypted;
        this.utpSocket += appender.utpSocket;
        this.tcpSocket += appender.tcpSocket;
    }
}
