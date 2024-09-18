package com.ghostchu.peerbanhelper.ipdb;

public record IPDBDownloadSource(String baseUrl, String databaseName, Boolean supportGzip) {
    public IPDBDownloadSource(String baseUrl, String databaseName) {
        this(baseUrl, databaseName, false);
    }

    public String getIPDBUrl() {
        return this.supportGzip ? this.baseUrl + databaseName + ".mmdb.gz" : this.baseUrl + databaseName + ".mmdb";
    }
}
