package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang3.StringUtils;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.function.Function;

public class ActiveProbing extends AbstractFeatureModule {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(ActiveProbing.class);
    private int timeout;
    private final List<Function<PeerAddress, BanResult>> rules = new ArrayList<>();
    private Cache<PeerAddress, BanResult> cache;

    public ActiveProbing(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public @NotNull String getName() {
        return "Active Probing";
    }

    @Override
    public @NotNull String getConfigName() {
        return "active-probing";
    }

    @Override
    public void onEnable() {
        reloadConfig();
    }
    @Override
    public void onDisable() {

    }

    private void reloadConfig() {
        this.timeout = getConfig().getInt("timeout", 3000);
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(getConfig().getLong("max-cached-entry", 3000))
                .expireAfterAccess(getConfig().getLong("expire-after-no-access", 28800), TimeUnit.SECONDS)
                .build();
        this.rules.clear();
        for (String rule : getConfig().getStringList("probing")) {
            if (rule.equals("PING")) {
                rules.add(this::pingPeer);
                continue;
            }
            String[] spilt = rule.split("@");
            switch (spilt[0]) {
                case "TCP" -> rules.add((address) -> tcpTestPeer(address, spilt));
                case "HTTP", "HTTPS" -> {
                    if (spilt.length < 4) {
                        log.warn(Lang.MODULE_AP_INVALID_RULE, rule);
                        continue;
                    }
                    rules.add((address) -> httpTestPeer(address, spilt));
                }
                default -> log.warn(Lang.MODULE_AP_INVALID_RULE, rule);
            }
        }
    }


    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        PeerAddress peerAddress = peer.getAddress();
        try {
            return cache.get(peerAddress, () -> {
                List<BanResult> finishedQueue = new ArrayList<>();
                List<CompletableFuture<?>> queue = new ArrayList<>(rules.size());

                for (Function<PeerAddress, BanResult> rule : rules) {
                    queue.add(CompletableFuture.runAsync(() -> finishedQueue.add(rule.apply(peerAddress)), ruleExecuteExecutor));
                }

                try {
                    CompletableFuture.allOf(queue.toArray(new CompletableFuture[0])).get(timeout + 5, TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException ignored) {
                }

                return getBanResult(finishedQueue);
            });
        } catch (ExecutionException e) {
            log.error(Lang.MODULE_AP_EXECUTE_EXCEPTION, e);
            return new BanResult(this, PeerAction.NO_ACTION, "[Runtime Exception] " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @NotNull
    private BanResult getBanResult(List<BanResult> finishedQueue) {
        BanResult banResult = null;

        for (BanResult result : finishedQueue) {
            if (banResult == null) {
                banResult = result;
            } else {
                if (banResult.action() == PeerAction.NO_ACTION) {
                    banResult = result;
                }
            }
        }

        if (banResult == null) {
            banResult = new BanResult(this,PeerAction.NO_ACTION, "No result provided");
        }
        return banResult;
    }

    private BanResult pingPeer(PeerAddress address) {
        try {
            InetAddress add = InetAddress.getByName(address.getIp());
            if (add.isReachable(timeout)) {
                return new BanResult(this,PeerAction.BAN, Lang.MODULE_AP_PEER_BAN_PING);
            }
        } catch (IOException e) {
            return new BanResult(this,PeerAction.NO_ACTION, "Exception: " + e.getClass().getName() + e.getMessage());
        }
        return new BanResult(this,PeerAction.NO_ACTION, "No response");
    }

    private BanResult httpTestPeer(PeerAddress address, String[] spilt) {
        String scheme = spilt[0].toLowerCase(Locale.ROOT);
        String host = address.getIp();
        String port = spilt[2];
        String subpath = spilt[1];
        String exceptedCode = spilt[3];
        String url = scheme + "://" + host;
        if (StringUtils.isNotEmpty(port)) {
            url += ":" + port;
        }
        if (StringUtils.isNotEmpty(subpath)) {
            if (subpath.startsWith("/")) {
                url += subpath;
            } else {
                url += "/" + subpath;
            }
        }
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_NONE);
        HttpClient.Builder builder = Methanol
                .newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .userAgent(String.format(getConfig().getString("http-probing-user-agent", "PeerBanHelper-PeerActiveProbing/%s (github.com/Ghost-chu/PeerBanHelper)"), Main.getMeta().getVersion()))
                .connectTimeout(Duration.of(timeout, ChronoUnit.MILLIS))
                .cookieHandler(cm);
        if (scheme.equals("https") && spilt.length == 5 && HTTPUtil.getIgnoreSslContext() != null) {
            builder = builder.sslContext(HTTPUtil.getIgnoreSslContext());
        }
        HttpClient client = builder.build();
        try {
            HttpResponse<String> resp = client.send(MutableRequest.GET(url),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            String code = String.valueOf(resp.statusCode());
            if (code.equals(exceptedCode)) {
                return new BanResult(this, PeerAction.BAN, String.format(Lang.MODULE_AP_BAN_PEER_CODE, code));
            }
            return new BanResult(this,PeerAction.NO_ACTION, String.format(Lang.MODULE_AP_PEER_CODE, code));
        } catch (IOException | InterruptedException e) {
            return new BanResult(this,PeerAction.NO_ACTION, "HTTP Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private BanResult tcpTestPeer(PeerAddress address, String[] spilt) {
        try (Socket socket = new Socket()) {
            int port = Integer.parseInt(spilt[1]);
            socket.connect(new InetSocketAddress(address.getIp(), port));
            if (socket.isConnected()) {
                return new BanResult(this,PeerAction.BAN, String.format(Lang.MODULE_AP_BAN_PEER_TCP_TEST, address.getIp() + " - " + port));
            }
            return new BanResult(this,PeerAction.NO_ACTION, String.format(Lang.MODULE_AP_TCP_TEST_PORT_FAIL, "Not connected"));
        } catch (IOException e) {
            return new BanResult(this,PeerAction.NO_ACTION, String.format(Lang.MODULE_AP_TCP_TEST_PORT_FAIL, e.getClass().getName() + ": " + e.getMessage()));
        } catch (NumberFormatException e) {
            log.warn(Lang.MODULE_AP_INCORRECT_TCP_TEST_PORT, spilt[1], e.getClass().getName() + ": " + e.getMessage());
            return new BanResult(this,PeerAction.NO_ACTION, String.format(Lang.MODULE_AP_TCP_TEST_PORT_FAIL, e.getClass().getName() + ": " + e.getMessage()));
        }
    }
}
