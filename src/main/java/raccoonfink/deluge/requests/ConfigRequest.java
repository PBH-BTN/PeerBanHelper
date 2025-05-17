package raccoonfink.deluge.requests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class ConfigRequest {

    private Long maxDownloadSpeed;

    private Long maxUploadSpeed;

    public JsonNode toRequestJSON() {
        final ObjectNode ret = JsonUtil.getObjectMapper().createObjectNode();
        if (maxDownloadSpeed != null) {
            ret.put("max_download_speed", maxDownloadSpeed);
        }
        if (maxUploadSpeed != null) {
            ret.put("max_upload_speed", maxUploadSpeed);
        }
        return ret;
    }
}
