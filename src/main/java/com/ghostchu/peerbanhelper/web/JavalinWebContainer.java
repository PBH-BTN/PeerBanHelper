package com.ghostchu.peerbanhelper.web;

import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.exception.IPAddressBannedException;
import com.ghostchu.peerbanhelper.web.exception.NotLoggedInException;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JsonMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.Map;

@Slf4j
public class JavalinWebContainer {
    private final int port;
    private final Javalin javalin;
    @Getter
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
                    ctx.json(Map.of("message", "Not logged in"));
                })
                .exception(Exception.class, (e, ctx) -> {
                    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
                    ctx.json(Map.of("message", "Internal server error"));
                    log.warn("500 Internal Server Error", e);
                })
                .beforeMatched(ctx -> {
                    if (ctx.routeRoles().isEmpty()) {
                        return;
                    }
                    if (ctx.routeRoles().contains(Role.ANYONE)) {
                        return;
                    }
                    String authenticated = ctx.sessionAttribute("authenticated");
                    if (authenticated != null && authenticated.equals(token)) {
                        return;
                    }
                    String authToken = ctx.header("Authorization");
                    if (authToken != null) {
                        if (authToken.startsWith("Bearer ")) {
                            String tk = authToken.substring(7);
                            if (tk.equals(token)) {
                                return;
                            }
                        }
                    }
                    throw new NotLoggedInException();
                })
                .after(ctx -> {
                    ctx.header("Access-Control-Allow-Origin", "*");
                    ctx.header("Access-Control-Max-Age", "3628800");
                    ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                    ctx.header("Access-Control-Allow-Headers", "X-Requested-With");
                    ctx.header("Access-Control-Allow-Headers", "Authorization");
                    setSameSiteForSession(ctx);
                })
                .start(this.port);
    }

    private void setSameSiteForSession(Context ctx) {
        String sessionCookie = ctx.cookie("JSESSIONID");
        if (sessionCookie != null) {
            ctx.cookie("JSESSIONID", sessionCookie);
            ctx.header("Set-Cookie", "JSESSIONID=" + sessionCookie + "; Path=/; SameSite=none");
        }
    }

    public Javalin javalin() {
        return javalin;
    }
}
