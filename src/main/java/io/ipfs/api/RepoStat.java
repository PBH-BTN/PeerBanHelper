package io.ipfs.api;

import java.util.Map;

public class RepoStat {

    public final long RepoSize;
    public final long StorageMax;
    public final long NumObjects;
    public final String RepoPath;
    public final String Version;

    public RepoStat(long repoSize, long storageMax, long numObjects, String repoPath, String version ) {
        this.RepoSize = repoSize;
        this.StorageMax = storageMax;
        this.NumObjects = numObjects;
        this.RepoPath = repoPath;
        this.Version = version;
    }
    public static RepoStat fromJson(Object rawjson) {
        Map json = (Map)rawjson;
        long repoSize = Long.parseLong(json.get("RepoSize").toString());
        long storageMax = Long.parseLong(json.get("StorageMax").toString());
        long numObjects = Long.parseLong(json.get("NumObjects").toString());
        String repoPath = (String)json.get("RepoPath");
        String version = (String)json.get("Version");

        return new RepoStat(repoSize, storageMax, numObjects, repoPath, version);
    }
}
