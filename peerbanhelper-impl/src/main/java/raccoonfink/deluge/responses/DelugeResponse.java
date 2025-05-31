package raccoonfink.deluge.responses;

import org.json.JSONException;
import org.json.JSONObject;
import raccoonfink.deluge.DelugeException;

public class DelugeResponse {

    private final int m_id;
    private final int m_responseCode;
    private final JSONObject m_result;

    public DelugeResponse(final Integer httpResponseCode, final JSONObject response) throws DelugeException {
        assert (httpResponseCode != null);

        try {
            m_id = response.getInt("id");
        } catch (JSONException e) {
            throw new DelugeException("Invalid 'id' field in JSON: " + response.toString(), e);
        }
        m_responseCode = httpResponseCode.intValue();
        m_result = response;
    }

    public int getId() {
        return m_id;
    }

    public int getResponseCode() {
        return m_responseCode;
    }

    public JSONObject getResponseData() {
        return m_result;
    }

    public JSONObject toResponseJSON() throws JSONException {
        final JSONObject ret = new JSONObject();
        ret.put("id", getId());
        ret.put("responseCode", getResponseCode());
        ret.put("result", JSONObject.NULL);
        return ret;
    }
}
