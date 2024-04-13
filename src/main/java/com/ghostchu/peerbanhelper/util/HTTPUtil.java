package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.text.Lang;
import com.github.mizosoft.methanol.Methanol;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.CookieManager;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
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
}
