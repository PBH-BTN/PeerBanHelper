package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@IgnoreScan
public final class BlockListController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public void onEnable() {
        webContainer.javalin()
                .get("/blocklist/ip", ctx -> {
                    StringBuilder builder = new StringBuilder();
                    for (Map.Entry<PeerAddress, BanMetadata> pair : getServer().getBannedPeers().entrySet()) {
                        builder.append(pair.getKey().getIp()).append("\n");
                    }
                    ctx.result(builder.toString());
                })
                .get("/blocklist/p2p-plain-format", ctx -> {
                    StringBuilder builder = new StringBuilder();
                    for (Map.Entry<PeerAddress, BanMetadata> pair : getServer().getBannedPeers().entrySet()) {
                        String ruleName = UUID.randomUUID().toString().replace("-", "");
                        String start = pair.getKey().getIp();
                        String end = pair.getKey().getIp();
                        builder.append(ruleName).append(":").append(start).append("-").append(end).append("\n");
                    }
                    ctx.result(builder.toString());
                }, Role.ANYONE)
                .get("/blocklist/dat-emule", ctx -> {
                    StringBuilder builder = new StringBuilder();
                    for (Map.Entry<PeerAddress, BanMetadata> pair : getServer().getBannedPeers().entrySet()) {
                        IPAddress ipAddress = IPAddressUtil.getIPAddress(pair.getKey().getIp());
                        if (ipAddress == null) continue;
                        String fullIp = ipAddress.toFullString();
                        builder.append(fullIp).append(" - ").append(fullIp).append(" , 000 , ").append(UUID.randomUUID().toString().replace("-", "")).append("\n");
                    }
                    ctx.result(builder.toString());
                }, Role.ANYONE);
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
