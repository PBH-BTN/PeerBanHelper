package sockslib.quickstart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.client.SSLSocks5;
import sockslib.client.Socks5;
import sockslib.client.SocksProxy;
import sockslib.common.Credentials;
import sockslib.common.SSLConfiguration;
import sockslib.common.SSLConfigurationBuilder;
import sockslib.common.UsernamePasswordCredentials;
import sockslib.common.methods.NoAuthenticationRequiredMethod;
import sockslib.common.methods.UsernamePasswordMethod;
import sockslib.server.SocksProxyServer;
import sockslib.server.SocksServerBuilder;
import sockslib.server.listener.LoggingListener;
import sockslib.server.manager.MemoryBasedUserManager;
import sockslib.server.manager.User;
import sockslib.server.manager.UserManager;
import sockslib.utils.Arguments;
import sockslib.utils.Timer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;

/**
 * The class <code>Socks5Server</code> can create a simple Socks5 server.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Sep 24, 2015 11:25 AM
 */
public class Socks5Server {

    private static final Logger logger = LoggerFactory.getLogger(Socks5Server.class);
    private final String KEY_STORE_TYPE = "JKS";
    private SocksProxyServer server;

    /**
     * Run a SOCKS5 server. Same as @{@link #start(String[])}.
     *
     * @param args Some arguments.
     * @throws IOException If any I/O error occurred
     */
    public static void main(@Nullable String[] args) throws IOException {
        Timer.open();
        Socks5Server socks5Server = new Socks5Server();
        socks5Server.start(args);
    }

