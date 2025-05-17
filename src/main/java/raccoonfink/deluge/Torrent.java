package raccoonfink.deluge;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;

public final class Torrent implements Comparable<Torrent> {
    private final String m_key;
    private final double m_distributedCopies;
    private final long m_downloadPayloadRate;
    private final long m_eta;
    private final boolean m_autoManaged;
    private final long m_maxDownloadSpeed;
    private final long m_maxUploadSpeed;
    private final String m_name;
    private final long m_numPeers;
    private final long m_numSeeds;
    private final double m_progress;
    private final long m_queue;
    private final double m_ratio;
    private final String m_savePath;
    private final double m_seedsPeerRatio;
    private final double m_timeAdded;
    private final long m_totalDone;
    private final long m_totalPeers;
    private final long m_totalSeeds;
    private final long m_totalSize;
    private final long m_totalUploaded;
    private final String m_trackerHost;
    private final long m_uploadPayloadRate;
    private final State m_state;

    public Torrent(final String key, final JsonNode data) {
        m_key = key;
        m_distributedCopies = data.path("distributed_copies").asDouble(0.0);
        m_downloadPayloadRate = data.path("download_payload_rate").asLong(0L);
        m_eta = data.path("eta").asLong(0L);
        m_autoManaged = data.path("is_auto_managed").asBoolean(false);
        m_maxDownloadSpeed = data.path("max_download_speed").asLong(0L);
        m_maxUploadSpeed = data.path("max_upload_speed").asLong(0L);
        m_name = data.path("name").asText("");
        m_numPeers = data.path("num_peers").asLong(0L);
        m_numSeeds = data.path("num_seeds").asLong(0L);
        m_progress = data.path("progress").asDouble(0.0);
        m_queue = data.path("queue").asLong(0L);
        m_ratio = data.path("ratio").asDouble(0.0);
        m_savePath = data.path("save_path").asText("");
        m_seedsPeerRatio = data.path("seeds_peer_ratio").asDouble(0.0);
        m_timeAdded = data.path("time_added").asDouble(0.0);
        m_totalDone = data.path("total_done").asLong(0L);
        m_totalPeers = data.path("total_peers").asLong(0L);
        m_totalSeeds = data.path("total_seeds").asLong(0L);
        m_totalSize = data.path("total_size").asLong(0L);
        m_totalUploaded = data.path("total_uploaded").asLong(0L);
        m_trackerHost = data.path("tracker_host").asText("");
        m_uploadPayloadRate = data.path("upload_payload_rate").asLong(0L);

        final String state = data.path("state").asText("");
        if ("Downloading Metadata".equals(state)) {
            m_state = State.Downloading_Metadata;
        } else if ("Checking Resume Data".equals(state)) {
            m_state = State.Checking_Resume_Data;
        } else {
            m_state = State.valueOf(state);
        }

    }

    public String getKey() {
        return m_key;
    }

    public double getDistributedCopies() {
        return m_distributedCopies;
    }

    public long getDownloadPayloadRate() {
        return m_downloadPayloadRate;
    }

    public long getEta() {
        return m_eta;
    }

    public boolean isAutoManaged() {
        return m_autoManaged;
    }

    public long getMaxDownloadSpeed() {
        return m_maxDownloadSpeed;
    }

    public long getMaxUploadSpeed() {
        return m_maxUploadSpeed;
    }

    public String getName() {
        return m_name;
    }

    public long getNumPeers() {
        return m_numPeers;
    }

    public long getNumSeeds() {
        return m_numSeeds;
    }

    public double getProgress() {
        return m_progress;
    }

    public long getQueue() {
        return m_queue;
    }

    public double getRatio() {
        return m_ratio;
    }

    public String getSavePath() {
        return m_savePath;
    }

    public double getSeedsPeerRatio() {
        return m_seedsPeerRatio;
    }

    public double getTimeAdded() {
        return m_timeAdded;
    }

    public long getTotalDone() {
        return m_totalDone;
    }

    public long getTotalPeers() {
        return m_totalPeers;
    }

    public long getTotalSeeds() {
        return m_totalSeeds;
    }

    public long getTotalSize() {
        return m_totalSize;
    }

    public long getTotalUploaded() {
        return m_totalUploaded;
    }

    public String getTrackerHost() {
        return m_trackerHost;
    }

    public long getUploadPayloadRate() {
        return m_uploadPayloadRate;
    }

    public State getState() {
        return m_state;
    }

    public int compareTo(final Torrent torrent) {
        return this.getName().compareTo(torrent.getName());
    }

    public JsonNode toJSON() {
        ObjectNode ret = JsonUtil.getObjectMapper().createObjectNode();
        ret.put("key", m_key);
        ret.put("distributed_copies", m_distributedCopies);
        ret.put("download_payload_rate", m_downloadPayloadRate);
        ret.put("eta", m_eta);
        ret.put("auto_managed", m_autoManaged);
        ret.put("max_download_speed", m_maxDownloadSpeed);
        ret.put("max_upload_speed", m_maxUploadSpeed);
        ret.put("name", m_name);
        ret.put("num_peers", m_numPeers);
        ret.put("num_seeds", m_numSeeds);
        ret.put("progress", m_progress);
        ret.put("queue", m_queue);
        ret.put("ratio", m_ratio);
        ret.put("save_path", m_savePath);
        ret.put("seeds_peer_ratio", m_seedsPeerRatio);
        ret.put("time_added", m_timeAdded);
        ret.put("total_done", m_totalDone);
        ret.put("total_peers", m_totalPeers);
        ret.put("total_seeds", m_totalSeeds);
        ret.put("total_size", m_totalSize);
        ret.put("total_uploaded", m_totalUploaded);
        ret.put("tracker_host", m_trackerHost);
        ret.put("upload_payload_rate", m_uploadPayloadRate);
        ret.put("state", m_state.name());
        return ret;
    }

    public static enum State {
        Queued,
        Checking,
        Downloading_Metadata,
        Downloading,
        Finished,
        Seeding,
        Allocating,
        Checking_Resume_Data
    }

}

