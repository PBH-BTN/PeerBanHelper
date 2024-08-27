package com.ghostchu.peerbanhelper.push;

public interface PushProvider {
    boolean push(String title, String content) throws Exception;
}