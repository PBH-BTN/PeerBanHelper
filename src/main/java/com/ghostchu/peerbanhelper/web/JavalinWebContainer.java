package com.ghostchu.peerbanhelper.web;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TextManager;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.web.exception.IPAddressBannedException;
import com.ghostchu.peerbanhelper.web.exception.NeedInitException;
import com.ghostchu.peerbanhelper.web.exception.NotLoggedInException;
import com.ghostchu.peerbanhelper.web.exception.RequirePBHPlusLicenseException;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JsonMapper;
import io.javalin.plugin.bundled.CorsPluginConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Slf4j
@Component
public class JavalinWebContainer {
    private final Javalin javalin;
    @Setter
    @Getter
    private String token;
    private Cache<String, AtomicInteger> FAIL2BAN = CacheBuilder.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .build();

    public JavalinWebContainer() {
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
                    c.showJavalinBanner = false;
                    c.jsonMapper(gsonMapper);
                    c.useVirtualThreads = true;
                    if (Main.getMainConfig().getBoolean("server.allow-cors")
                            || System.getenv("PBH_ALLOW_CORS") != null
                            || System.getProperty("PBH_ALLOW_CORS") != null
                    ) {
                        c.bundledPlugins.enableCors(cors -> cors.addRule(CorsPluginConfig.CorsRule::anyHost));
                    }
                    if (Main.getMainConfig().getBoolean("server.external-webui", false)) {
                        c.staticFiles.add(staticFiles -> {
                            staticFiles.hostedPath = "/";
                            staticFiles.directory = new File(Main.getDataDirectory(), "static").getPath();
                            staticFiles.location = Location.EXTERNAL;
                            staticFiles.precompress = false;
                            staticFiles.aliasCheck = null;
                            staticFiles.skipFileFunction = req -> false;
                            //staticFiles.headers.put("Cache-Control", "no-cache");
                        });
                        c.spaRoot.addFile("/", new File(new File(Main.getDataDirectory(), "static"), "index.html").getPath(), Location.EXTERNAL);
                    } else {
                        c.staticFiles.add(staticFiles -> {
                            staticFiles.hostedPath = "/";
                            staticFiles.directory = "/static";
                            staticFiles.location = Location.CLASSPATH;
                            staticFiles.precompress = false;
                            staticFiles.aliasCheck = null;
                            staticFiles.skipFileFunction = req -> false;
                            //staticFiles.headers.put("Cache-Control", "no-cache");
                        });
                        c.spaRoot.addFile("/", "/static/index.html", Location.CLASSPATH);
                    }

                })
                .exception(IPAddressBannedException.class, (e, ctx) -> {
                    ctx.status(HttpStatus.TOO_MANY_REQUESTS);
                    ctx.json(new StdResp(false, tl(reqLocale(ctx), Lang.WEBAPI_AUTH_BANNED_TOO_FREQ), null));
                })
                .exception(NotLoggedInException.class, (e, ctx) -> {
                    ctx.status(HttpStatus.FORBIDDEN);
                    ctx.json(new StdResp(false, tl(reqLocale(ctx), Lang.WEBAPI_NOT_LOGGED), null));
                })
                .exception(NeedInitException.class, (e, ctx) -> {
                    ctx.status(HttpStatus.SEE_OTHER);
                    ctx.header("Location", "/init");
                    ctx.json(new StdResp(false, tl(reqLocale(ctx), Lang.WEBAPI_NEED_INIT), Map.of("location", "/init")));
                })
                .exception(IllegalArgumentException.class, (e, ctx) -> {
                    ctx.status(HttpStatus.BAD_REQUEST);
                    ctx.json(new StdResp(false, null, e.getMessage()));
                })
                .exception(RequirePBHPlusLicenseException.class, (e, ctx) -> {
                    ctx.status(HttpStatus.PAYMENT_REQUIRED);
                    ctx.json(new StdResp(false, null, e.getMessage()));
                })
                .exception(Exception.class, (e, ctx) -> {
                    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
                    ctx.json(new StdResp(false, null, tl(reqLocale(ctx), Lang.WEBAPI_INTERNAL_ERROR)));
                    log.error("500 Internal Server Error", e);
                })
                .beforeMatched(ctx -> {
                    if (ctx.routeRoles().isEmpty()) {
                        return;
                    }
                    if (ctx.routeRoles().contains(Role.ANYONE)) {
                        return;
                    }
                    if (ctx.path().startsWith("/init")) {
                        return;
                    }
                    if (token == null || token.isBlank()) {
                        throw new NeedInitException();
                    }
                    String authenticated = ctx.sessionAttribute("authenticated");
                    if (authenticated != null && authenticated.equals(token)) {
                        return;
                    }
                    // 开始登陆验证
                    if (!allowAttemptLogin(ctx.ip())) {
                        throw new IPAddressBannedException();
                    }
                    String authToken = ctx.header("Authorization");
                    if (authToken != null) {
                        if (authToken.startsWith("Bearer ")) {
                            String tk = authToken.substring(7);
                            if (tk.equals(token)) {
                                markLoginSuccess(ctx.ip());
                                return;
                            }
                        }
                    }
                    markLoginFailed(ctx.ip());
                    throw new NotLoggedInException();
                })
                .options("/*", ctx -> ctx.status(200));

    }

    public void start(String host, int port, String token) {
        this.token = token;
        javalin.start(host, port);
    }

    public Javalin javalin() {
        return javalin;
    }

    public String reqLocale(Context context) {
        for (AcceptLanguages requestLocale : requestLocales(context)) {
            String pbhCode = requestLocale.code.toLowerCase(Locale.ROOT).replace("-", "_");
            if (TextManager.INSTANCE_HOLDER.getAvailableLanguages().contains(pbhCode)) {
                return pbhCode;
            }
        }
        return Main.DEF_LOCALE;
    }

    private List<AcceptLanguages> requestLocales(Context context) {
        String headerLocale = context.header("Accept-Language");
        if (headerLocale == null) {
            return List.of(new AcceptLanguages(Main.DEF_LOCALE, 1.0f));
        }
        // zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6
        // zh
        List<AcceptLanguages> preferLocales = new ArrayList<>();
        String[] browserRequested = headerLocale.split(",");
        for (String s : browserRequested) {
            String[] localeSettings = s.split(";");
            String localeCode = localeSettings[0];
            float prefer = 1.0f;
            try {
                if (localeSettings.length > 1) {
                    prefer = Float.parseFloat(localeSettings[1].substring(2));
                }
            } catch (Exception ignored) {
            }
            preferLocales.add(new AcceptLanguages(localeCode, prefer));
        }
        preferLocales.sort(Comparator.reverseOrder());
        return preferLocales;
    }

    @SneakyThrows
    public boolean allowAttemptLogin(String ip) {
        var counter = FAIL2BAN.get(ip, () -> new AtomicInteger(0));
        return counter.get() <= 10;
    }

    @SneakyThrows
    public void markLoginFailed(String ip) {
        var counter = FAIL2BAN.get(ip, () -> new AtomicInteger(0));
        counter.incrementAndGet();
    }

    @SneakyThrows
    public void markLoginSuccess(String ip) {
        var counter = FAIL2BAN.get(ip, () -> new AtomicInteger(0));
        counter.set(0);
    }

    private Cache<String, AtomicInteger> fail2Ban() {
        return FAIL2BAN;
    }

    public record AcceptLanguages(String code, float prefer) implements Comparable<AcceptLanguages> {
        @Override
        public int compareTo(@NotNull JavalinWebContainer.AcceptLanguages o) {
            return Float.compare(prefer, o.prefer);
        }
    }
}
