package com.ghostchu.peerbanhelper.web;

import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.exception.IPAddressBannedException;
import com.ghostchu.peerbanhelper.web.exception.NotLoggedInException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class JavalinWebContainer {
    private final int port;
    private final Javalin javalin;

    private final Cache<String, AtomicInteger> antiBruteAttack = CacheBuilder.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .build();
    private final String token;

    public JavalinWebContainer(int port, String token) {
        this.port = port;
        this.token = token;
        JsonMapper gsonMapper = new JsonMapper() {
            @Override
            public @NotNull String toJsonString(@NotNull Object obj, @NotNull Type type) {
                return JsonUtil.standard().toJson(obj, type);
            }

            @Override
            public <T> @NotNull T fromJsonString(@NotNull String json, @NotNull Type targetType) {
                return JsonUtil.standard().fromJson(json, targetType);
            }
        };
        this.javalin = Javalin.create(c -> {
                    c.http.gzipOnlyCompression();
                    c.http.generateEtags = true;
                    c.showJavalinBanner = false;
                    c.jsonMapper(gsonMapper);
                    c.useVirtualThreads = true;
                    c.staticFiles.add(staticFiles -> {
                        staticFiles.hostedPath = "/";
                        staticFiles.directory = "/static";
                        staticFiles.location = Location.CLASSPATH;
                        staticFiles.precompress = false;
                        staticFiles.aliasCheck = null;
                        staticFiles.skipFileFunction = req -> false;
                    });
                })
                .exception(IPAddressBannedException.class, (e, ctx) -> {
                    ctx.status(HttpStatus.TOO_MANY_REQUESTS);
                    ctx.json(Map.of("message", "IP banned for 15 minutes, try again later!"));
                })
                .exception(NotLoggedInException.class, (e, ctx) -> {
                    ctx.status(HttpStatus.FORBIDDEN);
                    ctx.json(Map.of("message", "Token incorrect or Not logged in"));
                })
                .exception(Exception.class, (e, ctx) -> {
                    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
                    ctx.json(Map.of("message", "Internal server error"));
                    log.warn("500 Internal Server Error", e);
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

    public Javalin javalin() {
        return javalin;
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
