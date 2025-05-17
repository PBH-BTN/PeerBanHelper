package raccoonfink.deluge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

public final class DelugeRequest {
    private final String m_method;
    private final List<Object> m_params;

    public DelugeRequest(final String method, final Object... params) {
        m_method = method;
        m_params = Arrays.asList(params);
    }

    public String toPostData(final int id) throws JSONException {
        assert (id >= 0);
        final JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("method", m_method);
        json.put("params", new JSONArray(m_params));
        return json.toString();
    }
}
