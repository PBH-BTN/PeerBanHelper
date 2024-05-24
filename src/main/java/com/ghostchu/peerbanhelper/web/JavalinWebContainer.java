package com.ghostchu.peerbanhelper.web;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.exception.IPAddressBannedException;
import com.ghostchu.peerbanhelper.web.exception.NotLoggedInException;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JsonMapper;
import io.javalin.plugin.bundled.CorsPluginConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.Map;

@Slf4j
public class JavalinWebContainer {
    private final Javalin javalin;
    @Getter
    private final String token;

    public JavalinWebContainer(int port, String token) {
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
                    c.bundledPlugins.enableCors(cors -> cors.addRule(CorsPluginConfig.CorsRule::anyHost));
                    c.staticFiles.add(staticFiles -> {
                        staticFiles.hostedPath = "/";
                        staticFiles.directory = "/static";
                        staticFiles.location = Location.CLASSPATH;
                        staticFiles.precompress = false;
                        staticFiles.aliasCheck = null;
                        staticFiles.skipFileFunction = req -> false;
                    });
                    c.spaRoot.addFile("/", "/static/index.html", Location.CLASSPATH);
                })
                .exception(IPAddressBannedException.class, (e, ctx) -> {
                    ctx.status(HttpStatus.TOO_MANY_REQUESTS);
                    ctx.json(Map.of("message", Lang.WEBAPI_AUTH_BANNED_TOO_FREQ));
                })
                .exception(NotLoggedInException.class, (e, ctx) -> {
                    ctx.status(HttpStatus.FORBIDDEN);
                    ctx.json(Map.of("message", Lang.WEBAPI_NOT_LOGGED));
                })
                .exception(Exception.class, (e, ctx) -> {
                    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
                    ctx.json(Map.of("message", Lang.WEBAPI_INTERNAL_ERROR));
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
                .options("/*", ctx -> ctx.status(200))
                .start(port);
    }

    public Javalin javalin() {
        return javalin;
    }
}
