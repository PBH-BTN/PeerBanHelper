package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.InetAddress;
import java.time.OffsetDateTime;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@AllArgsConstructor
@NoArgsConstructor
@Data
public final class BanLogDTO {
    private OffsetDateTime banAt;
    private OffsetDateTime unbanAt;
    private InetAddress peerIp;
    private int peerPort;
    private String peerId;
    private String peerClientName;
    private long peerUploaded;
    private long peerDownloaded;
    private double peerProgress;
    private String torrentInfoHash;
    private String torrentName;
    private long torrentSize;
    private String module;
    private String rule;
    private String description;
    private DownloaderBasicInfo downloader;

    public BanLogDTO(String locale, DownloaderManagerImpl downloaderManager, HistoryEntity history, TorrentEntityDTO torrent) {
        this.banAt = history.getBanAt();
        this.unbanAt = history.getUnbanAt();
        this.peerIp = history.getIp();
        this.peerPort = history.getPort();
        this.peerId = history.getPeerId();
        this.peerClientName = history.getPeerClientName();
        this.peerUploaded = history.getPeerUploaded();
        this.peerDownloaded = history.getPeerDownloaded();
        this.peerProgress = history.getPeerProgress();
        this.torrentInfoHash = torrent.infoHash();
        this.torrentName = torrent.name();
        this.torrentSize = torrent.size();
        this.module = history.getModuleName();
        this.rule = tl(locale, history.getRuleName());
        this.description = tl(locale, history.getDescription());
        this.downloader = downloaderManager.getDownloadInfo(history.getDownloader());
    }
}
