package raccoonfink.deluge.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import raccoonfink.deluge.DelugeException;

public final class DeleteSessionResponse extends DelugeResponse {
    private final boolean m_sessionDeleted;

    public DeleteSessionResponse(final Integer httpResponseCode, final JsonNode result) throws DelugeException {
        super(httpResponseCode, result);

        if (result.has("result")) {
            m_sessionDeleted = result.get("result").asBoolean();
        } else {
            m_sessionDeleted = false;
        }
    }

    public boolean isSessionDeleted() {
        return m_sessionDeleted;
    }

    @Override
    public JsonNode toResponseJSON() {
        final ObjectNode ret = (ObjectNode) super.toResponseJSON();
        ret.put("result", isSessionDeleted());
        return ret;
    }
}
