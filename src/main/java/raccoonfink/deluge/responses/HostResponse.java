package raccoonfink.deluge.responses;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import raccoonfink.deluge.DelugeException;
import raccoonfink.deluge.Host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HostResponse extends DelugeResponse {
    private final List<Host> m_hosts = new ArrayList<>();

    public HostResponse(final Integer httpResponseCode, final JsonObject response, final boolean singleResult) throws DelugeException {
        super(httpResponseCode, response);
        if (!response.has("result")) {
            throw new DelugeException("Missing 'result' field in JSON response");
        }
        final JsonArray result = response.getAsJsonArray("result");
        if (singleResult) {
            m_hosts.add(getHost(result));
        } else {
            for (JsonElement element : result) {
                final JsonArray host = element.getAsJsonArray();
                m_hosts.add(getHost(host));
            }
        }
    }

    public List<Host> getHosts() {
        return Collections.unmodifiableList(m_hosts);
    }

    private Host getHost(final JsonArray host) {
        String id = host.get(0).getAsString();
        String hostname = host.get(1).getAsString();
        int port = host.get(2).getAsInt();
        String status = host.get(3).getAsString();
        String version = host.size() > 4 ? host.get(4).getAsString() : null;
        return new Host(id, hostname, port, status, version);
    }

    @Override
    public JsonObject toResponseJSON() {
        final JsonObject ret = super.toResponseJSON();
        JsonArray resultArray = new JsonArray();
        for (final Host host : m_hosts) {
            resultArray.add(host.toJSON());
        }
        ret.add("result", resultArray);
        return ret;
    }
}
