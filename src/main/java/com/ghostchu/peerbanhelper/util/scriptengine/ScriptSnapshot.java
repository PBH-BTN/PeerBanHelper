package com.ghostchu.peerbanhelper.util.scriptengine;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;

import java.util.HashMap;
import java.util.Map;

public final class ScriptSnapshot {
    private ScriptSnapshot() {}

    public static Map<String, Object> create(Torrent torrent, Peer peer, Downloader downloader, long banDuration) {
        var address = peer.getPeerAddress();
        Map<String, Object> peerData = new HashMap<>();
        var addressData = new HashMap<String, Object>();
        addressData.put("address", address.getIp());
        addressData.put("ip", address.getIp());
        addressData.put("port", address.getPort());
        peerData.put("peerAddress", addressData);
        peerData.put("peerId", peer.getPeerId());
        peerData.put("clientName", peer.getClientName());
        peerData.put("downloadSpeed", peer.getDownloadSpeed());
        peerData.put("downloaded", peer.getDownloaded());
        peerData.put("uploadSpeed", peer.getUploadSpeed());
        peerData.put("uploaded", peer.getUploaded());
        peerData.put("progress", peer.getProgress());
        peerData.put("handshaking", peer.isHandshaking());
        peerData.put("flags", peer.getFlags() == null ? null : peer.getFlags().toString());

        Map<String, Object> torrentData = new HashMap<>();
        torrentData.put("id", torrent.getId());
        torrentData.put("name", torrent.getName());
        torrentData.put("hash", torrent.getHash());
        torrentData.put("progress", torrent.getProgress());
        torrentData.put("size", torrent.getSize());
        torrentData.put("completedSize", torrent.getCompletedSize());
        torrentData.put("rtUploadSpeed", torrent.getRtUploadSpeed());
        torrentData.put("rtDownloadSpeed", torrent.getRtDownloadSpeed());
        torrentData.put("private", torrent.isPrivate());
        torrentData.put("seeding", torrent.isSeeding());
        Map<String, Object> downloaderData = new HashMap<>();
        downloaderData.put("id", downloader.getId());
        downloaderData.put("name", downloader.getName());
        downloaderData.put("type", downloader.getType());
        return Map.of("peer", peerData, "torrent", torrentData, "downloader", downloaderData, "banDuration", banDuration);
    }
}
