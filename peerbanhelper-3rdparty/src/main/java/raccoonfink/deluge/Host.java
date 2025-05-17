package raccoonfink.deluge;

import org.json.JSONException;
import org.json.JSONObject;

public final class Host {
    private final String m_id;
    private final String m_hostname;
    private final int m_port;
    private final Status m_status;
    private final String m_version;

    public Host(final String id, final String hostname, final int port, final String status, final String version) {
        m_id = id;
        m_hostname = hostname;
        m_port = port;
        m_status = Status.valueOf(status);
        m_version = version;
    }

    public String getId() {
        return m_id;
    }

    public String getHostname() {
        return m_hostname;
    }

    public int getPort() {
        return m_port;
    }

    public Status getStatus() {
        return m_status;
    }

    public String getVersion() {
        return m_version;
    }

    public JSONObject toJSON() throws JSONException {
        final JSONObject ret = new JSONObject();
        ret.put("id", m_id);
        ret.put("hostname", m_hostname);
        ret.put("status", m_status);
        ret.putOpt("version", m_version);
        return ret;
    }

    public static enum Status {
        Offline,
        Online,
        Connected
    }
}
