package sockslib.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.client.SocksProxy;
import sockslib.common.SSLConfiguration;
import sockslib.common.methods.NoAuthenticationRequiredMethod;
import sockslib.common.methods.SocksMethod;
import sockslib.common.methods.UsernamePasswordMethod;
import sockslib.server.listener.PipeInitializer;
import sockslib.server.listener.SessionListener;
import sockslib.server.manager.MemoryBasedUserManager;
import sockslib.server.manager.UserManager;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The class <code>SocksServerBuilder</code> is a tool class to build an {@link SocksProxyServer}.
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Aug 27, 2015
 * @since JDK1.7
 */
public class SocksServerBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SocksServerBuilder.class);
    private static final int DEFAULT_PORT = 1080;

    private Class<? extends SocksHandler> socksHandlerClass;
    private Set<SocksMethod> socksMethods;
    private UserManager userManager;
    private SocksProxy proxy;
    private int timeout;
    private InetAddress bindAddr;
    private int bindPort = DEFAULT_PORT;
    private boolean daemon = false;
    private ExecutorService executorService;
    private SessionManager sessionManager = new BasicSessionManager();
    private SSLConfiguration sslConfiguration;
    private Map<String, SessionListener> sessionListeners = new HashMap<>();
    private PipeInitializer pipeInitializer;

    /**
     * Creates a <code>SocksServerBuilder</code> with a <code>Class<? extends {@link
     * SocksHandler}</code>
     * instance.
     *
     * @param socksHandlerClass <code>java.lang.Class<? extends {@link SocksHandler}</code> instance.
     */
    private SocksServerBuilder(Class<? extends SocksHandler> socksHandlerClass) {
        this.socksHandlerClass =
                checkNotNull(socksHandlerClass, "Argument [socksHandlerClass] may not be null");
        userManager = new MemoryBasedUserManager();
    }

    /**
     * Builds a {@link SocksProxyServer} which support SOCKS5 protocol.
     * This SOCKS5 server will accept all requests from clients with no authentication.
     *
     * @return Instance of {@link SocksProxyServer}.
     */
    public static SocksProxyServer buildAnonymousSocks5Server() {
        return buildAnonymousSocks5Server(DEFAULT_PORT);
    }

    /**
     * Builds a {@link SocksProxyServer} which support SOCKS5 protocol bind at a specified port.
     * This SOCKS5 server will accept all requests from clients with no authentication.
     *
     * @param bindPort The port that server listened.
     * @return Instance of {@link SocksProxyServer}.
     */
    public static SocksProxyServer buildAnonymousSocks5Server(int bindPort) {
        return newSocks5ServerBuilder().setSocksMethods(new NoAuthenticationRequiredMethod())
                .setBindPort(bindPort).build();
    }

    /**
     * Builds a SSL based {@link SocksProxyServer} with no authentication required.
     *
     * @param bindPort      The port that server listened.
     * @param configuration SSL configuration
     * @return Instance of {@link SocksProxyServer}
     */
    public static SocksProxyServer buildAnonymousSSLSocks5Server(int bindPort, SSLConfiguration
            configuration) {
        return newSocks5ServerBuilder().setSocksMethods(new NoAuthenticationRequiredMethod())
                .setBindPort(bindPort).useSSL(configuration).build();
    }

    public static SocksProxyServer buildAnonymousSSLSocks5Server(SSLConfiguration configuration) {
        return buildAnonymousSSLSocks5Server(DEFAULT_PORT, configuration);
    }

    /**
     * Creates a <code>SocksServerBuilder</code> instance with specified Class instance of {@link
     * SocksHandler}.
     *
     * @param socksHandlerClass Class instance of {@link SocksHandler}.
     * @return Instance of {@link SocksServerBuilder}.
     */
    public static SocksServerBuilder newBuilder(Class<? extends SocksHandler> socksHandlerClass) {
        checkNotNull(socksHandlerClass, "Argument [socksHandlerClass] may not be null");
        return new SocksServerBuilder(socksHandlerClass);
    }

    /**
     * Calls {@link #newBuilder(Class)}  with an argument <code>Socks5Handler.class</code>;
     *
     * @return Instance of {@link SocksServerBuilder}.
     */
    public static SocksServerBuilder newSocks5ServerBuilder() {
        return new SocksServerBuilder(Socks5Handler.class);
    }

    /**
     * Add {@link SocksMethod}.
     *
     * @param methods Instance of {@link SocksMethod}.
     * @return Instance of {@link SocksServerBuilder}.
     */
    public SocksServerBuilder addSocksMethods(SocksMethod... methods) {
        if (socksMethods == null) {
            socksMethods = new HashSet<>();
        }
        Collections.addAll(socksMethods, methods);
        return this;
    }

    /**
     * Set SOCKS methods that SOCKS server will support.
     *
     * @param methods Instance of {@link SocksMethod}.
     * @return Instance of {@link SocksServerBuilder}.
     */
    public SocksServerBuilder setSocksMethods(SocksMethod... methods) {
        if (socksMethods == null) {
            socksMethods = new HashSet<>();
        }
        Collections.addAll(socksMethods, methods);
        return this;
    }

    public SocksServerBuilder setPipeInitializer(PipeInitializer pipeInitializer) {
        this.pipeInitializer = pipeInitializer;
        return this;
    }

    public SocksServerBuilder setSocksMethods(Set<SocksMethod> methods) {
        socksMethods = checkNotNull(methods, "Argument [methods] may not be null");
        return this;
    }

    public SocksServerBuilder setUserManager(UserManager userManager) {
        this.userManager = checkNotNull(userManager, "Argument [userManager] may not be null");
        return this;
    }

    public SocksServerBuilder setProxy(SocksProxy proxy) {
        this.proxy = proxy;
        return this;
    }

    public SocksServerBuilder setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public SocksServerBuilder setTimeout(long timeout, TimeUnit timeUnit) {
        this.timeout = (int) timeUnit.toMillis(timeout);
        return this;
    }

    public SocksServerBuilder setBindAddr(InetAddress bindAddr) {
        this.bindAddr = bindAddr;
        return this;
    }

    public SocksServerBuilder setBindPort(int bindPort) {
        this.bindPort = bindPort;
        return this;
    }

    public SocksServerBuilder setExecutorService(ExecutorService executorService) {
        this.executorService = checkNotNull(executorService);
        return this;
    }

    public SocksServerBuilder setDaemon(boolean daemon) {
        this.daemon = daemon;
        return this;
    }

    /**
     * Sets {@link SessionManager}.
     *
     * @param sessionManager instance of {@link SessionManager}.
     * @return Instance of {@link SocksServerBuilder}.
     */
    public SocksServerBuilder setSessionManager(SessionManager sessionManager) {
        this.sessionManager = checkNotNull(sessionManager);
        return this;
    }

    /**
     * Add a {@link SessionListener}.
     *
     * @param name     name of {@link SessionListener}
     * @param listener instance of {@link SessionListener}.
     * @return Instance of {@link SocksServerBuilder}.
     */
    public SocksServerBuilder addSessionListener(String name, SessionListener listener) {
        sessionListeners.put(name, listener);
        return this;
    }

    /**
     * Sets server in SSL mode.
     *
     * @param sslConfiguration instance of {@link SSLConfiguration}.
     * @return Instance of {@link SocksServerBuilder}.
     */
    public SocksServerBuilder useSSL(SSLConfiguration sslConfiguration) {
        this.sslConfiguration = sslConfiguration;
        return this;
    }

    /**
     * Builds a {@link SocksProxyServer} instance.
     *
     * @return instance of {@link SocksProxyServer}.
     */
    public SocksProxyServer build() {
        SocksProxyServer proxyServer = null;
        if (sslConfiguration == null) {
            proxyServer = new BasicSocksProxyServer(socksHandlerClass);
        } else {
            proxyServer = new SSLSocksProxyServer(socksHandlerClass, sslConfiguration);
        }
        proxyServer.setTimeout(timeout);
        proxyServer.setBindAddr(bindAddr);
        proxyServer.setBindPort(bindPort);
        proxyServer.setDaemon(daemon);
        proxyServer.setSessionManager(sessionManager);
        proxyServer.setPipeInitializer(pipeInitializer);
        if (socksMethods == null) {
            socksMethods = new HashSet<>();
            socksMethods.add(new NoAuthenticationRequiredMethod());
        }
        SocksMethod[] methods = new SocksMethod[socksMethods.size()];
        int i = 0;
        for (SocksMethod method : socksMethods) {
            if (method instanceof UsernamePasswordMethod) {
                if (userManager == null) {
                    userManager = new MemoryBasedUserManager();
                }
                ((UsernamePasswordMethod) method).setAuthenticator(new UsernamePasswordAuthenticator
                        (userManager));
            }
            methods[i] = method;
            i++;
        }
        if (executorService != null) {
            proxyServer.setExecutorService(executorService);
        }

        proxyServer.setSupportMethods(methods);
        if (proxy != null) {
            proxyServer.setProxy(proxy);
        }
        for (String name : sessionListeners.keySet()) {
            proxyServer.getSessionManager().addSessionListener(name, sessionListeners.get(name));
        }
        return proxyServer;
    }
}
