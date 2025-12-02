package com.ghostchu.peerbanhelper.btn.ability.impl;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.AbstractBtnAbility;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.common.net.InetAddresses;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONException;
import oshi.SystemInfo;
import oshi.hardware.NetworkIF;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public final class BtnAbilityHeartBeat extends AbstractBtnAbility {
    private final BtnNetwork btnNetwork;
    private final long interval;
    private final long randomInitialDelay;
    private final String endpoint;
    private final boolean multiIf;
    private final boolean powCaptcha;
    private String lastResult = "No information";

    public BtnAbilityHeartBeat(BtnNetwork btnNetwork, JsonObject ability) {
        this.btnNetwork = btnNetwork;
        this.interval = ability.get("interval").getAsLong();
        this.endpoint = ability.get("endpoint").getAsString();
        this.multiIf = ability.get("multi_if").getAsBoolean();
        this.randomInitialDelay = ability.get("random_initial_delay").getAsLong();
        this.powCaptcha = ability.get("pow_captcha").getAsBoolean();
    }

    @Override
    public String getName() {
        return "BtnAbilityHeartBeat";
    }

    @Override
    public TranslationComponent getDisplayName() {
        return new TranslationComponent(Lang.BTN_HEARTBEAT_TITLE);
    }

    @Override
    public TranslationComponent getDescription() {
        return new TranslationComponent(Lang.BTN_HEARTBEAT_DESCRIPTION, lastResult);
    }

    @Override
    public void load() {
        setLastStatus(true, new TranslationComponent(Lang.BTN_STAND_BY));
        btnNetwork.getScheduler().scheduleWithFixedDelay(this::sendHeartBeat, interval + ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
    }

    private void sendHeartBeat() {
        if (multiIf) {
            sendHeartBeatMultiIf();
        } else {
            sendHeartBeatDefaultIf();
        }
    }

    private void sendHeartBeatDefaultIf() {
        var body = RequestBody.create(JsonUtil.standard().toJson(Map.of("ifaddr", "default")), MediaType.parse("application/json"));
        Request.Builder request = new Request.Builder().url(endpoint).post(body);
        if (powCaptcha) btnNetwork.gatherAndSolveCaptchaBlocking(request);
        try (Response resp = btnNetwork.getHttpUtil().newBuilder().build().newCall(request.build()).execute()) {
            if (!resp.isSuccessful()) {
                setLastStatus(true, new TranslationComponent(Lang.BTN_HEARTBEAT_FAILED));
                lastResult = "default -> Failed: " + resp.code() + " - " + resp.body().string();
            } else {
                setLastStatus(true, new TranslationComponent(Lang.BTN_HEARTBEAT_SUCCESS));
                ServerResponse data = JsonUtil.standard().fromJson(resp.body().charStream(), ServerResponse.class);
                if (data != null && data.getExternalIp() != null) {
                    lastResult = "default -> " + data.getExternalIp();
                } else {
                    lastResult = "default -> No External IP returned";
                }

            }
        } catch (final IOException | JSONException e) {
            lastResult = "default -> Failed: " + e.getClass().getName() + ": " + e.getMessage();
            setLastStatus(true, new TranslationComponent(Lang.BTN_HEARTBEAT_FAILED));
        }
    }

    private void sendHeartBeatMultiIf() {
        List<CompletableFuture<Void>> futures = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean anySuccess = new AtomicBoolean(false);
        List<String> ifNets = new ArrayList<>();
        for (NetworkIF networkIF : new SystemInfo().getHardware().getNetworkIFs()) {
            var ipv4 = networkIF.getIPv4addr();
            var ipv6 = networkIF.getIPv6addr();
            ifNets.addAll(Arrays.asList(ipv4));
            ifNets.addAll(Arrays.asList(ipv6));
        }
        Map<String, String> result = Collections.synchronizedMap(new TreeMap<>());
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            ifNets.forEach(ip -> futures.add(CompletableFuture.runAsync(() -> {
                var client = createHttpClient(ip);
                var body = RequestBody.create(JsonUtil.standard().toJson(Map.of("ifaddr", ip)), MediaType.parse("application/json"));
                Request.Builder request = new Request.Builder().url(endpoint).post(body);
                try (Response resp = client.newCall(request.build()).execute()) {
                    if (!resp.isSuccessful()) {
                        result.put(ip, "Failed: " + resp.code() + ": " + resp.message());
                    } else {
                        ServerResponse data = JsonUtil.standard().fromJson(resp.body().charStream(), ServerResponse.class);
                        if (data != null && data.getExternalIp() != null) {
                            result.put(ip, data.getExternalIp());
                            anySuccess.set(true);
                        } else {
                            result.put(ip, "Failed: No external IP returned");
                        }
                    }
                } catch (final IOException | JSONException e) {
                    result.put(ip, "Failed: " + e.getClass().getName() + ": " + e.getMessage());
                }
            }, executorService)));
        }

        for (CompletableFuture<Void> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("Heartbeat request timed out");
                future.cancel(true);
            } catch (InterruptedException | ExecutionException e) {
                log.warn("Heartbeat request failed", e);
            }
        }

        if (anySuccess.get()) {
            setLastStatus(true, new TranslationComponent(Lang.BTN_HEARTBEAT_SUCCESS));
        } else {
            setLastStatus(false, new TranslationComponent(Lang.BTN_HEARTBEAT_FAILED));
        }
        // {ip} -> {result}
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> pair : result.entrySet()) {
            builder.append(pair.getKey()).append(" -> ").append(pair.getValue()).append("\n");
        }
        lastResult = builder.toString();
    }

    private OkHttpClient createHttpClient(String ifNet) {
        return btnNetwork.getHttpUtil()
                .newBuilder()
                .socketFactory(new SocketFactory() {
                    @Override
                    public Socket createSocket(String host, int port) throws IOException {
                        try {
                            return new Socket(host, port, InetAddresses.forString(ifNet), 0);
                        } catch (IllegalArgumentException e) {
                            throw new IOException("Invalid local IP address: " + ifNet, e);
                        }
                    }

                    @Override
                    public Socket createSocket(InetAddress address, int port) throws IOException {
                        try {
                            return new Socket(address, port, InetAddresses.forString(ifNet), 0);
                        } catch (IllegalArgumentException e) {
                            throw new IOException("Invalid local IP address: " + ifNet, e);
                        }
                    }

                    @Override
                    public Socket createSocket(String host, int port, InetAddress clientAddress, int clientPort) throws IOException {
                        return new Socket(host, port, clientAddress, clientPort);
                    }

                    @Override
                    public Socket createSocket(InetAddress address, int port, InetAddress clientAddress, int clientPort) throws IOException {
                        return new Socket(address, port, clientAddress, clientPort);
                    }
                })
                .build();
    }

    @Override
    public void unload() {

    }

    @AllArgsConstructor
    @Data
    static class ServerResponse {
        @SerializedName("external_ip")
        private String externalIp;
    }
}
