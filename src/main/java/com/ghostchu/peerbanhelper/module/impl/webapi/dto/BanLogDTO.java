package com.ghostchu.peerbanhelper.module.impl.webapi.dto;

import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.ghostchu.peerbanhelper.downloader.DownloaderBasicInfo;
import com.ghostchu.peerbanhelper.downloader.DownloaderManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BanLogDTO {
    private long banAt;
    private long unbanAt;
    private String peerIp;
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

    public BanLogDTO(String locale, DownloaderManager downloaderManager, HistoryEntity history) {
        this.banAt = history.getBanAt().getTime();
        this.unbanAt = history.getUnbanAt().getTime();
        this.peerIp = history.getIp();
        this.peerPort = history.getPort();
        this.peerId = history.getPeerId();
        this.peerClientName = history.getPeerClientName();
        this.peerUploaded = history.getPeerUploaded();
        this.peerDownloaded = history.getPeerDownloaded();
        this.peerProgress = history.getPeerProgress();
        this.torrentInfoHash = history.getTorrent().getInfoHash();
        this.torrentName = history.getTorrent().getName();
        this.torrentSize = history.getTorrent().getSize();
        this.module = history.getRule().getModule().getName();
        this.rule = tl(locale, history.getRule().getRule());
        this.description = tl(locale, history.getDescription());
        this.downloader = downloaderManager.getDownloadInfo(history.getDownloader());
    }
}
