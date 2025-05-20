package com.ghostchu.peerbanhelper.api.util.dns;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface DNSLookup {
    CompletableFuture<Optional<String>> ptr(String query);
}
