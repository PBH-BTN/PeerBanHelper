package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import inet.ipaddr.IPAddress;
import io.javalin.http.Context;
import io.javalin.openapi.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
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
        webContainer.routes()
                .get("/blocklist/ip", this::blocklistIp)
                .get("/blocklist/p2p-plain-format", this::blocklistP2pPlain, Role.ANYONE)
                .get("/blocklist/dat-emule", this::blocklistDatEmule, Role.ANYONE);
    }

    @OpenApi(
            path = "/blocklist/dat-emule",
            methods = HttpMethod.GET,
            summary = "eMule DAT 格式封禁列表",
            description = "以 eMule DAT 文本格式导出当前封禁 IP 列表",
            tags = {"封禁列表"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(mimeType = "text/plain"))
            },
            operationId = "blocklistDatEmule"
    )
    private void blocklistDatEmule(@NotNull Context ctx) {
        // Deduplicate remapped IPs using LinkedHashSet to maintain insertion order
        Set<IPAddress> remappedIps = new LinkedHashSet<>();
        for (IPAddress ipAddress : banList.copyKeySet()) {
            if (ipAddress == null) continue;
            var ipAddresses = IPAddressUtil.remapBanListAddress(ipAddress);
            remappedIps.addAll(ipAddresses);
        }
        
        StringBuilder builder = new StringBuilder();
        for (IPAddress ipAddress : remappedIps) {
            String start = ipAddress.toPrefixBlock().getLower().withoutPrefixLength().toCompressedString();
            String end = ipAddress.toPrefixBlock().getUpper().withoutPrefixLength().toCompressedString();
            builder.append(start).append(" - ").append(end).append(" , 000 , ").append(ipAddress.toCompressedString()).append("\n");
        }
        ctx.result(builder.toString());
    }

    @OpenApi(
            path = "/blocklist/p2p-plain-format",
            methods = HttpMethod.GET,
            summary = "P2P 明文格式封禁列表",
            description = "以 P2P 明文格式导出当前封禁 IP 列表",
            tags = {"封禁列表"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(mimeType = "text/plain"))
            },
            operationId = "blocklistP2pPlain"
    )
    private void blocklistP2pPlain(@NotNull Context ctx) {
        Set<IPAddress> remappedIps = new LinkedHashSet<>();
        for (IPAddress addr : banList.copyKeySet()) {
            var ipAddresses = IPAddressUtil.remapBanListAddress(addr);
            remappedIps.addAll(ipAddresses);
        }
        
        StringBuilder builder = new StringBuilder();
        for (IPAddress addr : remappedIps) {
            String ruleName = UUID.randomUUID().toString().replace("-", "");
            String start = addr.toPrefixBlock().getLower().withoutPrefixLength().toCompressedString();
            String end = addr.toPrefixBlock().getUpper().withoutPrefixLength().toCompressedString();
            builder.append(ruleName).append(":").append(start).append("-").append(end).append("\n");
        }
        var result = builder.toString();
        var userAgent = ctx.userAgent();
        if(result.isBlank() && userAgent != null && userAgent.startsWith("Transmission")){
            result = "TransmissionWorkaround:127.127.127.127-127.127.127.127";
        }
        ctx.result(result);
    }

    @OpenApi(
            path = "/blocklist/ip",
            methods = HttpMethod.GET,
            summary = "获取 IP 封禁列表",
            description = "以纯文本格式导出当前封禁 IP 列表",
            tags = {"封禁列表"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(mimeType = "text/plain"))
            },
            operationId = "blocklistIp"
    )
    private void blocklistIp(@NotNull Context ctx) {
        // Deduplicate remapped IPs using LinkedHashSet to maintain insertion order
        Set<IPAddress> remappedIps = new LinkedHashSet<>();
        for (IPAddress ipAddress : banList.copyKeySet()) {
            var ipAddresses = IPAddressUtil.remapBanListAddress(ipAddress);
            remappedIps.addAll(ipAddresses);
        }
        
        StringBuilder builder = new StringBuilder();
        for (IPAddress ipAddress : remappedIps) {
            builder.append(ipAddress.toPrefixBlock().toCompressedString()).append("\n");
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
