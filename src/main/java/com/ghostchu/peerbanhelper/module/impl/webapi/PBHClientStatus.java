package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderLastStatus;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import fi.iki.elonen.NanoHTTPD;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class PBHClientStatus extends AbstractFeatureModule implements PBHAPI {

    public PBHClientStatus(PeerBanHelperServer server, YamlConfiguration profile) {
      super(server,profile);
    }

    @Override
    public boolean shouldHandle(String uri) {
        return uri.equals("/api/clientStatus");
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Downloader downloader : getServer().getDownloaders()) {
            Map<String, Object> map = new HashMap<>(2);
            map.put("name", downloader.getName());
            map.put("type", downloader.getType());
            map.put("endpoint", downloader.getEndpoint());
            try {
                map.put("status", downloader.getLastStatus().name());
                long torrents = getServer().getLivePeersSnapshot().values()
                        .stream()
                        .filter(peerMetadata -> peerMetadata.getDownloader().equals(downloader.getName()))
                        .map(meta -> meta.getTorrent().getHash())
                        .distinct()
                        .count();
                long peers = getServer().getLivePeersSnapshot().values().size();
                map.put("torrents", torrents);
                map.put("peers", peers);
            } catch (Throwable th) {
                map.put("status", DownloaderLastStatus.ERROR);
            }
            list.add(map);
        }
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(list)));
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Client Status";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-clientstatus";
    }

    @Override
    public void onEnable() {
        getServer().getWebManagerServer().register(this);
    }

    @Override
    public void onDisable() {
        getServer().getWebManagerServer().unregister(this);
    }

}
