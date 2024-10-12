package cordelia.client;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import cordelia.rpc.RqArguments;
import cordelia.rpc.RsArguments;
import cordelia.rpc.types.Status;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;
import static com.ghostchu.peerbanhelper.util.HTTPUtil.MEDIA_TYPE_JSON;

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
    private final OkHttpClient httpClient;

    public TrClient(String url, String user, String password, boolean verifySSL, String httpVersion) {
        this.url = url;
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        OkHttpClient.Builder builder = Main.getSharedHttpClient()
                .newBuilder()
                .followRedirects(true)
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(15, ChronoUnit.SECONDS))
                .authenticator(new Authenticator(){
                    @Override
                    public Request authenticate(@Nullable Route route, @NotNull Response response) {
                        String credential = Credentials.basic(user, password == null ? "" : password);
                        return response.request().newBuilder().header("Authorization", credential).build();
                    }
                })
                .cookieJar(new JavaNetCookieJar(cm));
        if (!verifySSL && HTTPUtil.getIgnoreSSLSocketFactory() != null) {
            builder.sslSocketFactory(HTTPUtil.getIgnoreSSLSocketFactory(), HTTPUtil.getIgnoreTrustManager());
        }
        if (httpVersion.equals("HTTP_1_1")) {
            builder.protocols(Arrays.asList(Protocol.HTTP_1_1));
        }
        this.httpClient = builder.build();
    }

    public <E extends RqArguments, S extends RsArguments> TypedResponse<S> execute(E req) {
        return execute(req, null);
    }

    public <E extends RqArguments, S extends RsArguments> TypedResponse<S> execute(E req, Long tag) {
        String jsonBuffer = null;
        try (Response resp = httpClient.newCall(new Request.Builder()
                .url(url)
                .post(RequestBody.create(JsonUtil.getGson().toJson(req.toReq(tag)), MEDIA_TYPE_JSON))
                .header("Content-Type", "application/json")
                .header(Session.SESSION_ID, session(false).id())
                .build()).execute()) {
            if (resp.code() == 409) {
                session(true); // force renew
                throw new IllegalStateException("Session invalid, re-created, please try again.");
            }
            jsonBuffer = resp.body().string();
            RawResponse raw = om.fromJson(resp.body().string(), RawResponse.class);
            String json = om.toJson(raw.getArguments());
            return new TypedResponse<>(raw.getTag(), raw.getResult(), om.fromJson(json, req.answerClass()));
        } catch (JsonSyntaxException jsonSyntaxException) {
            log.error(tlUI(Lang.DOWNLOADER_TR_INVALID_RESPONSE, jsonBuffer, jsonSyntaxException));
            throw new IllegalStateException(jsonSyntaxException);
        } catch (IOException e) {
            log.error("Request Transmission JsonRPC failure", e);
            throw new IllegalStateException(e);
        }
    }

    public void shutdown() {

    }

    private Session session(boolean forceUpdate) {
        if (sessionStore.isEmpty() || forceUpdate) {
            try (Response resp = httpClient.newCall(new Request.Builder().url(url).build()).execute()) {
                String sessionId = resp.headers().get(Session.SESSION_ID);
                if (sessionId != null) {
                    sessionStore.set(new Session(sessionId));
                } else {
                    throw new InterruptedException();
                }
            } catch (IOException | InterruptedException e) {
                log.error(tlUI(Lang.TRCLIENT_API_ERROR, e.getClass().getName()), e.getMessage());
                throw new IllegalStateException(e);
            }
        }
        return sessionStore.get();
    }
}
