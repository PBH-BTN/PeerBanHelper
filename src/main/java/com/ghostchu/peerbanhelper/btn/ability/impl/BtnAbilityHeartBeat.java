package com.ghostchu.peerbanhelper.btn.ability.impl;

import com.ghostchu.peerbanhelper.btn.BtnNetwork;
import com.ghostchu.peerbanhelper.btn.ability.AbstractBtnAbility;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.common.net.InetAddresses;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.spotify.futures.CompletableFutures;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
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
        this.powCaptcha = ability.has("pow_captcha") && ability.get("pow_captcha").getAsBoolean();
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
        btnNetwork.getScheduler().scheduleWithFixedDelay(this::sendHeartBeat, ThreadLocalRandom.current().nextLong(randomInitialDelay), interval, TimeUnit.MILLISECONDS);
        lastResult = "Scheduled";
    }

    private void sendHeartBeat() {
        if (multiIf) {
            sendHeartBeatMultiIf();
        } else {
            sendHeartBeatDefaultIf();
        }
    }

    private void sendHeartBeatDefaultIf() {
        lastResult = "Requesting as default interface";
        var body = RequestBody.create(JsonUtil.standard().toJson(Map.of("ifaddr", "default")), MediaType.parse("application/json"));
        Request.Builder request = new Request.Builder().url(endpoint).post(body);
        if (powCaptcha) btnNetwork.gatherAndSolveCaptchaBlocking(request, "heartbeat");
        try (Response resp = btnNetwork.getHttpUtil().newBuilder().build().newCall(request.build()).execute()) {
            var responseBody = resp.body().string();
            if (!resp.isSuccessful()) {
                setLastStatus(true, new TranslationComponent(Lang.BTN_HEARTBEAT_FAILED));
                lastResult = "default -> Failed: " + resp.code() + " - " + responseBody;
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
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                var netif = interfaces.nextElement();
                var addrs = netif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    var addr = addrs.nextElement();
                    String ip = addr.getHostAddress();
                    ifNets.add(ip);
                }
            }
        } catch (SocketException exception) {
            log.warn("No interfaces found", exception);
        }
        Map<String, String> result = Collections.synchronizedMap(new TreeMap<>());
        ifNets.forEach(ip -> futures.add(CompletableFuture.runAsync(() -> {
            var client = createHttpClient(ip);
            var body = RequestBody.create(JsonUtil.standard().toJson(Map.of("ifaddr", ip)), MediaType.parse("application/json"));
            Request.Builder request = new Request.Builder().url(endpoint).post(body);
            if (powCaptcha) btnNetwork.gatherAndSolveCaptchaBlocking(request, "heartbeat");
            try (Response resp = client.newCall(request.build()).execute()) {
                var responseBody = resp.body().string();
                if (!resp.isSuccessful()) {
                    result.put(ip, "Failed: " + resp.code() + ": " + responseBody);
                } else {
                    ServerResponse data = JsonUtil.standard().fromJson(responseBody, ServerResponse.class);
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
        }, Executors.newVirtualThreadPerTaskExecutor())));
        lastResult = "Waiting for all heartbeat requests to complete";
        try {
            CompletableFutures.allAsList(futures).get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("Heartbeat request timed out");
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Heartbeat request failed", e);
        } finally {
            futures.forEach(future -> {
                if (!future.isDone() && !future.isCompletedExceptionally()) {
                    future.cancel(true);
                }
            });
        }
        if (anySuccess.get()) {
            setLastStatus(true, new TranslationComponent(Lang.BTN_HEARTBEAT_SUCCESS));
        } else {
            setLastStatus(false, new TranslationComponent(Lang.BTN_HEARTBEAT_FAILED));
        }
        StringBuilder builder = new StringBuilder();
        for (String ip : new LinkedHashSet<>(result.values())) {
            builder.append(ip).append("  \n");
        }
        lastResult = builder.toString();
    }

    private OkHttpClient createHttpClient(String ifNet) {
        InetAddress localAddress;
        try {
            localAddress = InetAddresses.forString(ifNet);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid local IP address: " + ifNet, e);
        }

        boolean isIPv4 = localAddress instanceof java.net.Inet4Address;

        OkHttpClient.Builder builder = btnNetwork.getHttpClient().newBuilder()
                .dns(hostname -> {
                    List<InetAddress> allAddresses = Dns.SYSTEM.lookup(hostname);
                    List<InetAddress> filteredAddresses = new ArrayList<>();

                    for (InetAddress address : allAddresses) {
                        boolean targetIsIPv4 = address instanceof java.net.Inet4Address;
                        if (isIPv4 == targetIsIPv4) {
                            filteredAddresses.add(address);
                        }
                    }

                    if (filteredAddresses.isEmpty()) {
                        log.warn("No matching {} addresses found for {}", isIPv4 ? "IPv4" : "IPv6", hostname);
                        return allAddresses;
                    }

                    return filteredAddresses;
                })
                .socketFactory(new SocketFactory() {
                    private final javax.net.SocketFactory defaultFactory = javax.net.SocketFactory.getDefault();

                    private Socket createAndBindSocket(InetAddress targetAddress) throws IOException {
                        if ((localAddress instanceof java.net.Inet4Address) != (targetAddress instanceof java.net.Inet4Address)) {
                            throw new IOException("Address family mismatch: cannot bind " +
                                    (localAddress instanceof java.net.Inet4Address ? "IPv4" : "IPv6") +
                                    " local address to " +
                                    (targetAddress instanceof java.net.Inet4Address ? "IPv4" : "IPv6") +
                                    " target");
                        }

                        Socket socket = new Socket();
                        try {
                            socket.bind(new java.net.InetSocketAddress(localAddress, 0));
                        } catch (IOException e) {
                            throw new IOException("Failed to bind to local IP address: " + ifNet, e);
                        }
                        return socket;
                    }

                    @Override
                    public Socket createSocket() throws IOException {
                        return defaultFactory.createSocket();
                    }

                    @Override
                    public Socket createSocket(String host, int port) throws IOException {
                        InetAddress address = InetAddress.getByName(host);
                        Socket socket = createAndBindSocket(address);
                        socket.connect(new java.net.InetSocketAddress(address, port));
                        return socket;
                    }

                    @Override
                    public Socket createSocket(InetAddress address, int port) throws IOException {
                        Socket socket = createAndBindSocket(address);
                        socket.connect(new java.net.InetSocketAddress(address, port));
                        return socket;
                    }

                    @Override
                    public Socket createSocket(String host, int port, InetAddress clientAddress, int clientPort) throws IOException {
                        return defaultFactory.createSocket(host, port, clientAddress, clientPort);
                    }

                    @Override
                    public Socket createSocket(InetAddress address, int port, InetAddress clientAddress, int clientPort) throws IOException {
                        return defaultFactory.createSocket(address, port, clientAddress, clientPort);
                    }
                });
        var trustManager = btnNetwork.getHttpClient().x509TrustManager();
        if (trustManager != null) {
            SSLSocketFactory originalSslFactory = btnNetwork.getHttpClient().sslSocketFactory();
            builder.sslSocketFactory(new SSLSocketFactory() {
                @Override
                public String[] getDefaultCipherSuites() {
                    return originalSslFactory.getDefaultCipherSuites();
                }

                @Override
                public String[] getSupportedCipherSuites() {
                    return originalSslFactory.getSupportedCipherSuites();
                }

                private Socket createAndBindSocket(InetAddress targetAddress) throws IOException {
                    if ((localAddress instanceof java.net.Inet4Address) != (targetAddress instanceof java.net.Inet4Address)) {
                        throw new IOException("SSL: Address family mismatch: cannot bind " +
                                (localAddress instanceof java.net.Inet4Address ? "IPv4" : "IPv6") +
                                " local address to " +
                                (targetAddress instanceof java.net.Inet4Address ? "IPv4" : "IPv6") +
                                " target");
                    }

                    Socket socket = new Socket();
                    try {
                        socket.bind(new java.net.InetSocketAddress(localAddress, 0));
                    } catch (IOException e) {
                        throw new IOException("SSL: Failed to bind to local IP address: " + ifNet, e);
                    }
                    return socket;
                }

                @Override
                public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
                    return originalSslFactory.createSocket(s, host, port, autoClose);
                }

                @Override
                public Socket createSocket(String host, int port) throws IOException {
                    InetAddress address = InetAddress.getByName(host);
                    Socket socket = createAndBindSocket(address);
                    socket.connect(new java.net.InetSocketAddress(address, port));
                    return originalSslFactory.createSocket(socket, host, port, true);
                }

                @Override
                public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
                    return originalSslFactory.createSocket(host, port, localHost, localPort);
                }

                @Override
                public Socket createSocket(InetAddress address, int port) throws IOException {
                    Socket socket = createAndBindSocket(address);
                    socket.connect(new java.net.InetSocketAddress(address, port));
                    return originalSslFactory.createSocket(socket, address.getHostAddress(), port, true);
                }

                @Override
                public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
                    return originalSslFactory.createSocket(address, port, localAddress, localPort);
                }
            }, btnNetwork.getHttpClient().x509TrustManager());
        }
        return builder.build();
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
