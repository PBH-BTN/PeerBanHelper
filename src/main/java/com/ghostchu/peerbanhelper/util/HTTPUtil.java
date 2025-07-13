package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.ProxySelector;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public final class HTTPUtil {
    @Getter
    private static SSLContext ignoreSslContext;
    @Autowired(required = false)
    private ProxySelector proxySelector;

    public HTTPUtil() {

    }

    @SneakyThrows
    public OkHttpClient.Builder disableSSLVerify(OkHttpClient.Builder builder, boolean apply) {
        if(!apply) return  builder;
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

    public OkHttpClient.Builder newBuilder() {
        var okHttpBuilder = new OkHttpClient.Builder()
                .dispatcher(new Dispatcher(Executors.newVirtualThreadPerTaskExecutor()))
                .connectTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .fastFallback(true)
                .retryOnConnectionFailure(true)
                .cookieJar(new PBHCookieJar())
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder()
                            .header("User-Agent", Main.getUserAgent());
                    return chain.proceed(requestBuilder.build());
                });
        if (proxySelector != null) {
            okHttpBuilder.proxySelector(proxySelector);
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


    public interface ProgressListener {
        void update(long bytesRead, long contentLength, boolean done);
    }

    final ProgressListener progressListener = (bytesRead, contentLength, done) -> {
        if (done) {
            log.info(tlUI(Lang.DOWNLOAD_COMPLETED, bytesRead));
        } else {
            if (contentLength != -1) {
                log.info(tlUI(Lang.DOWNLOAD_PROGRESS_DETERMINED, bytesRead, contentLength, String.format("%.2f", ((100.0f * bytesRead) / contentLength))));
            } else {
                log.info(tlUI(Lang.DOWNLOAD_PROGRESS, bytesRead));
            }
        }
    };

    public static class PBHCookieJar implements CookieJar {
        private final Map<String, List<Cookie>> cookieStore = new HashMap<>();

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
}
