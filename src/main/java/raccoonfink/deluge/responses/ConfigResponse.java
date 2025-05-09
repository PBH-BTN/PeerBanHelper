package raccoonfink.deluge.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import raccoonfink.deluge.DelugeException;

@Getter
public class ConfigResponse extends DelugeResponse {

    private ConfigRequestDTO config;

    public ConfigResponse(Integer httpResponseCode, JsonNode response) throws DelugeException {
        super(httpResponseCode, response);

        if (!response.has("result")) {
            return;
        }
        JsonNode jsonObject = response.get("result");
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
