package raccoonfink.deluge.responses;

import org.json.JSONException;
import org.json.JSONObject;
import raccoonfink.deluge.DelugeException;

public final class CheckSessionResponse extends DelugeResponse {
    private final boolean m_sessionActive;

    public CheckSessionResponse(final Integer httpResponseCode, final JSONObject result) throws DelugeException {
        super(httpResponseCode, result);

        if (result != null) {
            m_sessionActive = result.optBoolean("result");
        } else {
            m_sessionActive = false;
        }
    }

    public boolean isSessionActive() {
        return m_sessionActive;
    }

    @Override
    public JSONObject toResponseJSON() throws JSONException {
        final JSONObject ret = super.toResponseJSON();
        ret.put("result", isSessionActive());
        return ret;
    }
}
