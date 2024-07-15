//package com.ghostchu.peerbanhelper.downloader.impl.rtorrent.resp;
//
//import com.ghostchu.peerbanhelper.downloader.impl.rtorrent.bean.RPeer;
//import com.google.gson.JsonArray;
//import com.google.gson.JsonElement;
//import lombok.Getter;
//
//import java.util.ArrayList;
//import java.util.List;
//@Getter
//public class PrsResponse {
//    private final List<RPeer> rpcPeers = new ArrayList<>();
//
//    public PrsResponse(JsonArray arr) {
//        for (JsonElement element : arr) {
//            JsonArray peerArray = element.getAsJsonArray();
//            rpcPeers.add(new RPeer(peerArray.asList().stream().map(JsonElement::getAsString).toArray(String[]::new)));
//        }
//    }
//}
