package raccoonfink.deluge.requests;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.json.JSONException;
import org.json.JSONObject;

@NoArgsConstructor
@Data
public class ConfigRequest {

    private Long maxDownloadSpeed;

    private Long maxUploadSpeed;

    public JSONObject toRequestJSON() throws JSONException {
        final JSONObject ret = new JSONObject();
        if (maxDownloadSpeed != null) {
            ret.put("max_download_speed", maxDownloadSpeed);
        }
        if (maxUploadSpeed != null) {
            ret.put("max_upload_speed", maxUploadSpeed);
        }
        return ret;
    }
}
