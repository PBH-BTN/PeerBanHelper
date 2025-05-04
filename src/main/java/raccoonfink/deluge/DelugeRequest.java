package raccoonfink.deluge;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;

import java.util.Arrays;
import java.util.List;

public final class DelugeRequest {
    private final String m_method;
    private final List<Object> m_params;

    public DelugeRequest(final String method, final Object... params) {
        m_method = method;
        m_params = Arrays.asList(params);
    }

    public String toPostData(final int id) {
        assert (id >= 0);
        ObjectNode json = JsonUtil.getObjectMapper().createObjectNode();
        json.put("id", id);
        json.put("method", m_method);
        ArrayNode paramsNode = JsonUtil.getObjectMapper().valueToTree(m_params);
        json.set("params", paramsNode);
        return json.toString();
    }
}
