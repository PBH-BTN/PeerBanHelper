package raccoonfink.deluge.responses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import raccoonfink.deluge.DelugeEvent;
import raccoonfink.deluge.DelugeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EventsResponse extends DelugeResponse {
    private final List<DelugeEvent> m_events = new ArrayList<DelugeEvent>();

    public EventsResponse(final Integer httpResponseCode, final JsonNode result) throws DelugeException {
        super(httpResponseCode, result);

        if (!result.get("result").isNull()) {
//            try {
            final JsonNode res = result.get("result");
            for (int i = 0; i < res.size(); i++) {
                m_events.add(new DelugeEvent(res.get(i)));
            }
            // TODO: 是否有必要增加一些检查?
//            } catch (final JSONException e) {
//                throw new DelugeException(e);
//            }
        }
    }

    public List<DelugeEvent> getEvents() {
        return Collections.unmodifiableList(m_events);
    }

    @Override
    public JsonNode toResponseJSON() {
        final ObjectNode ret = (ObjectNode) super.toResponseJSON();
        ArrayNode resultArray = ret.putArray("result");
        for (final DelugeEvent ev : m_events) {
            // 使用 ArrayNode 的 add 方法添加 JsonNode
            resultArray.add(ev.toJSON());
        }
        return ret;
    }
}