    /**
     * Start a SOCKS5 server with some options.
     *
     * @param args Arguments.Support "--h --help --port --auth"
     * @throws IOException If any I/O error occurred
     */
    public void start(@Nullable String[] args) throws IOException {
        Arguments arguments = new Arguments(args);
        SocksServerBuilder builder = null;

        if (arguments.hasArgsIn("-h", "--help")) {
            showHelp();
            return;
        }

        builder = SocksServerBuilder.newSocks5ServerBuilder();
        try {
            initPort(arguments, builder);
            initAuth(arguments, builder);
            initSSL(arguments, builder);
            initProxy(arguments, builder);
        } catch (IllegalArgumentException e) {
            return;
        }

        server = builder.build();
        final SocksProxyServer finalServer = server;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            finalServer.shutdown();
            logger.info("SOCKS5 sever shutdown");
        }));
        server.getSessionManager().addSessionListener("logger", new LoggingListener());
        server.start();
    }

    /**
     * Print help information.
     */
    public void showHelp() {
        System.out.println("Usage: [Options]");
        System.out.println("  -p, --port <val>             Server bind port");
        System.out.println("  -a, --auth <val>             Use username/password authentication");
        System.out.println("                               Example: --auth=admin:1234");
        System.out.println("                               --auth=admin:1234,root:1234");
        System.out.println("  -s, --ssl <val>              SSL configuration file path");
        System.out.println("  -l, --sslClientAuth          Authenticate client's certificate");
        System.out.println("  -P, --proxy <val>            Set server SOCKS5 proxy, <val> should be:");
        System.out.println("                               host:port:username:password or host:port");
        System.out.println("  -S, --proxySsl <val>         Proxy SSL configuration file path");
        System.out.println("  -k, --keystore <val>         Keystore location");
        System.out.println("  -w  --keystorePassword <val>");
        System.out.println("                               Password of keystore");
        System.out.println("  -t  --keystoreType <val>");
        System.out.println("                               Keystore type, default \"JKS\"");
        System.out.println(
                "  -K, --trustStore <val>    Trust keystore location. default same as " + "[--keystore]");
        System.out.println("  -W, --trustStorePassword <val>");
        System.out.println("                               Password of trusted keystore");
        System.out.println("  -T, --trustStoreType <val>");
        System.out.println("                               Trust keystore type, default \"JKS\"");
        System.out.println("  -pk <val>                    Proxy keystore path");
        System.out.println("  -pw <val>                    Password for keystore");
        System.out.println("  -pt <val>                    Proxy keystore type");
        System.out.println("  -h, --help                   Show help");
    }

    /**
     * Shutdown SOCKS5 server.
     */
    public void shutdown() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void initPort(Arguments arguments, SocksServerBuilder builder) throws IllegalArgumentException {
        int port = arguments.getIntValue(Arrays.asList("-p", "--port"), 1080);
        builder.setBindPort(port);
    }

    private void initAuth(Arguments arguments, SocksServerBuilder builder) throws IllegalArgumentException {
        String authValue = arguments.getValue(Arrays.asList("-a", "--auth"), null);
        if (authValue != null) {
            UserManager userManager = new MemoryBasedUserManager();
            for (String user : authValue.split(",")) {
                String[] userPassword = user.split(":");
                String username = userPassword[0];
                String password = userPassword[1];
                userManager.create(new User(username, password));
            }
            builder.setSocksMethods(new UsernamePasswordMethod()).setUserManager(userManager);
        } else {
            builder.setSocksMethods(new NoAuthenticationRequiredMethod());
        }
    }

    private void initSSL(Arguments arguments, SocksServerBuilder builder) throws IllegalArgumentException {
        String sslConfigValue = arguments.getValue(Arrays.asList("-s", "--ssl"), null);
        boolean clientAuth = arguments.hasArgsIn("-l", "--sslClientAuth");
        if (sslConfigValue != null) {
            try {
                builder.useSSL(SSLConfiguration.load(sslConfigValue));
            } catch (IOException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }
        String keyStorePath = arguments.getValue(Arrays.asList("-k", "--keystore"), null);
        String keyStorePassword = arguments.getValue(Arrays.asList("-w", "--keystorePassword"), null);
        String keyStoreType = arguments.getValue(Arrays.asList("-t", "--keystoreType"), KEY_STORE_TYPE);
        String trustKeyStorePath = arguments.getValue(Arrays.asList("-K", "--trustStore"), null);
        String trustKeyStorePassword =
                arguments.getValue(Arrays.asList("-W", "--trustStorePassword"), null);
        String trustKeyStoreType =
                arguments.getValue(Arrays.asList("-T", "--trustStoreType"), KEY_STORE_TYPE);
        if (keyStorePath != null) {
            if (keyStorePassword == null) {
                logger.error("Need password for keystore:{}", keyStorePath);
                throw new IllegalArgumentException();
            }
            SSLConfigurationBuilder sslConfigBuilder = SSLConfigurationBuilder.newBuilder();
            sslConfigBuilder.setKeyStorePath(keyStorePath).setKeyStorePassword(keyStorePassword)
                    .setKeyStoreType(keyStoreType).setClientAuth(clientAuth);
            if (clientAuth) {
                if (trustKeyStorePath != null) {
                    if (trustKeyStorePassword == null) {
                        logger.error("Need password for keystore:{}", trustKeyStorePath);
                        throw new IllegalArgumentException();
                    }
                    sslConfigBuilder.setTrustKeyStorePath(trustKeyStorePath).setTrustKeyStorePassword
                            (trustKeyStorePassword).setTrustKeyStoreType(trustKeyStoreType).setClientAuth(true);
                } else {// if trust keystore is null, use keystore as trust keystore
                    sslConfigBuilder.useKeystoreAsTrustKeyStore();
                }
            }
            builder.useSSL(sslConfigBuilder.build());
        }
    }

    private void initProxy(Arguments arguments, SocksServerBuilder builder) throws
            IllegalArgumentException, IOException {
        String proxyValue = arguments.getValue(Arrays.asList("-P", "--proxy"), null);
        String regex = "((\\w+):(\\w+)@)?([.\\w]+):(\\d+)";
        if (proxyValue != null) {
            if (proxyValue.matches(regex)) {
                SocksProxy proxy = null;
                String host = null;
                int port = 1080;
                Credentials credentials = null;
                String[] values = proxyValue.split("@");
                String[] address = null;
                String[] user = null;
                if (values.length == 1) {
                    address = values[0].split(":");
                } else {
                    user = values[0].split(":");
                    address = values[1].split(":");
                    credentials = new UsernamePasswordCredentials(user[0], user[1]);
                }
                host = address[0];
                port = Integer.parseInt(address[1]);

                String proxySslValue = arguments.getValue(Arrays.asList("-S", "--proxySsl"), null);
                if (proxySslValue != null) {
                    proxy =
                            new SSLSocks5(new InetSocketAddress(host, port), SSLConfiguration.load
                                    (proxySslValue));
                }
                SocksProxy tempProxy = initProxySSL(arguments, builder, new InetSocketAddress(host, port));
                if (tempProxy != null) {
                    proxy = tempProxy;
                }
                if (proxy == null) {
                    proxy = new Socks5(new InetSocketAddress(host, port));
                }
                if (credentials != null) {
                    proxy.setCredentials(credentials);
                }
                builder.setProxy(proxy);

            } else {
                logger.error("[-P] or [--proxy] value: [username:password@]host:port");
                throw new IllegalArgumentException();
            }
        }
    }

    private SocksProxy initProxySSL(Arguments arguments, SocksServerBuilder builder, SocketAddress
            address) throws IllegalArgumentException {
        String keystorePath = arguments.getValue("-pk", null);
        String keystorePassword = arguments.getValue("-pw", null);
        String keystoreType = arguments.getValue("-pt", KEY_STORE_TYPE);
        if (keystorePath != null) {
            if (keystorePassword == null) {
                logger.info("Need password for keystore:{}", keystorePath);
                throw new IllegalArgumentException();
            }
            SSLConfigurationBuilder sslConfigurationBuilder = SSLConfigurationBuilder.newBuilder();
            sslConfigurationBuilder.setKeyStorePath(keystorePath).setKeyStorePassword(keystorePassword)
                    .setKeyStoreType(keystoreType).useKeystoreAsTrustKeyStore();
            return new SSLSocks5(address, sslConfigurationBuilder.build());
        }
        return null;
    }

}
