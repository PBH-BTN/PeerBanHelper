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
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.net.Proxy;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

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

    public TrClient(HTTPUtil httpUtil, String url, String user, String password, boolean verifySSL) {
        this.url = url;
        // Note: For simplicity, we're not implementing cookie handling for now
        
        OkHttpClient.Builder builder = httpUtil.newBuilder()
                .proxy(Proxy.NO_PROXY)
                .connectionPool(new ConnectionPool(24, 5, TimeUnit.MINUTES))
                .connectTimeout(Duration.of(10, ChronoUnit.SECONDS))
                .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .writeTimeout(Duration.of(30, ChronoUnit.SECONDS))
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder()
                            .header("User-Agent", Main.getUserAgent());
                    return chain.proceed(requestBuilder.build());
                })
                .authenticator((route, response) -> {
                    if(HTTPUtil.responseCount(response) > 1) {
                        return null;
                    }
                    String credential = Credentials.basic(user, password == null ? "" : password);
                    return response.request().newBuilder()
                            .header("Authorization", credential)
                            .build();
                });

        httpUtil.disableSSLVerify(builder, !verifySSL);
        this.httpClient = builder.build();
    }

    public <E extends RqArguments, S extends RsArguments> TypedResponse<S> execute(E req) {
        return execute(req, null);
    }

    public <E extends RqArguments, S extends RsArguments> TypedResponse<S> execute(E req, Long tag) {
        try {
            RequestBody requestBody = RequestBody.create(
                JsonUtil.getGson().toJson(req.toReq(tag)),
                MediaType.get("application/json")
            );
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header(Session.SESSION_ID, session(false).id())
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.code() == 409) {
                    session(true); // force renew
                    throw new IllegalStateException("Session invalid, re-created, please try again.");
                }
                
                String responseBody = response.body().string();
                RawResponse raw = om.fromJson(responseBody, RawResponse.class);
                String json = om.toJson(raw.getArguments());
                return new TypedResponse<>(raw.getTag(), raw.getResult(), om.fromJson(json, req.answerClass()));
            }
        } catch (JsonSyntaxException jsonSyntaxException) {
            log.error(tlUI(Lang.DOWNLOADER_TR_INVALID_RESPONSE, "<?>", jsonSyntaxException));
            throw new TransmissionIOException("Invalid response", jsonSyntaxException);
        } catch (IOException e) {
            log.debug("Request Transmission JsonRPC failure", e);
            throw new TransmissionIOException("IOException", e);
        }
    }

    public void shutdown() {

    }

    private Session session(boolean forceUpdate) {
        if (sessionStore.isEmpty() || forceUpdate) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    String sessionId = response.header(Session.SESSION_ID);
                    if (sessionId == null) {
                        throw new IllegalStateException("No session ID found in response headers");
                    }
                    sessionStore.set(new Session(sessionId));
                }
            } catch (IOException e) {
                log.error(tlUI(Lang.TRCLIENT_API_ERROR, e.getClass().getName()), e.getMessage());
                throw new IllegalStateException(e);
            }
        }
        return sessionStore.get();
    }
}
