/**
 * Copyright © 2023 JAdaptive Limited (support@jadaptive.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.sshtools.porter;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.xml.sax.SAXException;

/**
 * Very simple UPnP client implementation that just deals with discovery of
 * routers and mapping or un-mapping of ports.
 */
public final class UPnP {
    final static Logger LOG = System.getLogger("UPnP");

    private final static String[] MESSAGES = { "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
            "urn:schemas-upnp-org:service:WANIPConnection:1", "urn:schemas-upnp-org:service:WANPPPConnection:1" };

    /**
     * Build new {@link Discovery} instances, used to locate UPnP routers on the
     * network.
     */
    public final static class DiscoveryBuilder {

        private List<InetAddress> localAddresses = new ArrayList<>();
        private int soTimeout = 3000;
        private Optional<Consumer<Gateway>> onGateway = Optional.empty();
        private boolean shutdownHooks = true;

        /**
         * Set the socket timeout for discovery.
         * 
         * @param soTimeout socket timeout
         * @return this for chaining
         */
        public DiscoveryBuilder withSoTimeout(int soTimeout) {
            this.soTimeout = soTimeout;
            return this;
        }

        /**
         * Do not configure shutdown hooks that will unmap when the JVM terminates.
         * 
         * @return this for chaining
         */
        public DiscoveryBuilder withoutShutdownHooks() {
            this.shutdownHooks = false;
            return this;
        }

        /**
         * Set the addresses of the local interfaces to use to perform discovery. By
         * default these will be automatically discovered.
         * 
         * @param addresses address of local interfaces
         * @return this for chaining
         */
        public DiscoveryBuilder withLocalAddress(InetAddress... addresses) {
            return withLocalAddresses(Arrays.asList(addresses));

        }

        /**
         * Set the addresses of the local interfaces to use to perform discovery. By
         * default these will be automatically discovered.
         * 
         * @param addresses address of local interfaces
         * @return this for chaining
         */
        public DiscoveryBuilder withLocalAddresses(Collection<InetAddress> addresses) {
            localAddresses.clear();
            localAddresses.addAll(addresses);
            return this;
        }

        /**
         * Invoked when a new {@link Gateway} is discovered by a {@link Discovery}
         * instance.
         * 
         * @param onGateway consume
         * @return this for chaining
         */
        public DiscoveryBuilder onGateway(Consumer<Gateway> onGateway) {
            this.onGateway = Optional.of(onGateway);
            return this;
        }

        /**
         * Build a new {@link Discovery} instance.
         * 
         * @return discovery instance
         */
        public Discovery build() {
            return new Discovery(this);
        }
    }

    /**
     * Represents a single <strong>IGD</strong>, or Internet Gateway Device. This
     * will likely be a router.
     */
    public final static class Gateway {

        private InetAddress localIp;
        private InetAddress ip;

        private final String type;
        private final URI control;
        private final boolean shutdownHooks;
        private final Map<Integer, Thread> hooks = new HashMap<>();

        private Gateway(InetAddress localIp, InetAddress ip, URI location, boolean shutdownHooks) throws IOException {
            this.localIp = localIp;
            this.ip = ip;
            this.shutdownHooks = shutdownHooks;

            var document = loadDocument(location);

            String type = null;
            String control = null;

            var services = document.getElementsByTagName("service");
            for (int i = 0; i < services.getLength(); i++) {

                type = null;
                control = null;

                var service = services.item(i);
                var n = service.getChildNodes();

                for (int j = 0; j < n.getLength(); j++) {
                    Node x = n.item(j);
                    if (x.getNodeName().trim().equalsIgnoreCase("serviceType")) {
                        type = x.getFirstChild().getNodeValue().trim();
                    } else if (x.getNodeName().trim().equalsIgnoreCase("controlURL")) {
                        control = x.getFirstChild().getNodeValue().trim();
                    }
                }
                if (type == null || control == null) {
                    continue;
                }
                if (type.trim().toLowerCase().contains(":wanipconnection:")
                        || type.trim().toLowerCase().contains(":wanpppconnection:")) {
                    break;
                }
            }
            if (control == null || type == null) {
                throw new IOException("Missing detail.");
            }

            this.type = type;
            this.control = location.resolve(control);

            LOG.log(Level.DEBUG, "Descriptor {0}, control URL is {1}, service type is {2}, local IP is {3}", location,
                    this.control, type, localIp);
        }

