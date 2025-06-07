package raccoonfink.deluge.responses;

import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.json.JSONObject;
import raccoonfink.deluge.DelugeException;

@Getter
public class ConfigResponse extends DelugeResponse {

    private ConfigRequestDTO config;

    public ConfigResponse(Integer httpResponseCode, JSONObject response) throws DelugeException {
        super(httpResponseCode, response);

        if (response.isNull("result")) {
            return;
        }
        JSONObject jsonObject = response.getJSONObject("result");
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
