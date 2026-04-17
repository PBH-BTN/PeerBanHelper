package com.ghostchu.peerbanhelper.util.umami;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.CommonDataCollector;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class UmamiHelper {
    private final String umamiUrl = "https://uma.pbh-btn.com/api/send";
    private final String websiteId = "f9ed8c46-5d57-4ae5-ab45-227658dffb41";
    private final CommonDataCollector dataCollector;
    private final OkHttpClient client;

    public UmamiHelper(@Autowired HTTPUtil httpUtil, @Autowired CommonDataCollector dataCollector) {
        this.dataCollector = dataCollector;
        this.client = httpUtil.newBuilder().build();
    }

    public void sendBootEvent() {
        sendEvent("/lifecycle/boot", "application-boot");
    }


    public void sendHeartbeatEvent() {
        sendEvent("/lifecycle/heartbeat", "application-heartbeat");
    }

    private void sendEvent(String url, String eventName) {
        if (!Main.getMainConfig().getBoolean("privacy.analytics")) {
            return; // User disabled telemetry
        }
        Thread.ofVirtual().name("Umami Event Sender").start(() -> {
            var event = new UmamiEvent();
            event.setType("event");
            var payload = new UmamiEvent.PayloadDTO();
            payload.setId(Main.getMainConfig().getString("installation-id"));
            payload.setUrl(url);
            payload.setHostname("backend.peerbanhelper");
            payload.setName(eventName);
            payload.setWebsite(websiteId);
            payload.setData(createData());
            event.setPayload(payload);
            String json = JsonUtil.standard().toJson(event);
            var postReq = new Request.Builder()
                    .url(umamiUrl)
                    .post(RequestBody.create(json.getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/json")
                    .build();
            try (var response = client.newCall(postReq).execute()) {
                if (!response.isSuccessful()) {
                    log.debug("Unable to send Umami event: HTTP {}: {}", response.code(), response.body().string());
                    return;
                }
                log.debug("Successfully sent Umami event.");
            } catch (IOException e) {
                log.debug("Unable to send Umami event due exception: {}", e.getMessage(), e);
            }
        });
    }

    public Map<String, Object> createData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jvm", dataCollector.generateJvmData());
        data.put("pbh", dataCollector.generatePbhData());
        data.put("system", dataCollector.generateSystemData());
        data.put("networking", dataCollector.generateNetworkStats());
        return data;
    }


    @NoArgsConstructor
    @Data
    public static class UmamiEvent {

        @SerializedName("type")
        private String type;
        @SerializedName("payload")
        private PayloadDTO payload;

        @NoArgsConstructor
        @Data
        public static class PayloadDTO {
            @SerializedName("id")
            private String id;
            @SerializedName("website")
            private String website;
            @SerializedName("url")
            private String url;
            @SerializedName("hostname")
            private String hostname;
            @SerializedName("name")
            private String name;
            @SerializedName("data")
            private Map<String, Object> data;
        }
    }

}
