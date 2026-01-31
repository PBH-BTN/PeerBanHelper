package raccoonfink.deluge.responses;

import com.google.gson.JsonObject;
import raccoonfink.deluge.DelugeException;

public final class LoginResponse extends DelugeResponse {
    private final boolean m_loggedIn;

    public LoginResponse(final Integer httpResponseCode, final JsonObject response) throws DelugeException {
        super(httpResponseCode, response);

        if (response != null && response.has("result")) {
            m_loggedIn = response.get("result").getAsBoolean();
        } else {
            m_loggedIn = false;
        }
    }

    public boolean isLoggedIn() {
        return m_loggedIn;
    }

    @Override
    public JsonObject toResponseJSON() {
        final JsonObject ret = super.toResponseJSON();
        ret.addProperty("result", isLoggedIn());
        return ret;
    }
}
