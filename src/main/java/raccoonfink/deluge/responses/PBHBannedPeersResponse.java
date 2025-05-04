package raccoonfink.deluge.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import raccoonfink.deluge.DelugeException;

import java.util.List;

@Getter
public final class PBHBannedPeersResponse extends DelugeResponse {
    private BannedPeersResponseDTO bannedPeers;

    public PBHBannedPeersResponse(final Integer httpResponseCode, final JsonNode response) throws DelugeException {
        super(httpResponseCode, response);
        if (!response.has("result")) {
            return;
        }
        JsonNode jsonObject = response.get("result");
        String resultJson = jsonObject.toString();
        this.bannedPeers = JsonUtil.getGson().fromJson(resultJson, BannedPeersResponseDTO.class);
    }

    @NoArgsConstructor
    @Data
    public static class BannedPeersResponseDTO {
        @SerializedName("size")
        private Integer size;
        @SerializedName("ips")
        private List<String> ips;
    }
}
