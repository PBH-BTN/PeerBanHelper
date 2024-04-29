package com.ghostchu.peerbanhelper.web;

import com.ghostchu.peerbanhelper.text.Lang;
import fi.iki.elonen.NanoHTTPD;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.ApiStatus;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class WebManager extends NanoHTTPD {

    private final Set<PBHAPI> apiEndpoints = new HashSet<>();

    public WebManager(int port) {
        super(port);
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
        }catch (Exception e){
            log.warn(Lang.ERR_INITIALIZE_BAN_PROVIDER_ENDPOINT_FAILURE, e);
        }
    }

    public void register(PBHAPI pbhapi) {
        synchronized (apiEndpoints) {
            apiEndpoints.add(pbhapi);
        }
    }


    public boolean unregister(PBHAPI pbhapi) {
        synchronized (apiEndpoints) {
            return apiEndpoints.remove(pbhapi);
        }
    }

    public boolean unregister(Class<PBHAPI> pbhapiClass) {
        synchronized (apiEndpoints) {
            return apiEndpoints.removeIf(c -> c.getClass().equals(pbhapiClass));
        }
    }

    public void unregisterAll() {
        synchronized (apiEndpoints) {
            apiEndpoints.forEach(this::unregister);
        }
    }

    @Override
    @ApiStatus.Internal
    public Response serve(IHTTPSession session) {
        if (session.getMethod() != Method.GET) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "error method");
        }
        for (PBHAPI apiEndpoint : apiEndpoints) {
            try {
                if (apiEndpoint.shouldHandle(session.getUri())) {
                    return apiEndpoint.handle(session);
                }
            } catch (Exception e) {
                log.error("Failed to handle API request {}", apiEndpoint.getClass().getName(), e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed to handle api request: " + e.getClass().getName() + ": " + e.getMessage());
            }
        }
        return serveStaticResources(session);
    }

    private Response serveStaticResources(IHTTPSession session) {
        // 尝试处理静态资源
        String uri = session.getUri();
        if (uri.isBlank() || uri.equals("/")) {
            uri = "/index.html";
        }
        return tryServeResources(session, uri, false);
    }

    private Response tryServeResources(IHTTPSession session, String uri, boolean fallback) {
        InputStream is = getClass().getResourceAsStream("/static" + uri);
        if (is == null) {
            if (uri.equals("/index.html")) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Error: Resources not found");
            }
            return tryServeResources(session, "/index.html", true);
        }
        // return newChunkedResponse(fallback ? Response.Status.NOT_FOUND : Response.Status.OK, NanoHTTPD.getMimeTypeForFile(uri), is);
        return newChunkedResponse(Response.Status.OK, NanoHTTPD.getMimeTypeForFile(uri), is);
    }
}
