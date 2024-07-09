package com.ghostchu.peerbanhelper.downloader.impl.rtorrent;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import com.google.common.primitives.Bytes;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import de.timroes.axmlrpc.Call;
import de.timroes.axmlrpc.ResponseParser;
import de.timroes.axmlrpc.serializer.SerializerHandler;
import lombok.Cleanup;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
public class RTorrent implements Downloader {
    private final String apiEndpoint;

    private final Config config;
    private final Connector connector;
    private final SerializerHandler serializerHandler;
    private DownloaderLastStatus lastStatus = DownloaderLastStatus.UNKNOWN;
    private String name;
    private String statusMessage;

    public RTorrent(String name, Config config) {
        this.serializerHandler = new SerializerHandler();
        this.name = name;
        this.config = config;
        if (config.getEndpoint().startsWith("http")) {
            this.apiEndpoint = config.getEndpoint() + "/RPC2";
            this.connector = new XMLRPCOverHTTPConnector(serializerHandler, this.apiEndpoint, HttpClient.Version.valueOf(config.getHttpVersion()), config.getBasicAuth().getUser(), config.getBasicAuth().getPass(), config.isVerifySsl());
        } else {
            this.apiEndpoint = config.getEndpoint();
            this.connector = new XMLRPCUnixSocketConnector(serializerHandler, Path.of(this.apiEndpoint));
        }
    }

    public static RTorrent loadFromConfig(String name, JsonObject section) {
        Config config = JsonUtil.getGson().fromJson(section.toString(), Config.class);
        return new RTorrent(name, config);
    }

    public static RTorrent loadFromConfig(String name, ConfigurationSection section) {
        Config config = Config.readFromYaml(section);
        return new RTorrent(name, config);
    }

    @Override
    public JsonObject saveDownloaderJson() {
        return JsonUtil.getGson().toJsonTree(config).getAsJsonObject();
    }

    @Override
    public YamlConfiguration saveDownloader() {
        return config.saveToYaml();
    }

    public boolean login() {
        if (isLoggedIn()) return true; // 重用 Session 会话
        try {
            var obj = this.connector.sendPayload(new Call(serializerHandler, "system.listMethods"), true);
            System.out.println(obj);
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getEndpoint() {
        return apiEndpoint;
    }


    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return "rTorrent";
    }

    public boolean isLoggedIn() {
        return false;
    }

    @Override
    public void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed) {
        if (removed != null && removed.isEmpty() && added != null && config.isIncrementBan()) {
            setBanListIncrement(added);
        } else {
            setBanListFull(fullList);
        }
    }

    @Override
    public List<Torrent> getTorrents() {
        return Collections.emptyList();
    }

    @Override
    public void relaunchTorrentIfNeeded(Collection<Torrent> torrents) {
        // QB 很棒，什么都不需要做
    }

    @Override
    public void relaunchTorrentIfNeededByTorrentWrapper(Collection<TorrentWrapper> torrents) {
        // QB 很棒，什么都不需要做
    }

    @Override
    public List<Peer> getPeers(Torrent torrent) {
        return Collections.emptyList();
    }

    private void setBanListIncrement(Collection<BanMetadata> added) {

    }

    private void setBanListFull(Collection<PeerAddress> peerAddresses) {

    }

    @Override
    public DownloaderLastStatus getLastStatus() {
        return lastStatus;
    }

    @Override
    public void setLastStatus(DownloaderLastStatus lastStatus, String statusMessage) {
        this.lastStatus = lastStatus;
        this.statusMessage = statusMessage;
    }

    @Override
    public String getLastStatusMessage() {
        return statusMessage;
    }

    @Override
    public void close() {

    }

    public interface Connector {
        Object sendPayload(Call payload, boolean debug) throws Exception;
    }

    @NoArgsConstructor
    @Data
    public static class Config {

        private String type;
        private String endpoint;
        private String username;
        private String password;
        private BasicauthDTO basicAuth;
        private String httpVersion;
        private boolean incrementBan;
        private boolean verifySsl;

        public static Config readFromYaml(ConfigurationSection section) {
            Config config = new Config();
            config.setType("rtorrent");
            config.setEndpoint(section.getString("endpoint"));
            if (config.getEndpoint().endsWith("/")) { // 浏览器复制党 workaround 一下， 避免连不上的情况
                config.setEndpoint(config.getEndpoint().substring(0, config.getEndpoint().length() - 1));
            }
            config.setUsername(section.getString("username"));
            config.setPassword(section.getString("password"));
            BasicauthDTO basicauthDTO = new BasicauthDTO();
            basicauthDTO.setUser(section.getString("basic-auth.user"));
            basicauthDTO.setPass(section.getString("basic-auth.pass"));
            config.setBasicAuth(basicauthDTO);
            config.setHttpVersion(section.getString("http-version", "HTTP_1_1"));
            config.setIncrementBan(section.getBoolean("increment-ban"));
            config.setVerifySsl(section.getBoolean("verify-ssl", true));
            return config;
        }

