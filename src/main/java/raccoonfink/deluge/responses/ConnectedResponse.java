package raccoonfink.deluge.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import raccoonfink.deluge.DelugeException;

public final class ConnectedResponse extends DelugeResponse {
    private final boolean m_connected;

    public ConnectedResponse(final Integer httpResponseCode, final JsonNode response) throws DelugeException {
        super(httpResponseCode, response);

        JsonNode resultNode = response.get("result");
        if (resultNode == null || !resultNode.isBoolean()) {
            throw new DelugeException("Invalid 'result' field in JSON: " + response);
        }
        m_connected = resultNode.asBoolean();
    }

    public ConnectedResponse(final Integer httpResponseCode, final JsonNode response, final boolean isConnected) throws DelugeException {
        super(httpResponseCode, response);
        m_connected = isConnected;
    }

    public boolean isConnected() {
        return m_connected;
    }


    @Override
    public JsonNode toResponseJSON() {
        final ObjectNode ret = (ObjectNode) super.toResponseJSON();
        ret.put("result", isConnected());
        return ret;
    }
}
