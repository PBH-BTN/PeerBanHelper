package com.ghostchu.peerbanhelper.util.dns.impl;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface PTRLookup {
    CompletableFuture<Optional<String>> ptr(String query);
}
