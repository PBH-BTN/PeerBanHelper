package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.text.Lang;
import com.github.mizosoft.methanol.Methanol;
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
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class HTTPUtil {
    @Getter
    private static SSLContext ignoreSslContext;
    private static final CookieManager cookieManager = new CookieManager();
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
            log.warn(Lang.MODULE_AP_SSL_CONTEXT_FAILURE, e);
        }
    }

    public static HttpClient getHttpClient(boolean ignoreSSL, ProxySelector proxySelector){
        Methanol.Builder builder = Methanol
                .newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .cookieHandler(cookieManager);
        if(ignoreSSL){
            builder.sslContext(ignoreSslContext);
        }
        if(proxySelector != null){
            builder.proxy(proxySelector);
        }
        return builder.build();
    }

    public static WritableBodyPublisher gzipBody(byte[] bytes){
        WritableBodyPublisher requestBody = WritableBodyPublisher.create();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(requestBody.outputStream());
             InputStream in = new ByteArrayInputStream(bytes)) {
            ByteStreams.copy(in, gzipOut);
        } catch (IOException ioe) {
            requestBody.closeExceptionally(ioe);
            log.error("Failed to compress request body",ioe);
        }
        return requestBody;
    }
    public static WritableBodyPublisher gzipBody(InputStream is){
        WritableBodyPublisher requestBody = WritableBodyPublisher.create();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(requestBody.outputStream())) {
            ByteStreams.copy(is, gzipOut);
        } catch (IOException ioe) {
            requestBody.closeExceptionally(ioe);
            log.error("Failed to compress request body",ioe);
        }
        return requestBody;
    }
}
