package raccoonfink.deluge.requests;

import com.google.gson.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class ConfigRequest {

    private Long maxDownloadSpeed;

    private Long maxUploadSpeed;

    public JsonObject toRequestJSON() {
        final JsonObject ret = new JsonObject();
        if (maxDownloadSpeed != null) {
            ret.addProperty("max_download_speed", maxDownloadSpeed);
        }
        if (maxUploadSpeed != null) {
            ret.addProperty("max_upload_speed", maxUploadSpeed);
        }
        return ret;
    }
}
