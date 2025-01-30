package raccoonfink.deluge.responses;

import org.json.JSONException;
import org.json.JSONObject;
import raccoonfink.deluge.DelugeException;

public final class DeleteSessionResponse extends DelugeResponse {
    private final boolean m_sessionDeleted;

    public DeleteSessionResponse(final Integer httpResponseCode, final JSONObject result) throws DelugeException {
        super(httpResponseCode, result);

        if (result != null) {
            m_sessionDeleted = result.optBoolean("result");
        } else {
            m_sessionDeleted = false;
        }
    }

    public boolean isSessionDeleted() {
        return m_sessionDeleted;
    }

    @Override
    public JSONObject toResponseJSON() throws JSONException {
        final JSONObject ret = super.toResponseJSON();
        ret.put("result", isSessionDeleted());
        return ret;
    }
}
