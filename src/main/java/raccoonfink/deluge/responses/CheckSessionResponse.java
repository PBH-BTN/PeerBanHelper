package raccoonfink.deluge.responses;

import com.google.gson.JsonObject;
import raccoonfink.deluge.DelugeException;

public final class CheckSessionResponse extends DelugeResponse {
    private final boolean m_sessionActive;

    public CheckSessionResponse(final Integer httpResponseCode, final JsonObject result) throws DelugeException {
        super(httpResponseCode, result);

        if (result != null && result.has("result")) {
            m_sessionActive = result.get("result").getAsBoolean();
        } else {
            m_sessionActive = false;
        }
    }

    public boolean isSessionActive() {
        return m_sessionActive;
    }

    @Override
    public JsonObject toResponseJSON() {
        final JsonObject ret = super.toResponseJSON();
        ret.addProperty("result", isSessionActive());
        return ret;
    }
}
