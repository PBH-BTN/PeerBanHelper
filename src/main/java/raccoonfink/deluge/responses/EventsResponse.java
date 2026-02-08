package raccoonfink.deluge.responses;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import raccoonfink.deluge.DelugeEvent;
import raccoonfink.deluge.DelugeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EventsResponse extends DelugeResponse {
    private final List<DelugeEvent> m_events = new ArrayList<>();

    public EventsResponse(final Integer httpResponseCode, final JsonObject result) throws DelugeException {
        super(httpResponseCode, result);

        if (result.has("result") && !result.get("result").isJsonNull()) {
            final JsonArray res = result.getAsJsonArray("result");
            for (JsonElement element : res) {
                m_events.add(new DelugeEvent(element.getAsJsonArray()));
            }
        }
    }

    public List<DelugeEvent> getEvents() {
        return Collections.unmodifiableList(m_events);
    }

    @Override
    public JsonObject toResponseJSON() {
        final JsonObject ret = super.toResponseJSON();
        JsonArray resultArray = new JsonArray();
        for (final DelugeEvent ev : m_events) {
            resultArray.add(ev.toJSON());
        }
        ret.add("result", resultArray);
        return ret;
    }
}
