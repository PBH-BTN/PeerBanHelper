package sockslib.common;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The class <code>SSLConfigurationBuilder</code> is a builder to build {@link SSLConfiguration}
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Oct 10,2015 11:13 AM
 */
public class SSLConfigurationBuilder {

    private String keyStorePath;
    private String keyStorePassword;
    private String trustKeyStorePath;
    private String trustKeyStorePassword;
    private boolean clientAuth = false;
    private String keyStoreType = "JKS";
    private String trustKeyStoreType = "JKS";

    private SSLConfigurationBuilder() {
    }

    public static SSLConfigurationBuilder newBuilder() {
        return new SSLConfigurationBuilder();
    }

    public SSLConfigurationBuilder setKeyStorePath(String keyStorePath) {
        this.keyStorePath = checkNotNull(keyStorePath);
        return this;
    }

    public SSLConfigurationBuilder setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = checkNotNull(keyStorePassword);
        return this;
    }

    public SSLConfigurationBuilder setTrustKeyStorePath(String trustKeyStorePath) {
        this.trustKeyStorePath = checkNotNull(trustKeyStorePath);
        return this;
    }

    public SSLConfigurationBuilder setTrustKeyStorePassword(String trustKeyStorePassword) {
        this.trustKeyStorePassword = checkNotNull(trustKeyStorePassword);
        return this;
    }

    public void setClientAuth(boolean clientAuth) {
        this.clientAuth = clientAuth;
    }

    public SSLConfigurationBuilder setKeyStoreType(String keyStoreType) {
        this.keyStoreType = checkNotNull(keyStoreType);
        return this;
    }

    public SSLConfigurationBuilder useKeystoreAsTrustKeyStore() {
        this.trustKeyStorePath = keyStorePath;
        this.trustKeyStorePassword = keyStorePassword;
        this.trustKeyStoreType = keyStoreType;
        return this;
    }

    public SSLConfigurationBuilder useTrustKeyStoreAsKeyStore() {
        this.keyStorePath = trustKeyStorePath;
        this.keyStorePassword = trustKeyStorePassword;
        this.keyStoreType = trustKeyStoreType;
        return this;
    }

    public SSLConfigurationBuilder setTrustKeyStoreType(String trustKeyStoreType) {
        this.trustKeyStoreType = checkNotNull(trustKeyStoreType);
        return this;
    }

    public SSLConfiguration build() {
        KeyStoreInfo keyStore = null;
        if (keyStorePath != null) {
            keyStore = new KeyStoreInfo(keyStorePath, keyStorePassword, keyStoreType);
        }
        KeyStoreInfo trustKeyStore = null;
        if (trustKeyStorePath != null) {
            trustKeyStore = new KeyStoreInfo(trustKeyStorePath, trustKeyStorePassword, trustKeyStoreType);
        }
        return new SSLConfiguration(keyStore, trustKeyStore, clientAuth);
    }
}
