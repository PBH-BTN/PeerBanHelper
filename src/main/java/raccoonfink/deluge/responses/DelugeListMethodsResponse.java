package raccoonfink.deluge.responses;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import raccoonfink.deluge.DelugeException;

import java.util.ArrayList;
import java.util.List;

@Getter
public final class DelugeListMethodsResponse extends DelugeResponse {
    private final List<String> delugeSupportedMethods = new ArrayList<>();

    public DelugeListMethodsResponse(Integer httpResponseCode, JsonObject response) throws DelugeException {
        super(httpResponseCode, response);
        JsonArray jsonArray = response.getAsJsonArray("result");
        for (JsonElement element : jsonArray) {
            delugeSupportedMethods.add(element.getAsString());
        }
    }
}
