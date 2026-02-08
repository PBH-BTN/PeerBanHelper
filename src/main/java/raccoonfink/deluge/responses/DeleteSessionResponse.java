package raccoonfink.deluge.responses;

import com.google.gson.JsonObject;
import raccoonfink.deluge.DelugeException;

public final class DeleteSessionResponse extends DelugeResponse {
    private final boolean m_sessionDeleted;

    public DeleteSessionResponse(final Integer httpResponseCode, final JsonObject result) throws DelugeException {
        super(httpResponseCode, result);

        if (result != null && result.has("result")) {
            m_sessionDeleted = result.get("result").getAsBoolean();
        } else {
            m_sessionDeleted = false;
        }
    }

    public boolean isSessionDeleted() {
        return m_sessionDeleted;
    }

    @Override
    public JsonObject toResponseJSON() {
        final JsonObject ret = super.toResponseJSON();
        ret.addProperty("result", isSessionDeleted());
        return ret;
    }
}
