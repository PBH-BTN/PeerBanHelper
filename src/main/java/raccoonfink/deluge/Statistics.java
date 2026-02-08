package raccoonfink.deluge;

import com.google.gson.JsonObject;

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

    public Statistics(final JsonObject stats) {
        m_dhtNodes = stats.has("dht_nodes") ? stats.get("dht_nodes").getAsInt() : 0;
        m_downloadProtocolRate = stats.has("download_protocol_rate") ? stats.get("download_protocol_rate").getAsInt() : 0;
        m_downloadRate = stats.has("download_rate") ? stats.get("download_rate").getAsInt() : 0;
        m_freeSpace = stats.has("free_space") ? stats.get("free_space").getAsInt() : 0;
        m_incomingConnections = stats.has("has_incoming_connections") && stats.get("has_incoming_connections").getAsBoolean();
        m_maxDownload = stats.has("max_download") ? stats.get("max_download").getAsDouble() : 0.0;
        m_maxNumConnections = stats.has("max_num_connections") ? stats.get("max_num_connections").getAsInt() : 0;
        m_maxUpload = stats.has("max_upload") ? stats.get("max_upload").getAsDouble() : 0.0;
        m_numConnections = stats.has("num_connections") ? stats.get("num_connections").getAsInt() : 0;
        m_uploadProtocolRate = stats.has("upload_protocol_rate") ? stats.get("upload_protocol_rate").getAsInt() : 0;
        m_uploadRate = stats.has("upload_rate") ? stats.get("upload_rate").getAsInt() : 0;
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

    public JsonObject toJSON() {
        final JsonObject ret = new JsonObject();
        ret.addProperty("dht_nodes", m_dhtNodes);
        ret.addProperty("download_protocol_rate", m_downloadProtocolRate);
        ret.addProperty("download_rate", m_downloadRate);
        ret.addProperty("free_space", m_freeSpace);
        ret.addProperty("incoming_connections", m_incomingConnections);
        ret.addProperty("max_download", m_maxDownload);
        ret.addProperty("max_num_connections", m_maxNumConnections);
        ret.addProperty("max_upload", m_maxUpload);
        ret.addProperty("num_connections", m_numConnections);
        ret.addProperty("upload_protocol_rate", m_uploadProtocolRate);
        ret.addProperty("upload_rate", m_uploadRate);
        return ret;
    }
}
