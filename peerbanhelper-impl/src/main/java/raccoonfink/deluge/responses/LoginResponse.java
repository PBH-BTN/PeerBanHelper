package raccoonfink.deluge.responses;

import org.json.JSONException;
import org.json.JSONObject;
import raccoonfink.deluge.DelugeException;

public final class LoginResponse extends DelugeResponse {
    private final boolean m_loggedIn;

    public LoginResponse(final Integer httpResponseCode, final JSONObject response) throws DelugeException {
        super(httpResponseCode, response);

        if (response != null) {
            m_loggedIn = response.optBoolean("result");
        } else {
            m_loggedIn = false;
        }
    }

    public boolean isLoggedIn() {
        return m_loggedIn;
    }

    @Override
    public JSONObject toResponseJSON() throws JSONException {
        final JSONObject ret = super.toResponseJSON();
        ret.put("result", isLoggedIn());
        return ret;
    }
}
