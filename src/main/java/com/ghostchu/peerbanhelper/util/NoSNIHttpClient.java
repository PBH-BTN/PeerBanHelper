package com.ghostchu.peerbanhelper.util;

import lombok.SneakyThrows;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class NoSNIHttpClient {

    public static void main(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
        // 创建一个自定义的 SSLContext，禁用 SNI
        SSLContext sslContext = createSSLContextWithoutSNI();

        // 创建 HttpClient，并将自定义的 SSLContext 应用于它
        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();

        // 创建 HTTP 请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://example.com"))
                .GET()
                .build();

        // 发送请求并获取响应
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Response status code: " + response.statusCode());
        System.out.println("Response body: " + response.body());
    }

    private static SSLContext createSSLContextWithoutSNI() throws NoSuchAlgorithmException, KeyManagementException {
        // 创建一个标准的 SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);

        // 获取默认的 SSLSocketFactory
        SSLSocketFactory defaultSslSocketFactory = sslContext.getSocketFactory();

        // 创建一个自定义的 SSLSocketFactory 禁用 SNI
        SSLSocketFactory noSniSslSocketFactory = new NoSNISSLSocketFactory(defaultSslSocketFactory);

        // 创建一个新的 SSLContext 并设置自定义的 SSLSocketFactory
        SSLContext noSniSslContext = SSLContext.getInstance("TLS");
        noSniSslContext.init(null, null, null);
        noSniSslContext = new CustomSSLContext(noSniSslSocketFactory);

        return noSniSslContext;
    }

    private static class NoSNISSLSocketFactory extends SSLSocketFactory {

        private final SSLSocketFactory delegate;

        NoSNISSLSocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public SSLSocket createSocket() throws IOException {
            return disableSNI((SSLSocket) delegate.createSocket());
        }

        @Override
        public SSLSocket createSocket(String host, int port) throws IOException {
            return disableSNI((SSLSocket) delegate.createSocket(host, port));
        }

        @Override
        public SSLSocket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws IOException {
            return disableSNI((SSLSocket) delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public SSLSocket createSocket(java.net.InetAddress host, int port) throws IOException {
            return disableSNI((SSLSocket) delegate.createSocket(host, port));
        }

        @Override
        public SSLSocket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws IOException {
            return disableSNI((SSLSocket) delegate.createSocket(address, port, localAddress, localPort));
        }

        @Override
        public SSLSocket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws IOException {
            return disableSNI((SSLSocket) delegate.createSocket(s, host, port, autoClose));
        }

        private SSLSocket disableSNI(SSLSocket socket) {
            SSLParameters sslParameters = socket.getSSLParameters();
            sslParameters.setServerNames(null);
            socket.setSSLParameters(sslParameters);
            return socket;
        }
    }

    private static class CustomSSLContext extends SSLContext {

        private final SSLSocketFactory socketFactory;

        CustomSSLContext(SSLSocketFactory socketFactory) throws NoSuchAlgorithmException {
            super(new CustomSSLContextSpi(socketFactory), SSLContext.getDefault().getProvider(), "TLS");
            this.socketFactory = socketFactory;
        }

    }

    private static class CustomSSLContextSpi extends SSLContextSpi {

        private final SSLSocketFactory socketFactory;

        CustomSSLContextSpi(SSLSocketFactory socketFactory) {
            this.socketFactory = socketFactory;
        }

        @Override
        protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr) throws KeyManagementException {

        }

        @Override
        protected SSLSocketFactory engineGetSocketFactory() {
            return socketFactory;
        }

        @Override
        protected SSLServerSocketFactory engineGetServerSocketFactory() {
            throw new UnsupportedOperationException("SSLServerSocketFactory not supported.");
        }

        @SneakyThrows
        @Override
        protected SSLEngine engineCreateSSLEngine() {
            return SSLContext.getDefault().createSSLEngine();
        }

        @SneakyThrows
        @Override
        protected SSLEngine engineCreateSSLEngine(String host, int port) {
            return SSLContext.getDefault().createSSLEngine(host, port);
        }

        @SneakyThrows
        @Override
        protected SSLSessionContext engineGetClientSessionContext() {
            return SSLContext.getDefault().getClientSessionContext();
        }

        @SneakyThrows
        @Override
        protected SSLSessionContext engineGetServerSessionContext() {
            return SSLContext.getDefault().getServerSessionContext();
        }
    }
}
