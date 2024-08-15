package raccoonfink.deluge;

import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.github.mizosoft.methanol.Methanol;
import com.github.mizosoft.methanol.MutableRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raccoonfink.deluge.responses.*;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

public class DelugeServer {
    private static final Logger log = LoggerFactory.getLogger(DelugeServer.class);
    private final String m_url;
    private final String m_password;
    private final HttpClient httpClient;

    private final CookieManager m_cookieManager = new CookieManager();
    private int m_counter = 0;

    public DelugeServer(final String url, final String password, boolean verifySSL, HttpClient.Version httpVersion, String baUser, String baPassword) {
        m_url = url;
        m_password = password;
        m_cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        HttpURLConnection.setFollowRedirects(true);

        HttpClient.Builder builder = Methanol
                .newBuilder()
                .version(httpVersion)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .headersTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .requestTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Accept-Encoding", "gzip,deflate")
                .defaultHeader("Content-Type", "application/json")
                .authenticator(new Authenticator() {
                    @Override
                    public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                        return new PasswordAuthentication(baUser, baPassword.toCharArray());
                    }
                })
                .cookieHandler(m_cookieManager);
        if (!verifySSL && HTTPUtil.getIgnoreSslContext() != null) {
            builder = builder.sslContext(HTTPUtil.getIgnoreSslContext());
        }
        this.httpClient = builder.build();
    }

