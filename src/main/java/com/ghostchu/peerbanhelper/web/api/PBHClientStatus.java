package com.ghostchu.peerbanhelper.web.api;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import fi.iki.elonen.NanoHTTPD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PBHClientStatus implements PBHAPI {
    private final PeerBanHelperServer server;

    public PBHClientStatus(PeerBanHelperServer server) {
        this.server = server;
    }

    @Override
    public boolean shouldHandle(String uri) {
        return uri.equals("/api/clientStatus");
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Downloader downloader : server.getDownloaders()) {
            Map<String, Object> map = new HashMap<>(2);
            map.put("name", downloader.getName());
            map.put("endpoint", downloader.getEndpoint());
            try {
                map.put("status", downloader.getLastStatus().name());
                List<Torrent> torrents = downloader.getTorrents();
                long peers = torrents.stream().mapToLong(t -> downloader.getPeers(t).size()).count();
                map.put("torrents", torrents.size());
                map.put("peers", peers);
            } catch (Throwable th) {
                map.put("status", DownloaderLastStatus.ERROR);
            }
            list.add(map);
        }
        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(list));
    }


}
