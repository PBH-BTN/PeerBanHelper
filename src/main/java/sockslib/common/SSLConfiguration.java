/*
 * Copyright 2015-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package sockslib.common;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.utils.PathUtil;

import javax.annotation.Nullable;
import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.security.KeyStore;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The class <code>SSLConfiguration</code> represents a configuration of SSL.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date May 17, 2015 6:52:52 PM
 */
public class SSLConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(SSLConfiguration.class);

    private KeyStoreInfo keyStoreInfo;
    private KeyStoreInfo trustKeyStoreInfo;
    private boolean needClientAuth = false;

    public SSLConfiguration(KeyStoreInfo keyStoreInfo, KeyStoreInfo trustKeyStoreInfo) {
        this(keyStoreInfo, trustKeyStoreInfo, false);
    }

    public SSLConfiguration(@Nullable KeyStoreInfo keyStoreInfo, @Nullable KeyStoreInfo
            trustKeyStoreInfo, boolean clientAuth) {
        this.keyStoreInfo = keyStoreInfo;
        this.trustKeyStoreInfo = trustKeyStoreInfo;
        this.needClientAuth = clientAuth;
    }

    /**
     * Creates a {@link SSLConfiguration} instance with a string.<br>
     * The string should format as: <br>
     * <pre>
     * KEYS_TORE_PATH,KEY_STORE_PASSWORD,TRUST_KEY_STORE_PATH,TRUST_KEY_STORE_PASSWORD,CLIENT_AUTH
     * </pre>
     *
     * @param configuration configuration as a string.
     * @return the instance of {@link SSLConfiguration}.
     */
    public static SSLConfiguration parse(String configuration) {
        String[] strings = configuration.split(",");
        if (strings.length == 2) {
            KeyStoreInfo keyStoreInfo = new KeyStoreInfo(strings[0], strings[1]);
            return new SSLConfiguration(keyStoreInfo, null);
        } else if (strings.length == 4) {
            KeyStoreInfo keyStoreInfo = new KeyStoreInfo(strings[0], strings[1]);
            KeyStoreInfo trustKeyStoreInfo = new KeyStoreInfo(strings[2], strings[3]);
            return new SSLConfiguration(keyStoreInfo, trustKeyStoreInfo);
        } else if (strings.length == 5) {
            KeyStoreInfo keyStoreInfo = new KeyStoreInfo(strings[0], strings[1]);
            KeyStoreInfo trustKeyStoreInfo = new KeyStoreInfo(strings[2], strings[3]);
            return new SSLConfiguration(keyStoreInfo, trustKeyStoreInfo, strings[4].equals("true"));
        }
        return null;
    }

    public static SSLConfiguration load(String filePath) throws FileNotFoundException, IOException {
        checkNotNull(filePath, "Argument [filePath] may not be null");
        logger.debug("load SSL configuration file:{}", filePath);
        KeyStoreInfo keyStoreInfo = null;
        KeyStoreInfo trustKeyStoreInfo = null;

        Properties properties = new Properties();
        properties.load(new FileInputStream(filePath));

        String keystorePath = PathUtil.getAbstractPath(properties.getProperty("ssl.keystore.location"));
        String password = properties.getProperty("ssl.keystore.password");
        String type = properties.getProperty("ssl.keystore.type", "JSK");
        String trustKeystorePath =
                PathUtil.getAbstractPath(properties.getProperty("ssl.trustStore.location"));
        String trustPassword = properties.getProperty("ssl.trustStore.password");
        String trustType = properties.getProperty("ssl.trustStore.type", "JSK");

        if (!Strings.isNullOrEmpty(keystorePath)) {
            keyStoreInfo = new KeyStoreInfo(keystorePath, password, type);
        }
        if (!Strings.isNullOrEmpty(trustKeystorePath)) {
            trustKeyStoreInfo = new KeyStoreInfo(trustKeystorePath, trustPassword, trustType);
        }
        String clientAuthValue = properties.getProperty("ssl.client.auth", "false");
        boolean clientAuth = false;
        if (clientAuthValue.equalsIgnoreCase("true")) {
            clientAuth = true;
        }

        return new SSLConfiguration(keyStoreInfo, trustKeyStoreInfo, clientAuth);
    }

    public static SSLConfiguration loadClassPath(String filePath) throws FileNotFoundException,
            IOException {
        checkNotNull(filePath, "Argument [filePath] may not be null");
        if (!filePath.startsWith(File.separator)) {
            filePath = File.separator + filePath;
        }
        URL url = SSLConfiguration.class.getResource(filePath);
        if (url == null) {
            throw new FileNotFoundException("classpath:" + filePath);
        }
        String path = url.getPath();
        return load(path);
    }

    public SSLSocketFactory getSSLSocketFactory() throws SSLConfigurationException {
        checkNotNull(trustKeyStoreInfo, "trustKeyStoreInfo may not be null");
        KeyStore keyStore = null;
        KeyStore trustKeyStore = null;
        try {
            SSLContext context = SSLContext.getInstance("SSL");
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustKeyStore = KeyStore.getInstance(trustKeyStoreInfo.getType());
            trustKeyStore.load(new FileInputStream(trustKeyStoreInfo.getKeyStorePath()),
                    trustKeyStoreInfo.getPassword().toCharArray());
            trustManagerFactory.init(trustKeyStore);

            if (keyStoreInfo != null && keyStoreInfo.getKeyStorePath() != null) {
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
                keyStore = KeyStore.getInstance(keyStoreInfo.getType());
                keyStore.load(new FileInputStream(keyStoreInfo.getKeyStorePath()), keyStoreInfo
                        .getPassword().toCharArray());
                keyManagerFactory.init(keyStore, keyStoreInfo.getPassword().toCharArray());

                context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(),
                        null);
            } else {
                context.init(null, trustManagerFactory.getTrustManagers(), null);
            }

            if (keyStore != null) {
                logger.info("SSL: Key store:{}", keyStoreInfo.getKeyStorePath());
            }
            logger.info("SSL: Trust key store:{}", trustKeyStoreInfo.getKeyStorePath());
            return context.getSocketFactory();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new SSLConfigurationException(e.getMessage());
        }
    }

    public SSLServerSocketFactory getSSLServerSocketFactory() throws SSLConfigurationException {
        checkNotNull(keyStoreInfo, "keyStoreInfo may not be null");
        String KEY_STORE_PASSWORD = getKeyStoreInfo().getPassword();
        String KEY_STORE_PATH = getKeyStoreInfo().getKeyStorePath();
        KeyStore keyStore = null;
        KeyStore trustKeyStore = null;

        try {
            SSLContext ctx = SSLContext.getInstance("SSL");
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(KEY_STORE_PATH), KEY_STORE_PASSWORD.toCharArray());
            keyManagerFactory.init(keyStore, KEY_STORE_PASSWORD.toCharArray());

            if (needClientAuth && trustKeyStoreInfo != null) {
                String TRUST_KEY_STORE_PATH = getTrustKeyStoreInfo().getKeyStorePath();
                String TRUST_KEY_STORE_PASSWORD = getTrustKeyStoreInfo().getPassword();
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
                trustKeyStore = KeyStore.getInstance("JKS");
                trustKeyStore.load(new FileInputStream(TRUST_KEY_STORE_PATH), TRUST_KEY_STORE_PASSWORD
                        .toCharArray());
                trustManagerFactory.init(trustKeyStore);
                ctx.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
            } else {
                ctx.init(keyManagerFactory.getKeyManagers(), null, null);
            }

            logger.info("SSL: Key store:{}", keyStoreInfo.getKeyStorePath());
            if (trustKeyStore != null) {
                logger.info("SSL: Trust key store:{}", trustKeyStoreInfo.getKeyStorePath());
            }
            logger.info("SSL: Client authentication:{}", needClientAuth);
            ;
            return ctx.getServerSocketFactory();
        } catch (Exception e) {
            throw new SSLConfigurationException(e.getMessage());
        }

    }

    /**
     * Returns the keyStoreInfo.
     *
     * @return the keyStoreInfo
     */
    public KeyStoreInfo getKeyStoreInfo() {
        return keyStoreInfo;
    }

    /**
     * @param keyStoreInfo the keyStoreInfo to set
     */
    public void setKeyStoreInfo(KeyStoreInfo keyStoreInfo) {
        this.keyStoreInfo = keyStoreInfo;
    }

    /**
     * @return the trustKeyStoreInfo
     */
    public KeyStoreInfo getTrustKeyStoreInfo() {
        return trustKeyStoreInfo;
    }

    /**
     * @param trustKeyStoreInfo the trustKeyStoreInfo to set
     */
    public void setTrustKeyStoreInfo(KeyStoreInfo trustKeyStoreInfo) {
        this.trustKeyStoreInfo = trustKeyStoreInfo;
    }

    /**
     * Returns the needClientAuth.
     *
     * @return the needClientAuth
     */
    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    /**
     * @param needClientAuth the needClientAuth to set
     */
    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }

}
