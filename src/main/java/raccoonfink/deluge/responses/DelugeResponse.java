package raccoonfink.deluge.responses;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import raccoonfink.deluge.DelugeException;

public class DelugeResponse {

    private final int m_id;
    private final int m_responseCode;
    private final JsonNode m_result;

    public DelugeResponse(final Integer httpResponseCode, final JsonNode response) throws DelugeException {
        assert (httpResponseCode != null);


        try {
            // 确保"id"字段存在
            JsonNode idNode = response.get("id");
            if (idNode == null) {
                throw new DelugeException("Missing 'id' field in JSON: " + response);
            }

            // 尝试将节点转换为Integer（支持数值和可解析的字符串）
            m_id = JsonUtil.getObjectMapper().convertValue(idNode, Integer.class);
        } catch (IllegalArgumentException e) {
            throw new DelugeException("Invalid 'id' field in JSON: " + response, e);
        }
        m_responseCode = httpResponseCode;
        m_result = response;
    }

    public int getId() {
        return m_id;
    }

    public int getResponseCode() {
        return m_responseCode;
    }

    public JsonNode getResponseData() {
        return m_result;
    }

    public JsonNode toResponseJSON() {
        ObjectNode ret = JsonUtil.getObjectMapper().createObjectNode();
        ret.put("id", getId());
        ret.put("responseCode", getResponseCode());
        ret.putNull("result");
        return ret;
    }
}
