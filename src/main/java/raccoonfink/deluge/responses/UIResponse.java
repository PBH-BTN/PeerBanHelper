package raccoonfink.deluge.responses;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import raccoonfink.deluge.DelugeException;
import raccoonfink.deluge.Statistics;
import raccoonfink.deluge.Torrent;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class UIResponse extends DelugeResponse {
    private boolean m_connected = false;
    private Statistics m_statistics;
    private final Set<Torrent> m_torrents = new TreeSet<>();

    public UIResponse(final Integer httpResponseCode, final JsonObject response) throws DelugeException {
        super(httpResponseCode, response);

        if (response == null) {
            return;
        }

        if (!response.has("result")) {
            return;
        }

        final JsonObject result = response.getAsJsonObject("result");
        m_connected = result.has("connected") && result.get("connected").getAsBoolean();

        m_statistics = new Statistics(result.getAsJsonObject("stats"));

        if (result.has("torrents") && !result.get("torrents").isJsonNull()) {
            final JsonObject torrents = result.getAsJsonObject("torrents");
            for (Map.Entry<String, JsonElement> entry : torrents.entrySet()) {
                final String key = entry.getKey();
                m_torrents.add(new Torrent(key, entry.getValue().getAsJsonObject()));
            }
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
    public JsonObject toResponseJSON() {
        final JsonObject ret = super.toResponseJSON();
        ret.addProperty("connected", m_connected);
        ret.add("statistics", m_statistics.toJSON());
        final JsonObject torrents = new JsonObject();
        ret.add("torrents", torrents);
        for (final Torrent torrent : m_torrents) {
            torrents.add(torrent.getKey(), torrent.toJSON());
        }
        return ret;
    }
}
