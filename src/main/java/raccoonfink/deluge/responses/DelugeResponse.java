package raccoonfink.deluge.responses;

import com.google.gson.JsonObject;
import raccoonfink.deluge.DelugeException;

public class DelugeResponse {

    private final int m_id;
    private final int m_responseCode;
    private final JsonObject m_result;

    public DelugeResponse(final Integer httpResponseCode, final JsonObject response) throws DelugeException {
        assert (httpResponseCode != null);

        if (!response.has("id")) {
            throw new DelugeException("Invalid 'id' field in JSON: " + response);
        }
        m_id = response.get("id").getAsInt();
        m_responseCode = httpResponseCode;
        m_result = response;
    }

    public int getId() {
        return m_id;
    }

    public int getResponseCode() {
        return m_responseCode;
    }

    public JsonObject getResponseData() {
        return m_result;
    }

    public JsonObject toResponseJSON() {
        final JsonObject ret = new JsonObject();
        ret.addProperty("id", getId());
        ret.addProperty("responseCode", getResponseCode());
        ret.add("result", null);
        return ret;
    }
}
