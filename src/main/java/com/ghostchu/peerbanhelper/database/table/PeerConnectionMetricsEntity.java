package com.ghostchu.peerbanhelper.database.table;

import com.ghostchu.peerbanhelper.database.dao.impl.PeerConnectionMetricDao;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Data
@DatabaseTable(tableName = "peer_connection_metrics", daoClass = PeerConnectionMetricDao.class)
public final class PeerConnectionMetricsEntity {
    @DatabaseField(generatedId = true)
    private Long id;
    @DatabaseField(canBeNull = false, index = true, uniqueCombo = true)
    private Timestamp timeframeAt;
    @DatabaseField(canBeNull = false, index = true, uniqueCombo = true)
    private String downloader;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long totalConnections;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long incomingConnections;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long remoteRefuseTransferToClient;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long remoteAcceptTransferToClient;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long localRefuseTransferToPeer;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long localAcceptTransferToPeer;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long localNotInterested;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long questionStatus;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long optimisticUnchoke;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long fromDHT;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long fromPEX;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long fromLSD;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long fromTrackerOrOther;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long rc4Encrypted;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long plainTextEncrypted;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long utpSocket;
    @DatabaseField(canBeNull = false, defaultValue = "0")
    private long tcpSocket;

    public void merge(@NotNull PeerConnectionMetricsEntity appender){
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
