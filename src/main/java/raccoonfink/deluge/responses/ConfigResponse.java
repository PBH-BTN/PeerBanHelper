package raccoonfink.deluge.responses;

import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import raccoonfink.deluge.DelugeException;

@Getter
public class ConfigResponse extends DelugeResponse {

    private ConfigRequestDTO config;

    public ConfigResponse(Integer httpResponseCode, JsonObject response) throws DelugeException {
        super(httpResponseCode, response);

        if (!response.has("result") || response.get("result").isJsonNull()) {
            return;
        }
        JsonObject jsonObject = response.getAsJsonObject("result");
        String resultJson = jsonObject.toString();
        this.config = JsonUtil.getGson().fromJson(resultJson, ConfigRequestDTO.class);
    }

    @NoArgsConstructor
    @Data
    public static class ConfigRequestDTO {

        @SerializedName("max_download_speed")
        private Long maxDownloadSpeed;

        @SerializedName("max_upload_speed")
        private Long maxUploadSpeed;
    }
}
