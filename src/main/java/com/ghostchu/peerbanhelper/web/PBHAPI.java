package com.ghostchu.peerbanhelper.web;

import fi.iki.elonen.NanoHTTPD;

import java.util.List;

public interface PBHAPI {
    boolean shouldHandle(String uri);

    NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session);

    /**
     * 申明支持的方法
     *
     * @return 支持的方法
     */
    default List<NanoHTTPD.Method> shouldHandleMethods() {
        return List.of(NanoHTTPD.Method.GET);
    }
}
