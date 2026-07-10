package com.ghostchu.peerbanhelper.module;

import io.javalin.http.sse.SseClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractSSEFeatureModule extends AbstractFeatureModule {
    protected final List<SseClient> sseClients = Collections.synchronizedList(new ArrayList<>());

    /**
     * Adds new SseClient to sseClients list, and mark it keepAlive.
     * It will also register a new listener for connection close.
     *
     * @param sseClient The SseClient
     */
    protected void registerSseManagement(SseClient sseClient) {
        this.sseClients.add(sseClient);
        sseClient.onClose(() -> onClose(sseClient));
        sseClient.keepAlive();
    }

    protected void iterateSseClients(Consumer<? super SseClient> clients){
        this.sseClients.forEach(clients);
    }

    protected void onClose(SseClient sseClient){
        this.sseClients.remove(sseClient);
    }
}