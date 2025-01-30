package raccoonfink.deluge.responses;

import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONObject;
import raccoonfink.deluge.DelugeException;

import java.util.ArrayList;
import java.util.List;

@Getter
public final class DelugeListMethodsResponse extends DelugeResponse {
    private final List<String> delugeSupportedMethods = new ArrayList<>();

    public DelugeListMethodsResponse(Integer httpResponseCode, JSONObject response) throws DelugeException {
        super(httpResponseCode, response);
        JSONArray jsonArray = response.getJSONArray("result");
        jsonArray.forEach(object -> {
            delugeSupportedMethods.add((String) object);
        });
    }
}
