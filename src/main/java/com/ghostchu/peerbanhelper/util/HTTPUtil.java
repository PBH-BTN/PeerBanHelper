package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.common.net.InetAddresses;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public final class HTTPUtil implements Reloadable {
    @Getter
    private static SSLContext ignoreSslContext;
    private Proxy.Type proxyType;
    private @Nullable String proxyHost;
    private int proxyPort;
    private final List<Pattern> proxyBypasses = Collections.synchronizedList(new ArrayList<>());
    private Proxy proxyInstance;
    private final NetworkReachability networkReachability = new NetworkReachability();
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

    public HTTPUtil() {
        Main.getReloadManager().register(this);
        reloadConfig();
        sched.scheduleAtFixedRate(this::checkReachability, 0, 1, TimeUnit.HOURS);
    }

    private void checkReachability() {
        var client = newBuilder()
                .followSslRedirects(true)
                .followRedirects(true)
                .callTimeout(10, TimeUnit.SECONDS).build();
        Request cnNetworkCheck = new Request.Builder()
                .url("https://www.qq.com/")
                .head()
                .build();
        try (Response response = client.newCall(cnNetworkCheck).execute()) {
            networkReachability.setAccessToChinaNetwork(response.isSuccessful());
        } catch (IOException e) {
            networkReachability.setAccessToChinaNetwork(false);
        }

        Request globalNetworkCheck = new Request.Builder()
                .url("https://www.google.com/generate_204")
                .head()
                .build();
        try (Response response = client.newCall(globalNetworkCheck).execute()) {
            networkReachability.setAccessToGlobalNetwork(response.isSuccessful());
        } catch (IOException e) {
            networkReachability.setAccessToGlobalNetwork(false);
        }
    }


    @Override
    public ReloadResult reloadModule() {
        reloadConfig();
        return new ReloadResult(ReloadStatus.SUCCESS, "", null);
    }

    private void reloadConfig() {
        this.proxyType = switch (Main.getMainConfig().getInt("proxy.setting", 0)) {
            case 1 -> Proxy.Type.HTTP;
            case 2 -> Proxy.Type.SOCKS;
            default -> Proxy.Type.DIRECT;
        };
        this.proxyHost = Main.getMainConfig().getString("proxy.host", "127.0.0.1");
        this.proxyPort = Main.getMainConfig().getInt("proxy.port", 7890);
        proxyBypasses.clear();
        for (String proxy : Main.getMainConfig().getString("proxy.non-proxy-hosts", "").split("\\|")) {
            if (!proxy.isEmpty()) {
                try {
                    proxyBypasses.add(Pattern.compile(proxy.replace("*", ".*").replace("?", ".?")));
                } catch (Exception e) {
                    log.error("Invalid proxy bypass pattern: {}", proxy, e);
                }
            }
        }
        if (proxyType == Proxy.Type.DIRECT) {
            this.proxyInstance = Proxy.NO_PROXY;
        } else {
            this.proxyInstance = new Proxy(proxyType, InetSocketAddress.createUnresolved(proxyHost, proxyPort));
        }
    }

    public Proxy.Type getProxyType() {
        return proxyType;
    }

    @SneakyThrows
    public OkHttpClient.Builder disableSSLVerify(OkHttpClient.Builder builder, boolean apply) {
        if (!apply) return builder;
        return builder.sslSocketFactory(getIgnoreInitedSslContext().getSocketFactory(), IGNORE_SSL_TRUST_MANAGER_X509)
                .hostnameVerifier(getIgnoreSslHostnameVerifier());
    }

    public OkHttpClient.Builder addProgressTracker(OkHttpClient.Builder builder) {
        return addProgressTracker(builder, progressListener);
    }

    public OkHttpClient.Builder addProgressTracker(OkHttpClient.Builder builder, ProgressListener customProgressListener) {
        return builder.addNetworkInterceptor(chain -> {
            Response originalResponse = chain.proceed(chain.request());
            return originalResponse.newBuilder()
                    .body(new ProgressResponseBody(originalResponse.body(), customProgressListener))
                    .build();
        });
    }

    public static int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }

    public OkHttpClient.Builder newBuilder() {
        var okHttpBuilder = new OkHttpClient.Builder()
                .dispatcher(new Dispatcher(Executors.newVirtualThreadPerTaskExecutor()))
                .connectTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .followSslRedirects(true)
                .fastFallback(true)
                .retryOnConnectionFailure(true)
                .cookieJar(new PBHCookieJar())
                .proxySelector(new ProxySelector() {
                    @Override
                    public List<Proxy> select(URI uri) {
                        var host = uri.getHost();
                        for (Pattern proxyBypass : proxyBypasses) {
                            if (proxyBypass.matcher(host).matches()) {
                                log.debug("Direct route for host: {}, matched pattern: {}", host, proxyBypass.pattern());
                                return List.of(Proxy.NO_PROXY);
                            }
                        }
                        if (InetAddresses.isInetAddress(host)) {
                            if (IPAddressUtil.getIPAddress(host).isLocal()) {
                                log.debug("Direct route for host: {}, local IP", host);
                                return List.of(Proxy.NO_PROXY);
                            }
                        }
                        log.debug("Route via proxyInstance: {}", host);
                        return List.of(proxyInstance);
                    }

                    @Override
                    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {

                    }
                })
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder()
                            .header("User-Agent", Main.getUserAgent());
                    return chain.proceed(requestBuilder.build());
                });
        if (ExternalSwitch.parseBoolean("pbh.http.logging", false)) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor(log::debug);
            logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);
            okHttpBuilder.addNetworkInterceptor(logging);
        }
        return okHttpBuilder;
    }

    /**
     * X509TrustManager instance which ignored SSL certification
     */
    public static final X509TrustManager IGNORE_SSL_TRUST_MANAGER_X509 = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{};
        }
    };

    /**
     * Get initialized SSLContext instance which ignored SSL certification
     *
     * @return
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    public static SSLContext getIgnoreInitedSslContext() throws NoSuchAlgorithmException, KeyManagementException {
        var sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, new TrustManager[]{IGNORE_SSL_TRUST_MANAGER_X509}, new SecureRandom());
        return sslContext;
    }

    /**
     * Get HostnameVerifier which ignored SSL certification
     *
     * @return
     */
    public static HostnameVerifier getIgnoreSslHostnameVerifier() {
        return (arg0, arg1) -> true;
    }

    private static class ProgressResponseBody extends ResponseBody {
        private final ResponseBody responseBody;
        private final ProgressListener progressListener;
        private BufferedSource bufferedSource;

        ProgressResponseBody(ResponseBody responseBody, ProgressListener progressListener) {
            this.responseBody = responseBody;
            this.progressListener = progressListener;
        }

        @Override
        public MediaType contentType() {
            return responseBody.contentType();
        }

        @Override
        public long contentLength() {
            return responseBody.contentLength();
        }

        @Override
        public @NotNull BufferedSource source() {
            if (bufferedSource == null) {
                bufferedSource = Okio.buffer(source(responseBody.source()));
            }
            return bufferedSource;
        }

        private Source source(Source source) {
            return new ForwardingSource(source) {
                long totalBytesRead = 0L;

                @Override
                public long read(@NotNull Buffer sink, long byteCount) throws IOException {
                    long bytesRead = super.read(sink, byteCount);
                    // read() returns the number of bytes read, or -1 if this source is exhausted.
                    totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                    progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1);
                    return bytesRead;
                }
            };
        }
    }


    public abstract static class ProgressListener {
        private long lastUpdateAt = 0;
        private long lastBytesRead = 0L;

        void update(long bytesRead, long contentLength, boolean done) {
            if (System.currentTimeMillis() - lastUpdateAt < 5000 && (bytesRead - lastBytesRead < 1024 * 1024) && !done) {
                // 如果距离上次更新小于 5 秒则不更新且且读取的字节数小于 1MB且不是完成状态，则不更新
                return;
            }
            this.lastUpdateAt = System.currentTimeMillis();
            this.lastBytesRead = bytesRead;
            update0(bytesRead, contentLength, done);
        }

        abstract void update0(long bytesRead, long contentLength, boolean done);
    }

    final ProgressListener progressListener = new ProgressListener() {
        @Override
        void update0(long bytesRead, long contentLength, boolean done) {
            if (done) {
                log.info(tlUI(Lang.DOWNLOAD_COMPLETED, bytesRead));
            } else {
                if (contentLength != -1) {
                    log.info(tlUI(Lang.DOWNLOAD_PROGRESS_DETERMINED, bytesRead, contentLength, String.format("%.2f", ((100.0f * bytesRead) / contentLength))));
                } else {
                    log.info(tlUI(Lang.DOWNLOAD_PROGRESS, bytesRead));
                }
            }
        }
    };

    public static class PBHCookieJar implements CookieJar {
        private final Map<String, List<Cookie>> cookieStore = new ConcurrentHashMap<>();

        @Override
        public void saveFromResponse(HttpUrl httpUrl, @NotNull List<Cookie> list) {
            cookieStore.put(httpUrl.host(), list);
        }

        @Override
        public @NotNull List<Cookie> loadForRequest(HttpUrl httpUrl) {
            List<Cookie> cookies = cookieStore.get(httpUrl.host());
            return cookies != null ? cookies : new ArrayList<>();
        }
    }

    @NotNull
    public NetworkReachability getNetworkReachability() {
        return networkReachability;
    }

    @Data
    public static class NetworkReachability {
        private Boolean accessToChinaNetwork;
        private Boolean accessToGlobalNetwork; // if access to global network is false then we must use high-cost CDN for service
    }
}