//    private static void closeQuietly(final Closeable c) {
//        if (c == null) {
//            return;
//        }
//        try {
//            c.close();
//        } catch (final IOException e) {
//        }
//    }

    public DelugeResponse makeRequest(final DelugeRequest delugeRequest) throws DelugeException {
        int connectionResponseCode;
        JSONObject jsonResponse;

        final String postData = delugeRequest.toPostData(m_counter++);
        //final String cookieHeader = getCookieHeader();
        HttpResponse<String> resp;
        try {
            if (postData != null) {
                resp = httpClient
                        .send(MutableRequest.POST(m_url, HttpRequest.BodyPublishers.ofString(postData)),
                                //.header("Cookie", cookieHeader)
                                //.header("Content-Length", String.valueOf(postData.getBytes(StandardCharsets.UTF_8).length)),
                                java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } else {
                resp = httpClient
                        .send(MutableRequest.POST(m_url, HttpRequest.BodyPublishers.noBody()),
                                //.header("Cookie", cookieHeader),
                                java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            }
            connectionResponseCode = resp.statusCode();
            if (connectionResponseCode != 200) {
                throw new DelugeException(resp.statusCode() + " - " + resp.body());
            }
            jsonResponse = new JSONObject(resp.body());
//				List<String> setCookie = resp.headers().allValues("Set-Cookie");
//				if(!setCookie.isEmpty()){
//					addCookies(setCookie);
//				}
        } catch (final IOException | JSONException e) {
            throw new DelugeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DelugeException(e);
        }
        try {
            if (jsonResponse.has("error") && !jsonResponse.isNull("error")) {
                final JSONObject error = jsonResponse.getJSONObject("error");
                final String message = error.optString("message");
                final int code = error.optInt("code");
                final StringBuilder builder = new StringBuilder("Error");
                if (code >= 0) {
                    builder.append(" ").append(code);
                }
                if (message != null) {
                    builder.append(": ").append(message);
                }
                throw new DelugeException(builder.toString());
            }
        } catch (final JSONException e) {
            throw new DelugeException(e);
        }
        return new DelugeResponse(connectionResponseCode, jsonResponse);
    }

//	private String getCookieHeader() {
//		StringJoiner joiner = new StringJoiner(",");
//		final List<HttpCookie> cookies = m_cookieManager.getCookieStore().getCookies();
//		if (cookies == null) {
//			return "";
//		}
//        for (HttpCookie cookie : cookies) {
//			joiner.add(cookie.toString());
//        }
//		return joiner.toString();
//	}

//	private void addCookies(final List<String> cookies) {
//		if (cookies == null) {
//			return;
//		}
//		for (final String cookie : cookies) {
//			m_cookieManager.getCookieStore().add(null, HttpCookie.parse(cookie).get(0));
//		}
//	}

    public DelugeListMethodsResponse listMethods() throws DelugeException {
        DelugeResponse response = makeRequest(new DelugeRequest("system.listMethods"));
        return new DelugeListMethodsResponse(response.getResponseCode(), response.getResponseData());
    }

    public CheckSessionResponse checkSession() throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("auth.check_session"));
        return new CheckSessionResponse(response.getResponseCode(), response.getResponseData());
    }

    public LoginResponse login() throws DelugeException {
        final DelugeRequest request = new DelugeRequest("auth.login", m_password);
        final DelugeResponse response = makeRequest(request);
        return new LoginResponse(response.getResponseCode(), response.getResponseData());
    }

    public DeleteSessionResponse deleteSession() throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("auth.delete_session"));
        return new DeleteSessionResponse(response.getResponseCode(), response.getResponseData());
    }

    public void registerEventListeners() throws DelugeException {
        final List<String> events = Arrays.asList("ConfigValueChangedEvent",
                "NewVersionAvailableEvent",
                "PluginDisabledEvent",
                "PluginEnabledEvent",
                "PreTorrentRemovedEvent",
                "SessionPausedEvent",
                "SessionResumedEvent",
                "SessionStartedEvent",
                "TorrentAddedEvent",
                "TorrentFileRenamedEvent",
                "TorrentFinishedEvent",
                "TorrentFolderRenamedEvent",
                "TorrentQueueChangedEvent",
                "TorrentRemovedEvent",
                "TorrentResumedEvent",
                "TorrentStateChangedEvent");

        for (final String event : events) {
            makeRequest(new DelugeRequest("web.register_event_listener", event));
        }
    }

    public ConnectedResponse isConnected() throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("web.connected"));
        return new ConnectedResponse(response.getResponseCode(), response.getResponseData());
    }

    public HostResponse getHosts() throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("web.get_hosts"));
        return new HostResponse(response.getResponseCode(), response.getResponseData(), false);
    }

    public HostResponse getHostStatus(final String id) throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("web.get_host_status", id));
        return new HostResponse(response.getResponseCode(), response.getResponseData(), true);
    }

    public ConnectedResponse connect(final String id) throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("web.connect", id));
        if (response.getResponseData().isNull("result")) {
            return new ConnectedResponse(response.getResponseCode(), response.getResponseData(), true);
        } else {
            return new ConnectedResponse(response.getResponseCode(), response.getResponseData());
        }
    }

    public ConnectedResponse disconnect() throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("web.disconnect"));
        if (response.getResponseData().isNull("result")) {
            return new ConnectedResponse(response.getResponseCode(), response.getResponseData(), false);
        } else {
            return new ConnectedResponse(response.getResponseCode(), response.getResponseData(), true);
        }
    }

    public EventsResponse getEvents() throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("web.get_events"));
        return new EventsResponse(response.getResponseCode(), response.getResponseData());
    }

    public UIResponse updateUI() throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("web.update_ui", new JSONArray(Arrays.asList(
                "queue",
                "name",
                "total_size",
                "state",
                "progress",
                "num_seeds",
                "total_seeds",
                "num_peers",
                "total_peers",
                "download_payload_rate",
                "upload_payload_rate",
                "eta",
                "ratio",
                "distributed_copies",
                "is_auto_managed",
                "time_added",
                "tracker_host",
                "save_path",
                "total_done",
                "total_uploaded",
                "max_download_speed",
                "max_upload_speed",
                "seeds_peers_ratio"
        )), new JSONObject()));
        return new UIResponse(response.getResponseCode(), response.getResponseData());
    }

    public PBHActiveTorrentsResponse getActiveTorrents() throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("peerbanhelperadapter.get_active_torrents_info"));
        return new PBHActiveTorrentsResponse(response.getResponseCode(), response.getResponseData());
    }

    public PBHBannedPeersResponse getBannedPeers() throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("peerbanhelperadapter.get_blocklist"));
        return new PBHBannedPeersResponse(response.getResponseCode(), response.getResponseData());
    }

    public boolean banPeers(List<String> ips) throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("peerbanhelperadapter.ban_ips", ips));
        return !determineResponseError(response);
    }

    public boolean unbanPeers(List<String> ips) throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("peerbanhelperadapter.unban_ips", ips));
        return !determineResponseError(response);
    }

    public boolean replaceBannedPeers(List<String> ips) throws DelugeException {
        final DelugeResponse response = makeRequest(new DelugeRequest("peerbanhelperadapter.replace_blocklist", ips));
        return !determineResponseError(response);
    }

    private boolean determineResponseError(DelugeResponse response) {
        if (response.getResponseData().isNull("error")) {
            return false;
        }
        Object error = response.getResponseData().get("error");
        if (error instanceof Boolean && !(Boolean) error) {
            return false;
        }
        printError(response);
        return true;
    }

    private void printError(DelugeResponse response) {
        JSONObject object = response.getResponseData().getJSONObject("error");
        log.info("Error when call Deluge RPC: message={}, code={}", object.getString("message"), object.getInt("code"));
    }
}

