package com.ghostchu.peerbanhelper.util.maven;

public enum MavenCentralMirror {
    CENTRAL("US", "https://repo1.maven.org/maven2", "https://repo1.maven.org/maven2/net/kyori/adventure-api/4.9.1/adventure-api-4.9.1.pom"),
    APACHE("US", "https://repo.maven.apache.org/maven2", "https://repo.maven.apache.org/maven2/net/kyori/adventure-api/4.9.1/adventure-api-4.9.1.pom"),
    ALIYUN("CN", "https://maven.aliyun.com/repository/public", "https://maven.aliyun.com/repository/central/net/kyori/adventure-api/4.9.1/adventure-api-4.9.1.pom"),
    TENCENT("CN", "https://mirrors.cloud.tencent.com/nexus/repository/maven-public", "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/net/kyori/adventure-api/4.9.1/adventure-api-4.9.1.pom"),
    HUAWEI("CN", "https://repo.huaweicloud.com/repository/maven", "https://repo.huaweicloud.com/repository/maven/net/kyori/adventure-api/4.9.1/adventure-api-4.9.1.pom");

    private final String region;
    private final String repoUrl;
    private final String testUrl; // Test url must be a valid file in repository, the repository must return a 200 OK status code

    MavenCentralMirror(String region, String repoUrl, String testUrl) {
        this.region = region;
        this.repoUrl = repoUrl;
        this.testUrl = testUrl;
    }

    public String getRegion() {
        return region;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getTestUrl() {
        return testUrl;
    }
}
