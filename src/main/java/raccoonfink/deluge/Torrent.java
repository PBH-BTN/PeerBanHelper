package raccoonfink.deluge;

import com.google.gson.JsonObject;
import lombok.Data;

@Data
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

    public Torrent(final String key, final JsonObject data) {
        m_key = key;
        m_distributedCopies = data.has("distributed_copies") ? data.get("distributed_copies").getAsDouble() : 0.0;
        m_downloadPayloadRate = data.has("download_payload_rate") ? data.get("download_payload_rate").getAsLong() : 0L;
        m_eta = data.has("eta") ? data.get("eta").getAsLong() : 0L;
        m_autoManaged = data.has("is_auto_managed") && data.get("is_auto_managed").getAsBoolean();
        m_maxDownloadSpeed = data.has("max_download_speed") ? data.get("max_download_speed").getAsLong() : 0L;
        m_maxUploadSpeed = data.has("max_upload_speed") ? data.get("max_upload_speed").getAsLong() : 0L;
        m_name = data.has("name") ? data.get("name").getAsString() : "";
        m_numPeers = data.has("num_peers") ? data.get("num_peers").getAsLong() : 0L;
        m_numSeeds = data.has("num_seeds") ? data.get("num_seeds").getAsLong() : 0L;
        m_progress = data.has("progress") ? data.get("progress").getAsDouble() : 0.0;
        m_queue = data.has("queue") ? data.get("queue").getAsLong() : 0L;
        m_ratio = data.has("ratio") ? data.get("ratio").getAsDouble() : 0.0;
        m_savePath = data.has("save_path") ? data.get("save_path").getAsString() : "";
        m_seedsPeerRatio = data.has("seeds_peer_ratio") ? data.get("seeds_peer_ratio").getAsDouble() : 0.0;
        m_timeAdded = data.has("time_added") ? data.get("time_added").getAsDouble() : 0.0;
        m_totalDone = data.has("total_done") ? data.get("total_done").getAsLong() : 0L;
        m_totalPeers = data.has("total_peers") ? data.get("total_peers").getAsLong() : 0L;
        m_totalSeeds = data.has("total_seeds") ? data.get("total_seeds").getAsLong() : 0L;
        m_totalSize = data.has("total_size") ? data.get("total_size").getAsLong() : 0L;
        m_totalUploaded = data.has("total_uploaded") ? data.get("total_uploaded").getAsLong() : 0L;
        m_trackerHost = data.has("tracker_host") ? data.get("tracker_host").getAsString() : "";
        m_uploadPayloadRate = data.has("upload_payload_rate") ? data.get("upload_payload_rate").getAsLong() : 0L;

        final String state = data.has("state") ? data.get("state").getAsString() : "";
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

    public JsonObject toJSON() {
        final JsonObject ret = new JsonObject();
        ret.addProperty("key", m_key);
        ret.addProperty("distributed_copies", m_distributedCopies);
        ret.addProperty("download_payload_rate", m_downloadPayloadRate);
        ret.addProperty("eta", m_eta);
        ret.addProperty("auto_managed", m_autoManaged);
        ret.addProperty("max_download_speed", m_maxDownloadSpeed);
        ret.addProperty("max_upload_speed", m_maxUploadSpeed);
        ret.addProperty("name", m_name);
        ret.addProperty("num_peers", m_numPeers);
        ret.addProperty("num_seeds", m_numSeeds);
        ret.addProperty("progress", m_progress);
        ret.addProperty("queue", m_queue);
        ret.addProperty("ratio", m_ratio);
        ret.addProperty("save_path", m_savePath);
        ret.addProperty("seeds_peer_ratio", m_seedsPeerRatio);
        ret.addProperty("time_added", m_timeAdded);
        ret.addProperty("total_done", m_totalDone);
        ret.addProperty("total_peers", m_totalPeers);
        ret.addProperty("total_seeds", m_totalSeeds);
        ret.addProperty("total_size", m_totalSize);
        ret.addProperty("total_uploaded", m_totalUploaded);
        ret.addProperty("tracker_host", m_trackerHost);
        ret.addProperty("upload_payload_rate", m_uploadPayloadRate);
        ret.addProperty("state", m_state.toString());
        return ret;
    }

    public enum State {
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

