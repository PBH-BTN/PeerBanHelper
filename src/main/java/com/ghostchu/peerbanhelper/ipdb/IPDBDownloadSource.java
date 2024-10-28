package com.ghostchu.peerbanhelper.ipdb;

public record IPDBDownloadSource(String baseUrl, String databaseName, Boolean supportXzip) {
    public IPDBDownloadSource(String baseUrl, String databaseName) {
        this(baseUrl, databaseName, false);
    }

    public String getIPDBUrl() {
        return this.supportXzip ? this.baseUrl + databaseName + ".mmdb.xz" : this.baseUrl + databaseName + ".mmdb";
    }
}
