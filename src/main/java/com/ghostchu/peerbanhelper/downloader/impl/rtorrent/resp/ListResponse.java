package com.ghostchu.peerbanhelper.downloader.impl.rtorrent.resp;

import com.ghostchu.peerbanhelper.downloader.impl.rtorrent.bean.RTorrent;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ListResponse {
    private final List<RTorrent> rpcTorrents = new ArrayList<>();

    public ListResponse(JsonObject jsonObject) {
        JsonObject torrents = jsonObject.getAsJsonObject("t");
        for (String hash : torrents.keySet()) {
            String[] args = torrents.getAsJsonArray(hash).asList().stream().map(JsonElement::getAsString).toArray(String[]::new);
            String[] mappedArgs = new String[args.length + 1];
            mappedArgs[0] = hash;
            System.arraycopy(args, 0, mappedArgs, 1, args.length);
            rpcTorrents.add(new RTorrent(mappedArgs));
        }
    }
}
