package raccoonfink.deluge.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import raccoonfink.deluge.DelugeException;
import raccoonfink.deluge.Host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HostResponse extends DelugeResponse {
    private final List<Host> m_hosts = new ArrayList<Host>();

    public HostResponse(final Integer httpResponseCode, final JsonNode response, final boolean singleResult) throws DelugeException {
        super(httpResponseCode, response);
        final JsonNode resultNode = response.get("result");
        if (resultNode == null || !resultNode.isArray()) {
            throw new DelugeException("Invalid or missing 'result' array in response");
        }
        final ArrayNode result = (ArrayNode) resultNode;
        if (singleResult) {
            m_hosts.add(getHost(result.get(0)));
        } else {
            for (int i = 0; i < result.size(); i++) {
                // TODO: 不知道 Deluge 的返回是什么样子
                final JsonNode host = result.get(i);
                m_hosts.add(getHost(host));
            }
        }

    }

    public List<Host> getHosts() {
        return Collections.unmodifiableList(m_hosts);
    }

    private Host getHost(final JsonNode host) {
        return new Host(host.get(0).asText(), host.get(1).asText(), host.get(2).asInt(), host.get(3).asText(), host.get(4).asText(""));
    }

    @Override
    public JsonNode toResponseJSON() {
        final ObjectNode ret = (ObjectNode) super.toResponseJSON();
        // 获取名为 "result" 的 ArrayNode，如果不存在则创建一个新的
        ArrayNode resultArray = (ArrayNode) ret.get("result");
        if (resultArray == null) {
            resultArray = ret.putArray("result");
        }
        for (final Host host : m_hosts) {
            // 使用 ArrayNode 的 add 方法添加 JsonNode
            resultArray.add(host.toJSON());
        }
        return ret;
    }
}
