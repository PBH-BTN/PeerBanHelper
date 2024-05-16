package com.ghostchu.peerbanhelper.web;

import com.ghostchu.peerbanhelper.web.exception.IPAddressBannedException;
import com.ghostchu.peerbanhelper.web.exception.NotLoggedInException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class JavalinWebContainer {
    private final int port;
    @Getter
    private final Javalin javalin;

    private final Cache<String, AtomicInteger> antiBruteAttack = CacheBuilder.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .build();
    private final String token;

    public JavalinWebContainer(int port, String token) {
        this.port = port;
        this.token = token;
        this.javalin = Javalin.create()
                .exception(IPAddressBannedException.class, (e, ctx) -> {
                    ctx.status(HttpStatus.TOO_MANY_REQUESTS);
                    ctx.json(Map.of("message", "IP banned for 15 minutes, try again later!"));
                })
                .exception(NotLoggedInException.class, (e, ctx) -> {
                    ctx.status(HttpStatus.FORBIDDEN);
                    ctx.json(Map.of("message", "Token incorrect or Not logged in"));
                })
                .before(ctx -> {
                    if (ctx.path().startsWith("/api/metadata/manifest")) { // Bypass authenticate
                        return;
                    }
                    if (!ctx.path().startsWith("/api")) {
                        return;
                    }
                    String authToken = ctx.header("PBH-Auth-Token");
                    if (isBruteAttack(ctx.ip())) {
                        throw new IPAddressBannedException();
                    }
                    if (authToken == null || !authToken.equals(this.token)) {
                        markBruteAttack(ctx.ip());
                        throw new NotLoggedInException();
                    }
                })
                .after(ctx -> {
                    ctx.header("Access-Control-Allow-Origin", "*");
                    ctx.header("Access-Control-Max-Age", "3628800");
                    ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                    ctx.header("Access-Control-Allow-Headers", "X-Requested-With");
                    ctx.header("Access-Control-Allow-Headers", "Authorization");
                })
                .start(this.port);
    }

    private void markBruteAttack(String ipAddress) {
        try {
            AtomicInteger counter = antiBruteAttack.get(ipAddress, () -> new AtomicInteger(0));
            counter.addAndGet(1);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isBruteAttack(String ipAddress) {
        AtomicInteger counter = antiBruteAttack.getIfPresent(ipAddress);
        return counter != null && counter.get() >= 10;
    }
}
