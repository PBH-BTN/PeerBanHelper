package raccoonfink.deluge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;

public final class DelugeRequest {
    private final String m_method;
    private final List<Object> m_params;
    private static final Gson gson = new Gson();

    public DelugeRequest(final String method, final Object... params) {
        m_method = method;
        m_params = Arrays.asList(params);
    }

    public String toPostData(final int id) {
        assert (id >= 0);
        final JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("method", m_method);
        json.add("params", gson.toJsonTree(m_params));
        return json.toString();
    }
}
