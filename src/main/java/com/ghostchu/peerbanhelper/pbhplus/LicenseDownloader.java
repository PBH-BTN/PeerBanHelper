package com.ghostchu.peerbanhelper.pbhplus;

import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LicenseDownloader {
    private final HTTPUtil httpUtil;

    public LicenseDownloader(HTTPUtil httpUtil) {
        this.httpUtil = httpUtil;
    }

    @Nullable
    public String attemptDownloadFromServer(@NotNull String text) {
        var httpClient = httpUtil.newBuilder().callTimeout(15, TimeUnit.SECONDS).build();
        var urlBuilder = new HttpUrl.Builder()
                .scheme("https")
                .host("api.pbh-btn.com")
                .addPathSegment("peerbanhelper")
                .addPathSegment("v1")
                .addPathSegment("licenses")
                .addPathSegment("download")
                .addQueryParameter("query", text);
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException(response.code() + " " + response.body().string());
            }
            if (response.code() == 204 || response.code() == 404) {
                return null;
            }
            var result = JsonUtil.standard().fromJson(response.body().charStream(), ServerDownloadedLicense.class);
            return result.keyText;
        } catch (Exception e) {
            log.warn("Failed to download the license from server", e);
            return null;
        }
    }

    public static class ServerDownloadedLicense {
        private String keyText;
    }
}
