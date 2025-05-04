package raccoonfink.deluge.responses;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import raccoonfink.deluge.DelugeException;

public final class CheckSessionResponse extends DelugeResponse {
    private final boolean m_sessionActive;

    public CheckSessionResponse(final Integer httpResponseCode, final JsonNode result) throws DelugeException {
        super(httpResponseCode, result);

        if (result.has("result")) {
            m_sessionActive = result.get("result").asBoolean();
        } else {
            m_sessionActive = false;
        }
    }

    public boolean isSessionActive() {
        return m_sessionActive;
    }

    @Override
    public JsonNode toResponseJSON() {
        final ObjectNode ret = (ObjectNode) super.toResponseJSON();
        ret.put("result", isSessionActive());
        return ret;
    }
}
