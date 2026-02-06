package raccoonfink.deluge;

import com.google.gson.JsonObject;

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

    public JsonObject toJSON() {
        final JsonObject ret = new JsonObject();
        ret.addProperty("id", m_id);
        ret.addProperty("hostname", m_hostname);
        ret.addProperty("status", m_status.toString());
        if (m_version != null) {
            ret.addProperty("version", m_version);
        }
        return ret;
    }

    public enum Status {
        Offline,
        Online,
        Connected
    }
}
