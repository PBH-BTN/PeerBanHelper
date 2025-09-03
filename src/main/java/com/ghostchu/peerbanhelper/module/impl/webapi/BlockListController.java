package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public final class BlockListController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
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
        for (IPAddress ipAddress : banList.copyKeySet()) {
            if (ipAddress == null) continue;
            String start = ipAddress.toPrefixBlock().getLower().withoutPrefixLength().toNormalizedString();
            String end = ipAddress.toPrefixBlock().getUpper().withoutPrefixLength().toNormalizedString();
            builder.append(start).append(" - ").append(end).append(" , 000 , ").append(ipAddress.toNormalizedString()).append("\n");
        }
        ctx.result(builder.toString());
    }

    private void blocklistP2pPlain(@NotNull Context ctx) {
        StringBuilder builder = new StringBuilder();
        for (IPAddress addr : banList.copyKeySet()) {
            String ruleName = UUID.randomUUID().toString().replace("-", "");
            String start = addr.toPrefixBlock().getLower().withoutPrefixLength().toNormalizedString();
            String end = addr.toPrefixBlock().getUpper().withoutPrefixLength().toNormalizedString();
            builder.append(ruleName).append(":").append(start).append("-").append(end).append("\n");
        }
        ctx.result(builder.toString());
    }

    private void blocklistIp(@NotNull Context ctx) {
        StringBuilder builder = new StringBuilder();
        for (IPAddress ipAddress : banList.copyKeySet()) {
            builder.append(ipAddress.toPrefixBlock().toNormalizedString()).append("\n");
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
