package com.ghostchu.peerbanhelper.module.impl;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import kong.unirest.Empty;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import org.apache.commons.lang3.StringUtils;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.function.Function;

public class ActiveProbing extends AbstractFeatureModule {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(ActiveProbing.class);
    private final int timeout;
    private final List<Function<PeerAddress, BanResult>> rules = new ArrayList<>();
    private Cache<PeerAddress, BanResult> cache;

    public ActiveProbing(YamlConfiguration profile) {
        super(profile);
        this.timeout = getConfig().getInt("timeout", 3000);
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(getConfig().getLong("max-cached-entry", 3000))
                .expireAfterAccess(getConfig().getLong("expire-after-no-access", 28800), TimeUnit.SECONDS)
                .build();

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
    public String getName() {
        return "Active Probing";
    }

    @Override
    public String getConfigName() {
        return "active-probing";
    }

    @Override
    public BanResult shouldBanPeer(Torrent torrent, Peer peer, ExecutorService ruleExecuteExecutor) {
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
            return new BanResult(false, "[Runtime Exception] " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @NotNull
    private BanResult getBanResult(List<BanResult> finishedQueue) {
        BanResult banResult = null;

        for (BanResult result : finishedQueue) {
            if (banResult == null) {
                banResult = result;
            } else {
                if (!banResult.ban()) {
                    banResult = result;
                }
            }
        }

        if (banResult == null) {
            banResult = new BanResult(false, "No result provided");
        }
        return banResult;
    }

    private BanResult pingPeer(PeerAddress address) {
        try {
            InetAddress add = InetAddress.getByName(address.getIp());
            if (add.isReachable(timeout)) {
                return new BanResult(true, Lang.MODULE_AP_PEER_BAN_PING);
            }
        } catch (IOException e) {
            return new BanResult(false, "Exception: " + e.getClass().getName() + e.getMessage());
        }
        return new BanResult(false, "No response");
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
        UnirestInstance unirest = Unirest.spawnInstance();
        try (unirest) {
            unirest.config()
                    .connectTimeout(timeout)
                    .socketTimeout(timeout)
                    .automaticRetries(false)
                    .setDefaultHeader("User-Agent", String.format(getConfig().getString("http-probing-user-agent", "PeerBanHelper-PeerActiveProbing/%s (github.com/Ghost-chu/PeerBanHelper)"), Main.getMeta().getVersion()));
            if (scheme.equals("https") && spilt.length == 5) {
                unirest.config()
                        .verifySsl(Boolean.parseBoolean(spilt[4]));
            }
            HttpResponse<Empty> emptyHttpResponse = unirest.get(url).asEmpty();
            String code = String.valueOf(emptyHttpResponse.getStatus());
            if (code.equals(exceptedCode)) {
                return new BanResult(true, String.format(Lang.MODULE_AP_BAN_PEER_CODE, code));
            }
            return new BanResult(false, String.format(Lang.MODULE_AP_PEER_CODE, code));
        } catch (Exception e) {
            return new BanResult(false, "HTTP Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private BanResult tcpTestPeer(PeerAddress address, String[] spilt) {
        try (Socket socket = new Socket()) {
            int port = Integer.parseInt(spilt[1]);
            socket.connect(new InetSocketAddress(address.getIp(), port));
            if (socket.isConnected()) {
                return new BanResult(true, String.format(Lang.MODULE_AP_BAN_PEER_TCP_TEST, address.getIp() + " - " + port));
            }
            return new BanResult(false, String.format(Lang.MODULE_AP_TCP_TEST_PORT_FAIL, "Not connected"));
        } catch (IOException e) {
            return new BanResult(false, String.format(Lang.MODULE_AP_TCP_TEST_PORT_FAIL, e.getClass().getName() + ": " + e.getMessage()));
        } catch (NumberFormatException e) {
            log.warn(Lang.MODULE_AP_INCORRECT_TCP_TEST_PORT, spilt[1], e.getClass().getName() + ": " + e.getMessage());
            return new BanResult(false, String.format(Lang.MODULE_AP_TCP_TEST_PORT_FAIL, e.getClass().getName() + ": " + e.getMessage()));
        }
    }
}
