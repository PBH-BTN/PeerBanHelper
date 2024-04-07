package cordelia.client;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import cordelia.rpc.RqArguments;
import cordelia.rpc.RsArguments;
import cordelia.rpc.types.Status;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Slf4j
public final class TrClient {

    private final String url;
    private final SessionStore sessionStore = new SessionStore();
    private final Gson om = new GsonBuilder()
            .registerTypeAdapter(Status.class, new TypeAdapter<Status>() {
                @Override
                public void write(JsonWriter out, Status value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                        return;
                    }
                    out.value(value.getIdx());
                }

                @Override
                public Status read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return Status.STOPPED;
                    }
                    return Status.fromIdx(in.nextInt());
                }
            })
            .create();
    private final HttpClient httpClient;

    public TrClient(String url) {
        this(url, null, null, true);
    }

    public TrClient(String url, String user, String password, boolean verifySSL) {
        this.url = url;
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        HttpClient.Builder builder = HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .authenticator(new Authenticator() {
                    @Override
                    public PasswordAuthentication requestPasswordAuthenticationInstance(String host, InetAddress addr, int port, String protocol, String prompt, String scheme, URL url, RequestorType reqType) {
                        return new PasswordAuthentication(user, password.toCharArray());
                    }
                })
                .cookieHandler(cm);
        if (!verifySSL && HTTPUtil.getIgnoreSslContext() != null) {
            builder = builder.sslContext(HTTPUtil.getIgnoreSslContext());
        }
        this.httpClient = builder.build();
    }

    public <E extends RqArguments, S extends RsArguments> TypedResponse<S> execute(E req) {
        return execute(req, null);
    }

    @SneakyThrows(URISyntaxException.class)
    public <E extends RqArguments, S extends RsArguments> TypedResponse<S> execute(E req, Long tag) {
        try {
            HttpResponse<String> resp = httpClient.send(HttpRequest.newBuilder(new URI(url))
                            .header("User-Agent", Main.getUserAgent())
                            .header("Content-Type", "application/json")
                            .header(Session.SESSION_ID, session().id())
                            .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.getGson().toJson(req.toReq(tag))))
                            .timeout(Duration.of(30, ChronoUnit.SECONDS)).build()
                    , java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            RawResponse raw = om.fromJson(resp.body(), RawResponse.class);
            String json = om.toJson(raw.getArguments());
            return new TypedResponse<>(raw.getTag(), raw.getResult(), om.fromJson(json, req.answerClass()));
        } catch (IOException | InterruptedException e) {
            log.warn("Request Transmission JsonRPC failure", e);
            throw new IllegalStateException(e);
        }
    }

    public void shutdown() {

    }

    @SneakyThrows(URISyntaxException.class)
    private Session session() {
        if (sessionStore.isEmpty()) {
            try {
                HttpResponse<Void> resp = httpClient.send(java.net.http.HttpRequest.newBuilder(new URI(url)).GET().build(), java.net.http.HttpResponse.BodyHandlers.discarding());
                String sessionId = resp.headers().firstValue(Session.SESSION_ID).orElseThrow();
                sessionStore.set(new Session(sessionId));
            } catch (IOException | InterruptedException e) {
                log.warn("Request Transmission JsonRPC for getting session failure", e);
                throw new IllegalStateException(e);
            }
        }
        return sessionStore.get();
    }
}
