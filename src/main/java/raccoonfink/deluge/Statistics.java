package raccoonfink.deluge;

import org.json.JSONException;
import org.json.JSONObject;

public final class Statistics {

    private final int m_dhtNodes;
    private final int m_downloadProtocolRate;
    private final int m_downloadRate;
    private final int m_freeSpace;
    private final boolean m_incomingConnections;
    private final double m_maxDownload;
    private final int m_maxNumConnections;
    private final double m_maxUpload;
    private final int m_numConnections;
    private final int m_uploadProtocolRate;
    private final int m_uploadRate;

    public Statistics(final JSONObject stats) {
        m_dhtNodes = stats.optInt("dht_nodes");
        m_downloadProtocolRate = stats.optInt("download_protocol_rate");
        m_downloadRate = stats.optInt("download_rate");
        m_freeSpace = stats.optInt("free_space");
        m_incomingConnections = stats.optBoolean("has_incoming_connections");
        m_maxDownload = stats.optDouble("max_download");
        m_maxNumConnections = stats.optInt("max_num_connections");
        m_maxUpload = stats.optDouble("max_upload");
        m_numConnections = stats.optInt("num_connections");
        m_uploadProtocolRate = stats.optInt("upload_protocol_rate");
        m_uploadRate = stats.optInt("upload_rate");
    }

    public int getDHTNodes() {
        return m_dhtNodes;
    }

    public int getDownloadProtocolRate() {
        return m_downloadProtocolRate;
    }

    public int getDownloadRate() {
        return m_downloadRate;
    }

    public int getFreeSpace() {
        return m_freeSpace;
    }

    public boolean hasIncomingConnections() {
        return m_incomingConnections;
    }

    public double getMaxDownload() {
        return m_maxDownload;
    }

    public int getMaxNumConnections() {
        return m_maxNumConnections;
    }

    public double getMaxUpload() {
        return m_maxUpload;
    }

    public int getNumConnections() {
        return m_numConnections;
    }

    public int getUploadProtocolRate() {
        return m_uploadProtocolRate;
    }

    public int getUploadRate() {
        return m_uploadRate;
    }

    public JSONObject toJSON() throws JSONException {
        final JSONObject ret = new JSONObject();
        ret.put("dht_nodes", m_dhtNodes);
        ret.put("download_protocol_rate", m_downloadProtocolRate);
        ret.put("download_rate", m_downloadRate);
        ret.put("free_space", m_freeSpace);
        ret.put("incoming_connections", m_incomingConnections);
        ret.put("max_download", m_maxDownload);
        ret.put("max_num_connections", m_maxNumConnections);
        ret.put("max_upload", m_maxUpload);
        ret.put("num_connections", m_numConnections);
        ret.put("upload_protocol_rate", m_uploadProtocolRate);
        ret.put("upload_rate", m_uploadRate);
        return ret;
    }
}
