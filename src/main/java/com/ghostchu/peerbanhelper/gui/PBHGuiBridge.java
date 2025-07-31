package com.ghostchu.peerbanhelper.gui;

import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.database.dao.impl.tmp.TrackedSwarmDao;
import com.ghostchu.peerbanhelper.database.table.AlertEntity;
import com.ghostchu.peerbanhelper.metric.BasicMetrics;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class PBHGuiBridge {
    private final JavalinWebContainer javalinWebContainer;
    private final AlertManager alertManager;
    private final BasicMetrics metrics;
    private final DownloaderServer downloaderServer;
    private final TrackedSwarmDao trackedSwarmDao;

    public PBHGuiBridge(JavalinWebContainer javalinWebContainer, AlertManager alertManager,
                        @Qualifier("persistMetrics") BasicMetrics metrics,
                        DownloaderServer downloaderServer, TrackedSwarmDao trackedSwarmDao) {
        this.javalinWebContainer = javalinWebContainer;
        this.alertManager = alertManager;
        this.metrics = metrics;
        this.downloaderServer = downloaderServer;
        this.trackedSwarmDao = trackedSwarmDao;
    }

    public Optional<String> getWebUiToken() {
        return Optional.ofNullable(javalinWebContainer.getToken());
    }

    public Optional<URI> getWebUiUrl(){
        if(javalinWebContainer.isStarted()){
            return Optional.of(URI.create("http://127.0.0.1:" +javalinWebContainer.javalin().port() + "?token=" + javalinWebContainer.getToken()));
        }else{
            return  Optional.empty();
        }

    }

    public List<AlertEntity> getAlerts() {
        return alertManager.getUnreadAlerts();
    }

    /**
     * Get basic statistics similar to the WebUI dashboard
     */
    public Map<String, Object> getBasicStatistics() {
        Map<String, Object> map = new HashMap<>();
        map.put("checkCounter", metrics.getCheckCounter());
        map.put("peerBanCounter", metrics.getPeerBanCounter());
        map.put("peerUnbanCounter", metrics.getPeerUnbanCounter());
        map.put("banlistCounter", downloaderServer.getBannedPeers().size());
        map.put("bannedIpCounter", downloaderServer.getBannedPeers().keySet().stream().map(PeerAddress::getIp).distinct().count());
        map.put("wastedTraffic", metrics.getWastedTraffic());
        
        try {
            long trackedPeers = trackedSwarmDao.countOf();
            map.put("trackedSwarmCount", trackedPeers);
            if(trackedPeers > 0) {
                map.put("peersBlockRate", (double) metrics.getPeerBanCounter() / trackedPeers);
            }else{
                map.put("peersBlockRate", 0.0d);
            }
        } catch (SQLException e) {
            map.put("peersBlockRate", 0.0d);
            map.put("trackedSwarmCount", 0L);
            log.error("Unable to query tracked swarm count", e);
        }
        
        return map;
    }
}
