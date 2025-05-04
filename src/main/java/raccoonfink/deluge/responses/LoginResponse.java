package raccoonfink.deluge.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import raccoonfink.deluge.DelugeException;

public final class LoginResponse extends DelugeResponse {
    private final boolean m_loggedIn;

    public LoginResponse(final Integer httpResponseCode, final JsonNode response) throws DelugeException {
        super(httpResponseCode, response);

        if (response.has("result")) {
            m_loggedIn = response.get("result").asBoolean();
        } else {
            m_loggedIn = false;
        }
    }

    public boolean isLoggedIn() {
        return m_loggedIn;
    }

    @Override
    public JsonNode toResponseJSON() {
        final ObjectNode ret = (ObjectNode) super.toResponseJSON();
        ret.put("result", isLoggedIn());
        return ret;
    }
}
