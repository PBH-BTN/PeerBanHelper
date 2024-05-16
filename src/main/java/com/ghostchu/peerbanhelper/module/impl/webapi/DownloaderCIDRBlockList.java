package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import io.javalin.http.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class DownloaderCIDRBlockList extends AbstractFeatureModule {
    public DownloaderCIDRBlockList(PeerBanHelperServer server, YamlConfiguration profile) {
       super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public void onEnable() {
        getServer().getJavalinWebContainer().getJavalin()
                .get("/blocklist/transmission", ctx -> {
                    StringBuilder builder = new StringBuilder();
                    for (Map.Entry<PeerAddress, BanMetadata> pair : getServer().getBannedPeers().entrySet()) {
                        builder.append(IPAddressUtil.getIPAddress(pair.getKey().getIp()).assignPrefixForSingleBlock().toString()).append("\n");
                    }
                    ctx.status(HttpStatus.OK);
                    ctx.result(builder.toString());
                });
    }

    @Override
    public void onDisable() {

    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Downloader CIDR Blocklist";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-downloader-cidr-blocklist";
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    static class BanResponse {
        private String address;
        private BanMetadata banMetadata;
    }
}
