package raccoonfink.deluge;

import org.json.JSONException;
import org.json.JSONObject;

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

    public Torrent(final String key, final JSONObject data) {
        m_key = key;
        m_distributedCopies = data.optDouble("distributed_copies");
        m_downloadPayloadRate = data.optLong("download_payload_rate");
        m_eta = data.optLong("eta");
        m_autoManaged = data.optBoolean("is_auto_managed");
        m_maxDownloadSpeed = data.optLong("max_download_speed");
        m_maxUploadSpeed = data.optLong("max_upload_speed");
        m_name = data.optString("name");
        m_numPeers = data.optLong("num_peers");
        m_numSeeds = data.optLong("num_seeds");
        m_progress = data.optDouble("progress");
        m_queue = data.optLong("queue");
        m_ratio = data.optDouble("ratio");
        m_savePath = data.optString("save_path");
        m_seedsPeerRatio = data.optDouble("seeds_peer_ratio");
        m_timeAdded = data.optDouble("time_added");
        m_totalDone = data.optLong("total_done");
        m_totalPeers = data.optLong("total_peers");
        m_totalSeeds = data.optLong("total_seeds");
        m_totalSize = data.optLong("total_size");
        m_totalUploaded = data.optLong("total_uploaded");
        m_trackerHost = data.optString("tracker_host");
        m_uploadPayloadRate = data.optLong("upload_payload_rate");

        final String state = data.optString("state");
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

    public JSONObject toJSON() throws JSONException {
        final JSONObject ret = new JSONObject();
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
        ret.put("state", m_state);
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

