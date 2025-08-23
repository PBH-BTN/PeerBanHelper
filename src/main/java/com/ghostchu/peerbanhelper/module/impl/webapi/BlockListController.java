package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public final class BlockListController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private DownloaderServer downloaderServer;
    @Autowired
    private BanList banList;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public void onEnable() {
        webContainer.javalin()
                .get("/blocklist/ip", this::blocklistIp)
                .get("/blocklist/p2p-plain-format", this::blocklistP2pPlain, Role.ANYONE)
                .get("/blocklist/dat-emule", this::blocklistDatEmule, Role.ANYONE);
    }

    private void blocklistDatEmule(@NotNull Context ctx) {
        StringBuilder builder = new StringBuilder();
        for (PeerAddress addr : banList.copyKeySet()) {
            IPAddress ipAddress = IPAddressUtil.getIPAddress(addr.getIp());
            if (ipAddress == null) continue;
            String fullIp = ipAddress.toFullString();
            builder.append(fullIp).append(" - ").append(fullIp).append(" , 000 , ").append(UUID.randomUUID().toString().replace("-", "")).append("\n");
        }
        ctx.result(builder.toString());
    }

    private void blocklistP2pPlain(@NotNull Context ctx) {
        StringBuilder builder = new StringBuilder();
        for (PeerAddress addr : banList.copyKeySet()) {
            String ruleName = UUID.randomUUID().toString().replace("-", "");
            String start = addr.getIp();
            String end = addr.getIp();
            builder.append(ruleName).append(":").append(start).append("-").append(end).append("\n");
        }
        ctx.result(builder.toString());
    }

    private void blocklistIp(@NotNull Context ctx) {
        StringBuilder builder = new StringBuilder();
        for (PeerAddress addr : banList.copyKeySet()) {
            builder.append(addr.getIp()).append("\n");
        }
        ctx.result(builder.toString());
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
}