        public YamlConfiguration saveToYaml() {
            YamlConfiguration section = new YamlConfiguration();
            section.set("type", "rtorrent");
            section.set("endpoint", endpoint);
            section.set("username", username);
            section.set("password", password);
            section.set("basic-auth.user", Objects.requireNonNullElse(basicAuth.user, ""));
            section.set("basic-auth.pass", Objects.requireNonNullElse(basicAuth.pass, ""));
            section.set("http-version", httpVersion);
            section.set("increment-ban", incrementBan);
            section.set("verify-ssl", verifySsl);
            return section;
        }

        @NoArgsConstructor
        @Data
        public static class BasicauthDTO {
            @SerializedName("user")
            private String user;
            @SerializedName("pass")
            private String pass;
        }
    }

    public static class XMLRPCUnixSocketConnector implements Connector {
        private final UnixDomainSocketAddress socketAddress;
        private final SerializerHandler serializerHandler;

        public XMLRPCUnixSocketConnector(SerializerHandler serializerHandler, Path socketPath) {
            this.serializerHandler = serializerHandler;
            this.socketAddress = UnixDomainSocketAddress.of(socketPath);
        }

        @Override
        public Object sendPayload(Call payload, boolean debug) throws Exception {
            String xml = payload.getXML(debug);
            byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
            @Cleanup
            SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.connect(socketAddress);
            sendBytes(channel, bytes);
            byte[] resp = readBytes(channel);
            @Cleanup
            InputStream stream = new ByteArrayInputStream(resp);
            return new ResponseParser().parse(serializerHandler, stream, debug);
        }

        private byte[] readBytes(SocketChannel channel) throws IOException {
            List<byte[]> readBytes = new LinkedList<>();
            while (true) {
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                buffer.clear();
                int bytesRead = channel.read(buffer);
                if (bytesRead < 0) {
                    break;
                }
                buffer.flip();
                byte[] bytes = new byte[bytesRead];
                buffer.get(bytes);
                readBytes.add(bytes);
            }
            var it = readBytes.iterator();
            byte[] bytes = it.next();
            while (it.hasNext()) {
                bytes = Bytes.concat(bytes, it.next());
            }
            return bytes;
        }

        private void sendBytes(SocketChannel channel, byte[] bytes) throws IOException {
            ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
            buffer.clear();
            buffer.put(bytes);
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    public static class XMLRPCOverHTTPConnector implements Connector {
        private final HttpClient httpClient;
        private final SerializerHandler serializerHandler;
        private final String endpoint;

        public XMLRPCOverHTTPConnector(SerializerHandler serializerHandler, String endpoint, HttpClient.Version httpVersion, String baUser, String baPass, boolean verifySSL) {
            this.serializerHandler = serializerHandler;
            this.endpoint = endpoint;
            CookieManager cm = new CookieManager();
            cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            Methanol.Builder builder = Methanol
                    .newBuilder()
                    .version(httpVersion)
                    .defaultHeader("Accept-Encoding", "gzip,deflate")
                    .defaultHeader("Content-Type", "text/xml")
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                    .headersTimeout(Duration.of(10, ChronoUnit.SECONDS))
                    .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                    .requestTimeout(Duration.of(30, ChronoUnit.SECONDS))
                    .authenticator(new Authenticator() {
                        @Override
                        public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                            return new PasswordAuthentication(baUser, baPass.toCharArray());
                        }
                    })
                    .cookieHandler(cm);
            if (!verifySSL && HTTPUtil.getIgnoreSslContext() != null) {
                builder.sslContext(HTTPUtil.getIgnoreSslContext());
            }
            this.httpClient = builder.build();

        }

        @Override
        public Object sendPayload(Call payload, boolean debug) throws Exception {
            String xml = payload.getXML(debug);
            var body = this.httpClient.send(
                    MutableRequest.POST(endpoint, HttpRequest.BodyPublishers.ofString(xml, StandardCharsets.UTF_8)),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (body.statusCode() != 200) {
                log.warn("XML-RPC over HTTP returns un-excepted statusCode: {}. The function may work properly", body.statusCode());
            }
            return new ResponseParser().parse(serializerHandler, body.body(), debug);
        }
    }
}
