package raccoonfink.deluge.responses;

import org.json.JSONException;
import org.json.JSONObject;
import raccoonfink.deluge.DelugeException;

public final class ConnectedResponse extends DelugeResponse {
    private final boolean m_connected;

    public ConnectedResponse(final Integer httpResponseCode, final JSONObject response) throws DelugeException {
        super(httpResponseCode, response);
        try {
            m_connected = response.getBoolean("result");
        } catch (final JSONException e) {
            throw new DelugeException(e);
        }
    }

    public ConnectedResponse(final Integer httpResponseCode, final JSONObject response, final boolean isConnected) throws DelugeException {
        super(httpResponseCode, response);
        m_connected = isConnected;
    }

    public boolean isConnected() {
        return m_connected;
    }


    @Override
    public JSONObject toResponseJSON() throws JSONException {
        final JSONObject ret = super.toResponseJSON();
        ret.put("result", isConnected());
        return ret;
    }
}
