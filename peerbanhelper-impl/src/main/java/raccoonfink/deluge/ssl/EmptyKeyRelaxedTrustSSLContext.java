package raccoonfink.deluge.ssl;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public final class EmptyKeyRelaxedTrustSSLContext extends SSLContextSpi {
    public static final String ALGORITHM = "EmptyKeyRelaxedTrust";

    private final SSLContext m_delegate;

    public EmptyKeyRelaxedTrustSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext customContext = null;

        // Use a blank list of key managers so no SSL keys will be available
        KeyManager[] keyManager = null;
        TrustManager[] trustManagers = {
                new X509TrustManager() {
                    public void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
                        // Perform no checks
                    }

                    public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
                        // Perform no checks
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }
        };
        customContext = SSLContext.getInstance("SSL");
        customContext.init(keyManager, trustManagers, new SecureRandom());

        m_delegate = customContext;
    }

    @Override
    protected SSLEngine engineCreateSSLEngine() {
        return m_delegate.createSSLEngine();
    }

    @Override
    protected SSLEngine engineCreateSSLEngine(String arg0, int arg1) {
        return m_delegate.createSSLEngine(arg0, arg1);
    }

    @Override
    protected SSLSessionContext engineGetClientSessionContext() {
        return m_delegate.getClientSessionContext();
    }

    @Override
    protected SSLSessionContext engineGetServerSessionContext() {
        return m_delegate.getServerSessionContext();
    }

    @Override
    protected SSLServerSocketFactory engineGetServerSocketFactory() {
        return m_delegate.getServerSocketFactory();
    }

    @Override
    protected javax.net.ssl.SSLSocketFactory engineGetSocketFactory() {
        return m_delegate.getSocketFactory();
    }

    @Override
    protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom arg2) throws KeyManagementException {
        // Don't do anything, we've already initialized everything in the constructor
    }
}