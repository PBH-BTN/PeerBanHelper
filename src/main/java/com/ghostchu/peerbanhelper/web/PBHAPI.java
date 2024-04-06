package com.ghostchu.peerbanhelper.web;

import fi.iki.elonen.NanoHTTPD;

public interface PBHAPI {
    boolean shouldHandle(String uri);
    NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session);
}
