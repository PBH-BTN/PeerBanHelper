package raccoonfink.deluge.responses;

import com.google.gson.JsonObject;
import raccoonfink.deluge.DelugeException;

public final class ConnectedResponse extends DelugeResponse {
    private final boolean m_connected;

    public ConnectedResponse(final Integer httpResponseCode, final JsonObject response) throws DelugeException {
        super(httpResponseCode, response);
        if (!response.has("result")) {
            throw new DelugeException("Missing 'result' field in JSON response");
        }
        m_connected = response.get("result").getAsBoolean();
    }

    public ConnectedResponse(final Integer httpResponseCode, final JsonObject response, final boolean isConnected) throws DelugeException {
        super(httpResponseCode, response);
        m_connected = isConnected;
    }

    public boolean isConnected() {
        return m_connected;
    }


    @Override
    public JsonObject toResponseJSON() {
        final JsonObject ret = super.toResponseJSON();
        ret.addProperty("result", isConnected());
        return ret;
    }
}
