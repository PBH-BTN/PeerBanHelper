package com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean;

import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class A2Task implements Torrent {
    private BittorrentType bittorrent;
    private Long completedLength;
    private String connections;
    private Long downloadSpeed;
    private List<FilesType> files;
    private String gid;
    private String infoHash;
    private Long numSeeders;
    private Long seeder;
    private Long status;
    private Long totalLength;
    private Long uploadSpeed;

    @Override
    public @NotNull String getId() {
        return gid;
    }

    @Override
    public @NotNull String getName() {
        return bittorrent.getInfo().getName();
    }

    @Override
    public @NotNull String getHash() {
        return infoHash;
    }

    @Override
    public double getProgress() {
        return (double) completedLength / totalLength;
    }

    @Override
    public long getSize() {
        return totalLength;
    }

    @Override
    public long getCompletedSize() {
        return completedLength;
    }

    @Override
    public long getRtUploadSpeed() {
        return uploadSpeed;
    }

    @Override
    public long getRtDownloadSpeed() {
        return downloadSpeed;
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class InfoType {
        private String name;
    }
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class BittorrentType {
        private List<List<String>> announceList;
        private Integer creationDate;
        private InfoType info;
        private String magnetLink;
        private String mode;
    }
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class FilesType {
        private String completedLength;
        private String index;
        private String length;
        private String path;
        private String selected;
        private List<Object> uris;
    }

}
