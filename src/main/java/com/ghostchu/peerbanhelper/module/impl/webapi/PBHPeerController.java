package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.ipdb.IPDB;
import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.lab.Experiments;
import com.ghostchu.peerbanhelper.lab.Laboratory;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.rule.ActiveMonitoringModule;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.dns.DNSLookup;
import com.ghostchu.peerbanhelper.util.paging.Page;
import com.ghostchu.peerbanhelper.util.paging.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.j256.ormlite.stmt.SelectArg;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@IgnoreScan
public class PBHPeerController extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;
    private final HistoryDao historyDao;
    private final PeerRecordDao peerRecordDao;
    private final ActiveMonitoringModule activeMonitoringModule;
    private final Laboratory laboratory;
    private final DNSLookup dnsLookup;

    /**
     * Constructs a new PBHPeerController with the necessary dependencies for managing peer-related web API functionalities.
     *
     * @param javalinWebContainer The web container for handling HTTP routes and requests
     * @param historyDao Data access object for retrieving peer ban and access history
     * @param peerRecordDao Data access object for managing peer record information
     * @param activeMonitoringModule Module responsible for active peer monitoring
     * @param laboratory Experimental feature management system
     * @param dnsLookup Utility for performing DNS lookups and resolving IP addresses
     */
    public PBHPeerController(JavalinWebContainer javalinWebContainer,
                             HistoryDao historyDao, PeerRecordDao peerRecordDao,
                             ActiveMonitoringModule activeMonitoringModule,
                             Laboratory laboratory, DNSLookup dnsLookup) {
        super();
        this.javalinWebContainer = javalinWebContainer;
        this.historyDao = historyDao;
        this.peerRecordDao = peerRecordDao;
        this.activeMonitoringModule = activeMonitoringModule;
        this.laboratory = laboratory;
        this.dnsLookup = dnsLookup;
    }

    /**
     * Indicates whether the peer controller module is configurable.
     *
     * @return Always returns {@code false}, signifying that this module cannot be configured dynamically
     */
    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "Peer Controller";
    }

    @Override
    public @NotNull String getConfigName() {
        return "peer-controller";
    }

    @Override
    public void onEnable() {
        javalinWebContainer.javalin()
                .get("/api/peer/{ip}", this::handleInfo, Role.USER_READ)
                .get("/api/peer/{ip}/accessHistory", this::handleAccessHistory, Role.USER_READ, Role.PBH_PLUS)
                .get("/api/peer/{ip}/banHistory", this::handleBanHistory, Role.USER_READ, Role.PBH_PLUS);

    }

    /**
     * Handles retrieving and processing detailed information about a specific peer IP address.
     *
     * This method performs multiple database queries to collect comprehensive peer information,
     * including ban history, torrent access count, data transfer statistics, timestamps,
     * geolocation data, and DNS lookup results.
     *
     * @param ctx The Javalin context containing the request information
     * @throws SQLException If a database query error occurs during information retrieval
     *
     * @apiNote Performs the following key operations:
     * - Normalizes the IP address from the request path
     * - Queries ban count from history database
     * - Retrieves torrent access count
     * - Calculates total uploaded and downloaded data
     * - Determines first and last timestamps for peer activity
     * - Performs GeoIP lookup
     * - Attempts PTR record lookup based on experimental settings
     * - Returns peer information as a JSON response
     */
    private void handleInfo(Context ctx) throws SQLException {
        // 转换 IP 格式到 PBH 统一内部格式
        activeMonitoringModule.flush();
        String ip = IPAddressUtil.getIPAddress(ctx.pathParam("ip")).toNormalizedString();
        long banCount = historyDao.queryBuilder()
                .where()
                .eq("ip", new SelectArg(ip))
                .countOf();
        long torrentAccessCount = peerRecordDao.queryBuilder()
                .where()
                .eq("address", new SelectArg(ip))
                .countOf();
        long uploadedToPeer;
        long downloadedFromPeer;
        String[] upDownResult = peerRecordDao.queryBuilder()
                .selectRaw("SUM(uploaded) as uploaded_total, SUM(downloaded) as downloaded_total")
                .groupBy("address")
                .where()
                .eq("address", MsgUtil.escapeSql(ip))
                .queryRawFirst();
        if (upDownResult != null) {
            if (upDownResult.length == 2) {
                uploadedToPeer = Long.parseLong(upDownResult[0]);
                downloadedFromPeer = Long.parseLong(upDownResult[1]);
            } else {
                uploadedToPeer = -1;
                downloadedFromPeer = -1;
            }
        } else {
            uploadedToPeer = -1;
            downloadedFromPeer = -1;
        }
        long firstTimeSeenTS = -1;
        long lastTimeSeenTS = -1;
        // 单独做查询，因为一个 IP 可能有多条记录（当 torrent 或者 下载器不同时）
        var firstTimeSeen = peerRecordDao.queryBuilder()
                .orderBy("firstTimeSeen", true)
                .where()
                .eq("address", new SelectArg(ip))
                .queryForFirst();
        var lastTimeSeen = peerRecordDao.queryBuilder()
                .orderBy("lastTimeSeen", false)
                .where()
                .eq("address", new SelectArg(ip))
                .queryForFirst();
        if (firstTimeSeen != null) {
            firstTimeSeenTS = new Timestamp(firstTimeSeen.getFirstTimeSeen().getTime()).getTime();
        }
        if (lastTimeSeen != null) {
            lastTimeSeenTS = new Timestamp(lastTimeSeen.getLastTimeSeen().getTime()).getTime();
        }
        IPDB ipdb = getServer().getIpdb();
        IPGeoData geoIP = null;
        try {
            if (ipdb != null) {
                geoIP = ipdb.query(InetAddress.getByName(ip));
            }
        } catch (Exception e) {
            log.warn("Unable to perform GeoIP query for ip {}", ip);
        }
        String ptrLookup = null;
        try {
            if (laboratory.isExperimentActivated(Experiments.DNSJAVA.getExperiment())) {
                ptrLookup = dnsLookup.ptr(ip).get(3, TimeUnit.SECONDS).orElse(null);
            } else {
                ptrLookup = InetAddress.getByName(ip).getCanonicalHostName();
            }
        } catch (Exception ignored) {
        }
        var info = new PeerInfo(
                upDownResult != null || banCount > 0 || torrentAccessCount > 0,
                ip, firstTimeSeenTS, lastTimeSeenTS, banCount, torrentAccessCount, uploadedToPeer, downloadedFromPeer, geoIP, ptrLookup);
        ctx.json(new StdResp(true, null, info));
    }


    /**
     * Handles retrieving the ban history for a specific IP address.
     *
     * @param ctx The Javalin context containing the request information
     * @throws SQLException If a database error occurs during query execution
     *
     * This method performs the following operations:
     * 1. Extracts the IP address from the request path parameter
     * 2. Creates a pageable query result based on request parameters
     * 3. Queries the history database for ban records matching the IP
     * 4. Orders the results by ban timestamp in descending order
     * 5. Transforms ban records into localized ban log responses
     * 6. Returns the paginated ban history as a JSON response
     */
    private void handleBanHistory(Context ctx) throws SQLException {
        String ip = IPAddressUtil.getIPAddress(ctx.pathParam("ip")).toString();
        Pageable pageable = new Pageable(ctx);
        var builder = historyDao.queryBuilder()
                .orderBy("banAt", false);
        var where = builder
                .where()
                .eq("ip", new SelectArg(ip));
        builder.setWhere(where);
        var queryResult = historyDao.queryByPaging(builder, pageable);
        var result = queryResult.getResults().stream().map(r -> new PBHBanController.BanLogResponse(locale(ctx), r)).toList();
        ctx.json(new StdResp(true, null, new Page<>(pageable, queryResult.getTotal(), result)));
    }

    /**
     * Handles retrieving the access history for a specific IP address.
     *
     * @param ctx The Javalin context containing the request information
     * @throws SQLException If a database error occurs during query execution
     *
     * @apiNote This method performs the following operations:
     * - Flushes the active monitoring module
     * - Extracts the IP address from the request path parameter
     * - Creates a pageable query for peer records
     * - Filters records by the specified IP address
     * - Orders results by the last time seen in descending order
     * - Returns the access history as a JSON response
     */
    private void handleAccessHistory(Context ctx) throws SQLException {
        activeMonitoringModule.flush();
        String ip = IPAddressUtil.getIPAddress(ctx.pathParam("ip")).toString();
        Pageable pageable = new Pageable(ctx);
        var builder = peerRecordDao.queryBuilder()
                .orderBy("lastTimeSeen", false);
        var where = builder
                .where()
                .eq("address", new SelectArg(ip));
        builder.setWhere(where);
        ctx.json(new StdResp(true, null, peerRecordDao.queryByPaging(builder, pageable)));
    }


    @Override
    public void onDisable() {

    }

    public record PeerInfo(
            boolean found,
            String address,
            long firstTimeSeen,
            long lastTimeSeen,
            long banCount,
            long torrentAccessCount,
            long uploadedToPeer,
            long downloadedFromPeer,
            IPGeoData geo,
            String ptrLookup
    ) {
    }
}
