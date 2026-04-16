package com.ghostchu.peerbanhelper.databasent.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ghostchu.peerbanhelper.util.helpstatus.CanDirty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@TableName(value = "peer_connection_metrics", autoResultMap = true)
public final class PeerConnectionMetricsEntity extends AbstractCanDirtyEntity implements Serializable, CanDirty {
    @Serial
    private static final long serialVersionUID = 1L;
    private transient boolean dirty;

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

    public PeerConnectionMetricsEntity(Long id, OffsetDateTime timeframeAt, String downloader, long totalConnections, long incomingConnections, long remoteRefuseTransferToClient, long remoteAcceptTransferToClient, long localRefuseTransferToPeer, long localAcceptTransferToPeer, long localNotInterested, long questionStatus, long optimisticUnchoke, long fromDHT, long fromPEX, long fromLSD, long fromTrackerOrOther, long rc4Encrypted, long plainTextEncrypted, long utpSocket, long tcpSocket) {
        this.id = id;
        this.timeframeAt = timeframeAt;
        this.downloader = downloader;
        this.totalConnections = totalConnections;
        this.incomingConnections = incomingConnections;
        this.remoteRefuseTransferToClient = remoteRefuseTransferToClient;
        this.remoteAcceptTransferToClient = remoteAcceptTransferToClient;
        this.localRefuseTransferToPeer = localRefuseTransferToPeer;
        this.localAcceptTransferToPeer = localAcceptTransferToPeer;
        this.localNotInterested = localNotInterested;
        this.questionStatus = questionStatus;
        this.optimisticUnchoke = optimisticUnchoke;
        this.fromDHT = fromDHT;
        this.fromPEX = fromPEX;
        this.fromLSD = fromLSD;
        this.fromTrackerOrOther = fromTrackerOrOther;
        this.rc4Encrypted = rc4Encrypted;
        this.plainTextEncrypted = plainTextEncrypted;
        this.utpSocket = utpSocket;
        this.tcpSocket = tcpSocket;
    }

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
