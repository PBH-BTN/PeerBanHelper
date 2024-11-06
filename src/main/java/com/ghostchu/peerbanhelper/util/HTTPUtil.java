package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.text.Lang;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.ProgressTracker;
import com.github.mizosoft.methanol.WritableBodyPublisher;
import com.google.common.io.ByteStreams;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class HTTPUtil {
    private static final int MAX_RESEND = 5;
    private static final CookieManager cookieManager = new CookieManager();
    private static final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    @Getter
    private static SSLContext ignoreSslContext;
    private static final ProgressTracker tracker = ProgressTracker.newBuilder()
            .bytesTransferredThreshold(60 * 1024) // 60 kB
            .timePassedThreshold(Duration.of(3, ChronoUnit.SECONDS))
            .build();

    static {
        TrustManager trustManager = new X509ExtendedTrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            }
        };
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, new SecureRandom());
            ignoreSslContext = sslContext;
        } catch (Exception e) {
            log.error(tlUI(Lang.MODULE_AP_SSL_CONTEXT_FAILURE), e);
        }
    }

    public static HttpClient getHttpClient(boolean ignoreSSL, ProxySelector proxySelector) {
        Methanol.Builder builder = Methanol
                .newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .cookieHandler(cookieManager);
        if (ignoreSSL) {
            builder.sslContext(ignoreSslContext);
        }
        if (proxySelector != null) {
            builder.proxy(proxySelector);
        }
        return builder.build();
    }

    public static WritableBodyPublisher gzipBody(byte[] bytes) {
        WritableBodyPublisher requestBody = WritableBodyPublisher.create();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(requestBody.outputStream());
             InputStream in = new ByteArrayInputStream(bytes)) {
            ByteStreams.copy(in, gzipOut);
        } catch (IOException ioe) {
            requestBody.closeExceptionally(ioe);
            log.error("Failed to compress request body", ioe);
        }
        return requestBody;
    }

    public static WritableBodyPublisher gzipBody(InputStream is) {
        WritableBodyPublisher requestBody = WritableBodyPublisher.create();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(requestBody.outputStream())) {
            ByteStreams.copy(is, gzipOut);
        } catch (IOException ioe) {
            requestBody.closeExceptionally(ioe);
            log.error("Failed to compress request body", ioe);
        }
        return requestBody;
    }

    public static void onProgress(ProgressTracker.Progress progress) {
        if (progress.determinate()) { // Overall progress can be measured
            var percent = 100 * progress.value();
            log.info(tlUI(Lang.DOWNLOAD_PROGRESS_DETERMINED, progress.totalBytesTransferred(), progress.contentLength(), String.format("%.2f", percent)));
        } else {
            log.info(tlUI(Lang.DOWNLOAD_PROGRESS, progress.totalBytesTransferred()));
        }
        if (progress.done()) {
            log.info(tlUI(Lang.DOWNLOAD_COMPLETED, progress.totalBytesTransferred()));
        }
    }

    public static <T> CompletableFuture<HttpResponse<T>> nonRetryableSend(HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
        return client.sendAsync(request, bodyHandler)
                .handleAsync((r, t) -> tryResend(client, request, bodyHandler, MAX_RESEND, r, t), executor)
                .thenCompose(Function.identity());

    }

    public static <T> CompletableFuture<HttpResponse<T>> retryableSend(HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
        return client.sendAsync(request, bodyHandler)
                .handleAsync((r, t) -> tryResend(client, request, bodyHandler, 1, r, t), executor)
                .thenCompose(Function.identity());

    }

    public static <T> CompletableFuture<HttpResponse<T>> retryableSendProgressTracking(HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
        bodyHandler = tracker.tracking(bodyHandler, HTTPUtil::onProgress);
        HttpResponse.BodyHandler<T> finalBodyHandler = bodyHandler;
        return client.sendAsync(request, bodyHandler)
                .handleAsync((r, t) -> tryResend(client, request, finalBodyHandler, 1, r, t), executor)
                .thenCompose(Function.identity());

    }


    public static boolean shouldRetry(HttpResponse<?> r, Throwable t, int count) {
        if (count >= MAX_RESEND) {
            return false;
        }
        if (r != null) {
            return r.statusCode() == 500
                   || r.statusCode() == 502
                   || r.statusCode() == 503
                   || r.statusCode() == 504;
        }
        return false;
    }

    public static <T> CompletableFuture<HttpResponse<T>> tryResend(HttpClient client, HttpRequest request,
                                                                   HttpResponse.BodyHandler<T> handler, int count,
                                                                   HttpResponse<T> resp, Throwable t) {
        if (shouldRetry(resp, t, count)) {
            if (resp == null) {
                log.warn("Request to {} failed, retry {}/{}: {}", request.uri().toString(), count, MAX_RESEND, t.getClass().getName() + ": " + t.getMessage());
            } else {
                log.warn("Request to {} failed, retry {}/{}: {} ", request.uri().toString(), count, MAX_RESEND, resp.statusCode() + " - " + resp.body());
            }
            return client.sendAsync(request, handler)
                    .handleAsync((r, x) -> tryResend(client, request, handler, count + 1, r, x), executor)
                    .thenCompose(Function.identity());
        } else if (t != null) {
            return CompletableFuture.failedFuture(t);
        } else {
            return CompletableFuture.completedFuture(resp);
        }
    }

}
