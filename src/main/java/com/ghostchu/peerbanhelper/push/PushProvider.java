package com.ghostchu.peerbanhelper.push;

import com.google.gson.JsonObject;

public interface PushProvider {
    String getConfigType();
    JsonObject saveJson();
    boolean push(String title, String content) throws Exception;

}