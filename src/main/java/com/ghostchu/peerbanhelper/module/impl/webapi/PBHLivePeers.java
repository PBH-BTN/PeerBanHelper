package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import com.ghostchu.peerbanhelper.wrapper.PeerMetadata;
import fi.iki.elonen.NanoHTTPD;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.stream.Stream;

public class PBHLivePeers extends AbstractFeatureModule implements PBHAPI {

    public PBHLivePeers(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public boolean shouldHandle(String uri) {
        return uri.equals("/api/livePeers");
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        var peersResponseList = getPeersResponseStream();
        String JSON = JsonUtil.prettyPrinting().toJson(peersResponseList.toList());
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JSON));
    }

    private @NotNull Stream<PeerMetadata> getPeersResponseStream() {
        return getServer().getLivePeersSnapshot()
                .values()
                .stream()
                .sorted(Comparator.comparing(o -> o.getPeer().getAddress().getIp()));
    }

    @Override
    public void onEnable() {
        getServer().getWebManagerServer().register(this);
    }

    @Override
    public void onDisable() {
        getServer().getWebManagerServer().unregister(this);
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - PBH LivePeers";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-live-peers";
    }
}
