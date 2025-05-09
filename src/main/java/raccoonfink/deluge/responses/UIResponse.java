package raccoonfink.deluge.responses;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import raccoonfink.deluge.DelugeException;
import raccoonfink.deluge.Statistics;
import raccoonfink.deluge.Torrent;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public final class UIResponse extends DelugeResponse {
    private boolean m_connected = false;
    private Statistics m_statistics;
    private Set<Torrent> m_torrents = new TreeSet<Torrent>();

    @SuppressWarnings("rawtypes")
    public UIResponse(final Integer httpResponseCode, final JsonNode response) throws DelugeException {
        super(httpResponseCode, response);

        if (response.get("result").isNull()) {
            return;
        }

        try {
            final JsonNode result = response.get("result");
            m_connected = result.path("connected").asBoolean(false);

            m_statistics = new Statistics(result.get("stats"));

            // 此处以前是 optJSONObject 那么取不到应该是 null
            final JsonNode torrents = result.get("torrents");
            if (torrents != null && !torrents.isNull() && torrents.isObject()) {
                Iterator<String> it = torrents.fieldNames();
                while (it.hasNext()) {
                    final String key = it.next();
                    m_torrents.add(new Torrent(key, torrents.get(key)));
                }
            }
        } catch (final IllegalArgumentException | NullPointerException e) {
            throw new DelugeException(e);
        }
    }

    public boolean isConnected() {
        return m_connected;
    }

    public Statistics getStatistics() {
        return m_statistics;
    }

    public Set<Torrent> getTorrents() {
        return m_torrents;
    }

    @Override
    public JsonNode toResponseJSON() {
        final ObjectNode ret = (ObjectNode) super.toResponseJSON();
        ret.put("connected", m_connected);
        ret.set("statistics", m_statistics.toJSON());
        final ObjectNode torrents = JsonUtil.getObjectMapper().createObjectNode();
        ret.set("torrents", torrents);
        for (final Torrent torrent : m_torrents) {
            torrents.set(torrent.getKey(), torrent.toJSON());
        }
        return ret;
    }
}
