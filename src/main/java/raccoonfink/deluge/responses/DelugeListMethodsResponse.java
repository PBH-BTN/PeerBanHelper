package raccoonfink.deluge.responses;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import raccoonfink.deluge.DelugeException;

import java.util.ArrayList;
import java.util.List;

@Getter
public final class DelugeListMethodsResponse extends DelugeResponse {
    private final List<String> delugeSupportedMethods = new ArrayList<>();

    public DelugeListMethodsResponse(Integer httpResponseCode, JsonNode response) throws DelugeException {
        super(httpResponseCode, response);
        if (!response.has("result")) {
            return;
        }
        JsonNode jsonArray = response.get("result");
        jsonArray.forEach(object -> {
            delugeSupportedMethods.add(object.asText());
        });
    }
}