        private Document loadDocument(URI location) throws IOException {
            if (LOG.isLoggable(Level.DEBUG)) {
                LOG.log(Level.DEBUG, "Trying {0}", location);
            }

            Document d;
            try {
                d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(createClient()
                        .send(HttpRequest.newBuilder().GET().uri(location).build(), BodyHandlers.ofInputStream())
                        .body());
            } catch (SAXException | ParserConfigurationException | InterruptedException e) {
                throw new IOException("Failed to configure parser.", e);
            }
            return d;
        }

        private String buildCmdContent(String cmd, Map<String, Object> parms) {

            var content = new StringBuilder();
            content.append("<?xml version=\"1.0\"?>\r\n");
            content.append(
                    "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" SOAP-ENV:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">");
            content.append("<SOAP-ENV:Body>");
            content.append("<m:");
            content.append(cmd);
            content.append(" xmlns:m=\"");
            content.append(type);
            content.append("\">");
            if(parms != null) {
	            for (var p : parms.entrySet()) {
	                content.append("<");
	                content.append(p.getKey());
	                content.append(">");
	                content.append(p.getValue());
	                content.append("</");
	                content.append(p.getKey());
	                content.append(">");
	            }
            }
            content.append("</m:");
            content.append(cmd);
            content.append("></SOAP-ENV:Body></SOAP-ENV:Envelope>");
            return content.toString();
        }

        private Map<String, String> cmd(String cmd, Map<String, Object> params) throws IOException {

            var request = HttpRequest.newBuilder()
                    .POST(BodyPublishers.ofByteArray(buildCmdContent(cmd, params).getBytes("UTF-8"))).uri(control)
                    .setHeader("User-Agent", "DFT").setHeader("Content-Type", "text/xml")
                    .setHeader("SOAPAction", String.format("\"%s#%s\"", type, cmd)).build();

            try {

                var response = createClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
                var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(response.body());
                var iterator = ((DocumentTraversal) document).createNodeIterator(document.getDocumentElement(),
                        NodeFilter.SHOW_ELEMENT, null, true);
                var results = new HashMap<String, String>();

                while (true) {
                    var n = iterator.nextNode();
                    if (n == null)
                        break;
                    try {
                        if (n.getFirstChild().getNodeType() == Node.TEXT_NODE) {
                            results.put(n.getNodeName(), n.getTextContent());
                        }
                    } catch (Throwable t) {
                    }
                }
                return results;
            } catch (SAXException | ParserConfigurationException | InterruptedException se) {
                throw new IOException("Failed to parse response.", se);
            }
        }

