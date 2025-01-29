package raccoonfink.deluge.responses;

import org.json.JSONException;
import org.json.JSONObject;
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
    public UIResponse(final Integer httpResponseCode, final JSONObject response) throws DelugeException {
        super(httpResponseCode, response);

        if (response == null) {
            return;
        }

        try {
            final JSONObject result = response.getJSONObject("result");
            m_connected = result.optBoolean("connected");

            m_statistics = new Statistics(result.getJSONObject("stats"));

            final JSONObject torrents = result.optJSONObject("torrents");
            if (torrents != null) {
                final Iterator it = torrents.keys();
                while (it.hasNext()) {
                    final String key = (String) it.next();
                    m_torrents.add(new Torrent(key, torrents.getJSONObject(key)));

                }
            }
        } catch (final JSONException e) {
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
    public JSONObject toResponseJSON() throws JSONException {
        final JSONObject ret = super.toResponseJSON();
        ret.put("connected", m_connected);
        ret.put("statistics", m_statistics.toJSON());
        final JSONObject torrents = new JSONObject();
        ret.put("torrents", torrents);
        for (final Torrent torrent : m_torrents) {
            torrents.put(torrent.getKey(), torrent.toJSON());
        }
        return ret;
    }
}
