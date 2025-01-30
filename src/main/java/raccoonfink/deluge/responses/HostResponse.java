package raccoonfink.deluge.responses;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import raccoonfink.deluge.DelugeException;
import raccoonfink.deluge.Host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HostResponse extends DelugeResponse {
    private final List<Host> m_hosts = new ArrayList<Host>();

    public HostResponse(final Integer httpResponseCode, final JSONObject response, final boolean singleResult) throws DelugeException {
        super(httpResponseCode, response);
        try {
            final JSONArray result = response.getJSONArray("result");
            if (singleResult) {
                m_hosts.add(getHost(result));
            } else {
                for (int i = 0; i < result.length(); i++) {
                    final JSONArray host = result.getJSONArray(i);
                    m_hosts.add(getHost(host));
                }
            }
        } catch (final JSONException e) {
            throw new DelugeException(e);
        }
    }

    public List<Host> getHosts() {
        return Collections.unmodifiableList(m_hosts);
    }

    private Host getHost(final JSONArray host) throws JSONException {
        return new Host(host.getString(0), host.getString(1), host.getInt(2), host.getString(3), host.optString(4));
    }

    @Override
    public JSONObject toResponseJSON() throws JSONException {
        final JSONObject ret = super.toResponseJSON();
        for (final Host host : m_hosts) {
            ret.append("result", host.toJSON());
        }
        return ret;
    }
}