        private HttpClient createClient() {
            return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10))
                    .build();
        }

        /**
         * Get the address of this router.
         * 
         * @return address of router
         */
        public InetAddress ip() {
            return ip;
        }

        /**
         * Get the address of the local interface this device was discovered from.
         * 
         * @return local address where gateway was discovered
         */
        public InetAddress localIP() {
            return localIp;
        }

        @Override
        public int hashCode() {
            return Objects.hash(localIp, ip);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Gateway other = (Gateway) obj;
            return Objects.equals(localIp, other.localIp) && Objects.equals(ip, other.ip);
        }

        @Override
        public String toString() {
            return "Gateway [localIp=" + localIp + ", ip=" + ip + ", type=" + type + ", control=" + control + "]";
        }

        /**
         * Get the external address of this network, i.e. the address seen by everyone
         * else on the Internet. If the address is unknown or some other error occurs,
         * the return value will be empty.
         * 
         * @return external address
         */
        public Optional<String> externalIp() {
            try {
                Map<String, String> r = cmd("GetExternalIPAddress", null);
                return Optional.of(r.get("NewExternalIPAddress"));
            } catch (Throwable t) {
            	t.printStackTrace();
                return Optional.empty();
            }
        }

        /**
         * Map an external port number on the gateway for the specified protocol to the
         * same internal port number on the local address on this host..
         * 
         * @param port     port number
         * @param protocol protocol
         * @return mapped
         * @throws UncheckedIOException on serious error
         */
        public boolean map(int port, String protocol) {
            return map(port, port, protocol, "DFT");
        }

        /**
         * Map an external port number on the gateway for the specified protocol to a
         * potentially different internal port number on the local address on this
         * host..
         * 
         * @param internalPort port number
         * @param externalPort port number
         * @param protocol     protocol
         * @param description  description
         * @return mapped
         * @throws UncheckedIOException on serious error
         */
        public boolean map(int internalPort, int externalPort, String protocol, String description) {
            if (internalPort < 0 || internalPort > 65535 || externalPort < 0 || externalPort > 65535) {
                throw new IllegalArgumentException();
            }

            try {
                if (shutdownHooks) {
                    synchronized (hooks) {
                        if (hooks.containsKey(externalPort)) {
                            Runtime.getRuntime().removeShutdownHook(hooks.remove(externalPort));
                        }
                    }
                }
    
                var response = cmd("AddPortMapping",
                        Map.of("NewInternalClient", localIp.getHostAddress(), "NewExternalPort", externalPort,
                                "NewInternalPort", internalPort, "NewPortMappingDescription", description, "NewRemoteHost", "",
                                "NewProtocol", protocol, "NewEnabled", 1, "NewLeaseDuration", 0));
                var ok = response.get("errorCode") == null;
                if (ok && shutdownHooks) {
                    var hook = new Thread(() -> unmap(externalPort, protocol), "Unmap" + externalPort);
                    hooks.put(externalPort, hook);
                    Runtime.getRuntime().addShutdownHook(hook);
                }
                return ok;
            }
            catch(IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        /**
         * Un-map an external port number on the gateway for the specified protocol from
         * whatever internal port number on the local address on this host that was used
         * when mapping.
         * 
         * @param externalPort external port number
         * @param protocol     protocol
         * @return un-mapped
         * @throws UncheckedIOException on serious error
         */
        public boolean unmap(int externalPort, String protocol) {
            if (externalPort < 0 || externalPort > 65535) {
                throw new IllegalArgumentException();
            }
            try {
                if (shutdownHooks) {
                    synchronized (hooks) {
                        if (hooks.containsKey(externalPort)) {
                            try {
                                Runtime.getRuntime().removeShutdownHook(hooks.remove(externalPort));
                            }
                            catch(IllegalStateException _) {
                            }
                        }
                    }
                }
    
                cmd("DeletePortMapping",
                        Map.of("NewExternalPort", externalPort, "NewProtocol", protocol, "NewRemoteHost", ""));
    
                return true;
            }
            catch(IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        /**
         * Get if a given external port number was mapped to any port number on this
         * local address.
         * 
         * @param externalPort external port number
         * @param protocol     protocol
         * @return was mapped
         * @throws IOException on serious error
         */
        public boolean mapped(int externalPort, String protocol) {
            if (externalPort < 0 || externalPort > 65535) {
                throw new IllegalArgumentException();
            }

            try {
                var r = cmd("GetSpecificPortMappingEntry",
                        Map.of("NewProtocol", protocol, "NewExternalPort", externalPort, "NewRemoteHost", ""));
    
                if (r.get("errorCode") != null) {
                    throw new IOException("Request failed with error " + r.get("errorCode"));
                }
                return r.get("NewInternalPort") != null;
            }
            catch(IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        /**
         * Get if this gateway device is currently online, i.e. it has a valid external
         * address.
         * <p>
         * This information should not be treated as entirely accurate. Further
         * reachability tests should be needed if an accurate state is needed.
         * 
         * @return online
         */
        public boolean online() {
            var extIp = externalIp();
            return extIp.isPresent() && !extIp.get().equalsIgnoreCase("0.0.0.0") && !extIp.get().equalsIgnoreCase("::")
                    && !extIp.get().equalsIgnoreCase("0:0:0:0:0:0:0:0");
        }

    }

    /**
     * This class broadcasts to the local network to discover Internet Gateway
     * Devices. If runs a single time, in a background thread. When completed the
     * instance cannot be reused. Create a new instance to repeat discovery.
     */
    public final static class Discovery implements Closeable {
        private final ExecutorService executor;
        private final Set<Gateway> gateways = Collections.synchronizedSet(new LinkedHashSet<>());
        private final Semaphore haveFirst = new Semaphore(1);
        private final int soTimeout;
        private final boolean shutdownHooks;
        private final Optional<Consumer<Gateway>> onGateway;
        private boolean closed;

        private Discovery(DiscoveryBuilder bldr) {
            soTimeout = bldr.soTimeout;
            onGateway = bldr.onGateway;
            shutdownHooks = bldr.shutdownHooks;

            var addresses = bldr.localAddresses.isEmpty() ? findAddresses() : new ArrayList<>(bldr.localAddresses);
            executor = Executors.newFixedThreadPool(MESSAGES.length * 2);
            var jobs = addresses.size() * MESSAGES.length * 2;
            var sems = new Semaphore(jobs);
            try {
                haveFirst.acquire();
                sems.acquire(sems.availablePermits());
            } catch (InterruptedException e) {
                throw new IllegalStateException("Impossible.", e);
            }

            for (var addr : addresses) {
                for (var msg : MESSAGES) {
                    executor.execute(() -> {
                        try {
                            discover(msg, addr);
                        } finally {
                            sems.release();
                        }
                    });
                    // 提高成功率
                    executor.execute(() -> {
                        try {
                            Thread.sleep(soTimeout/2);
                            discover(msg, addr);
                        } catch (InterruptedException ie) {
                            throw new IllegalStateException("Interrupted.", ie);
                        } finally {
                            sems.release();
                        }
                    });
                }
            }
            executor.execute(() -> {
                try {
                    sems.acquire(jobs);
                    try {
                        /*
                         * All jobs now finished, if we found no devices make sure the haveFirst
                         * semaphore is released
                         */
                        if (gateways.isEmpty())
                            haveFirst.release();
                    } finally {
                        sems.release();
                    }
                } catch (InterruptedException ie) {
                    if (!closed)
                        throw new IllegalStateException("Interrupted.", ie);
                }
            });
            executor.shutdown();
        }

        /**
         * Get if discovery is currently active.
         * 
         * @return actively discovering
         */
        public boolean discovering() {
            return !executor.isTerminated();
        }

        /**
         * Get the first discovered {@link Gateway} device. This method will block until
         * the first one has been discovered. Discovery may continue after this method
         * exits.
         * 
         * @return gateway
         */
        public Optional<Gateway> gateway() {
            try {
                haveFirst.acquire();
                return Optional.of(gateways.iterator().next());
            } catch (NoSuchElementException nsee) {
                return Optional.empty();
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted.", e);
            } finally {
                haveFirst.release();
            }
        }
        
        /** 
         * Wait for discovery to complete forever.
         */
        public void awaitCompletion() {
            awaitCompletion(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        
        /** Wait for discovery to complete.
         * 
         * @param units units to wait for
         * @param unit time unit of units
         */
        public void awaitCompletion(long units, TimeUnit unit) {
            try {
                executor.awaitTermination(units, unit);
            } catch (InterruptedException e) {
                throw new IllegalStateException("Interrupted.", e);
            }
        }

        /**
         * Get all {@link Gateway} devices found on this network. This method will block
         * until discovery has finished.
         * <p>
         * To be notified as gateways are found, use
         * {@link DiscoveryBuilder#onGateway(Consumer)}.
         * 
         * @return discovered gateways
         */
        public Set<Gateway> gateways() {
            awaitCompletion();
            return Collections.unmodifiableSet(gateways);
        }

        @Override
        public void close() {
            closed = true;
            executor.shutdownNow();
        }

        private void discover(String msg, InetAddress addr) {
            var offline = new ArrayList<Gateway>();
            var online = new AtomicInteger();
            try {
                var request = buildBroadcastRequest(msg);
                var socket = new DatagramSocket(new InetSocketAddress(addr, 0));

                socket.send(
                        new DatagramPacket(request, request.length, new InetSocketAddress("239.255.255.250", 1900)));
                socket.setSoTimeout(soTimeout);

                while (true) {
                    try {
                        var recv = new DatagramPacket(new byte[1536], 1536);
                        socket.receive(recv);
                        var gw = new Gateway(addr, recv.getAddress(), extractLocation(recv.getData()), shutdownHooks);
                        if (gw.online()) {
                            found(gw);
                            online.incrementAndGet();
                        } else
                            offline.add(gw);
                    } catch (SocketTimeoutException t) {
                        break;
                    } catch (IOException ioe) {
                    }
                }
            } catch (IOException t) {
            }
            if (online.get() == 0 && offline.size() > 0) {
                for (var gw : offline)
                    found(gw);
            }
        }

        private URI extractLocation(byte[] data) throws IOException {
            for (var token : new String(data, "UTF-8").split("\n")) {
                if (token.trim().isEmpty() || token.startsWith("HTTP/1.") || token.startsWith("NOTIFY *")) {
                    continue;
                }
                var idx = token.indexOf(':');
                var name = token.substring(0, idx);
                var value = token.length() >= name.length() ? token.substring(name.length() + 1).trim() : null;
                if (name.equalsIgnoreCase("location")) {
                    return URI.create(value);
                }
            }
            throw new IOException("Invalid response.");
        }

        private void found(Gateway gateway) {
            synchronized (gateways) {
                var first = gateways.isEmpty();
                var added = gateways.add(gateway);
                if (added) {
                    onGateway.ifPresent(g -> g.accept(gateway));
                }
                if (first) {
                    haveFirst.release();
                }

            }
        }

        private byte[] buildBroadcastRequest(String msg) {
            var str = new StringBuilder();
            str.append("M-SEARCH * HTTP/1.1\r\n");
            str.append("HOST: 239.255.255.250:1900\r\n");
            str.append("ST: ");
            str.append(msg);
            str.append("\r\n");
            str.append("MAN: \"ssdp:discover\"\r\n");
            str.append("MX: 2\r\n\r\n");
            byte[] req = str.toString().getBytes();
            return req;
        }

        private List<InetAddress> findAddresses() {
            var l = new ArrayList<InetAddress>();
            try {
                var ifaces = NetworkInterface.getNetworkInterfaces();
                while (ifaces.hasMoreElements()) {
                    try {
                        var iface = ifaces.nextElement();
                        if (iface.isUp() && !iface.isVirtual() && !iface.isPointToPoint() && !iface.isLoopback()) {
                            var addrs = iface.getInetAddresses();
                            if (addrs != null) {
                                while (addrs.hasMoreElements()) {
                                    l.add(addrs.nextElement());
                                }
                            }
                        }
                    } catch (Throwable t) {
                    }
                }
            } catch (Throwable t) {
            }
            return l;
        }
    }

    /**
     * Convenience method to find the first gateway on the network. For options on
     * configuring the discovery process, use {@link DiscoveryBuilder}.
     * <p>
     * From this instance you can map, unmap ports etc.
     * 
     * @return default gateway
     */
    public static Optional<Gateway> gateway() {
        try (var disco = new DiscoveryBuilder().build()) {
            return disco.gateway();
        }
    }
    
    public static void main(String[] args) {
        UPnP.gateway().ifPresent(gw -> gw.map(8080, "TCP"));
    }

}
