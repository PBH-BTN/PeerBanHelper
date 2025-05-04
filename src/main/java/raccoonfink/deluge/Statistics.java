package raccoonfink.deluge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;


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

    public Statistics(final JsonNode stats) {
        m_dhtNodes = stats.path("dht_nodes").asInt(0);
        m_downloadProtocolRate = stats.path("download_protocol_rate").asInt(0);
        m_downloadRate = stats.path("download_rate").asInt(0);
        m_freeSpace = stats.path("free_space").asInt(0);
        m_incomingConnections = stats.path("has_incoming_connections").asBoolean(false);
        m_maxDownload = stats.path("max_download").asDouble(0.0);
        m_maxNumConnections = stats.path("max_num_connections").asInt(0);
        m_maxUpload = stats.path("max_upload").asDouble(0.0);
        m_numConnections = stats.path("num_connections").asInt(0);
        m_uploadProtocolRate = stats.path("upload_protocol_rate").asInt(0);
        m_uploadRate = stats.path("upload_rate").asInt(0);
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

    public JsonNode toJSON() {
        ObjectNode ret = JsonUtil.getObjectMapper().createObjectNode();
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
