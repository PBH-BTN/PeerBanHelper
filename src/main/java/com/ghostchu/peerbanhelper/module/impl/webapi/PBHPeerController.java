package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.ipdb.IPDB;
import com.ghostchu.peerbanhelper.ipdb.IPGeoData;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.paging.Page;
import com.ghostchu.peerbanhelper.util.paging.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.sql.SQLException;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@Slf4j
public class PBHPeerController extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;
    private final HistoryDao historyDao;
    private final PeerRecordDao peerRecordDao;

    public PBHPeerController(JavalinWebContainer javalinWebContainer, HistoryDao historyDao, PeerRecordDao peerRecordDao) {
        super();
        this.javalinWebContainer = javalinWebContainer;
        this.historyDao = historyDao;
        this.peerRecordDao = peerRecordDao;
    }

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
                .get("/api/peer/{ip}/accessHistory", this::handleAccessHistory, Role.USER_READ)
                .get("/api/peer/{ip}/banHistory", this::handleBanHistory, Role.USER_READ);

    }

    private void handleInfo(Context ctx) throws SQLException {
        // 转换 IP 格式到 PBH 统一内部格式
        @SuppressWarnings("DataFlowIssue")
        String ip = IPAddressUtil.getIPAddress(ctx.pathParam("ip")).toString();
        long banCount = historyDao.queryBuilder()
                .where()
                .eq("ip", ip)
                .countOf();
        long torrentAccessCount = peerRecordDao.queryBuilder()
                .where()
                .eq("address", ip)
                .countOf();
        long uploadedToPeer;
        long downloadedFromPeer;
        String[] upDownResult = peerRecordDao.queryBuilder()
                .selectRaw("SUM(uploaded) as uploaded_total, SUM(downloaded) as downloaded_total")
                .groupBy("address")
                .where()
                .eq("address", ip)
                .queryRawFirst();
        if (upDownResult == null) {
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PEER_NOT_FOUND), null));
            return;
        }
        if (upDownResult.length == 2) {
            uploadedToPeer = Long.parseLong(upDownResult[0]);
            downloadedFromPeer = Long.parseLong(upDownResult[1]);
        } else {
            uploadedToPeer = -1;
            downloadedFromPeer = -1;
        }
        // 单独做查询，因为一个 IP 可能有多条记录（当 torrent 或者 下载器不同时）
        var firstTimeSeen = peerRecordDao.queryBuilder()
                .orderBy("firstTimeSeen", true)
                .where()
                .eq("address", ip)
                .queryForFirst().getFirstTimeSeen();
        var lastTimeSeen = peerRecordDao.queryBuilder()
                .orderBy("lastTimeSeen", false)
                .where()
                .eq("address", ip)
                .queryForFirst().getLastTimeSeen();

        IPDB ipdb = getServer().getIpdb();
        IPGeoData geoIP = null;
        try {
            if (ipdb != null) {
                geoIP = ipdb.query(InetAddress.getByName(ip));
            }
        } catch (Exception e) {
            log.warn("Unable to perform GeoIP query for ip {}", ip);
        }
        var info = new PeerInfo(ip, firstTimeSeen.getTime(), lastTimeSeen.getTime(), banCount, torrentAccessCount, uploadedToPeer, downloadedFromPeer, geoIP);
        ctx.json(new StdResp(true, null, info));
    }


    private void handleBanHistory(Context ctx) throws SQLException {
        @SuppressWarnings("DataFlowIssue")
        String ip = IPAddressUtil.getIPAddress(ctx.pathParam("ip")).toString();
        Pageable pageable = new Pageable(ctx);
        var builder = historyDao.queryBuilder()
                .orderBy("banAt", false);
        var where = builder
                .where()
                .eq("ip", ip);
        builder.setWhere(where);
        var queryResult = historyDao.queryByPaging(builder, pageable);
        var result = queryResult.getResults().stream().map(r -> new PBHBanController.BanLogResponse(locale(ctx), r)).toList();
        ctx.json(new StdResp(true, null, new Page<>(pageable, queryResult.getTotal(), result)));
    }

    private void handleAccessHistory(Context ctx) throws SQLException {
        @SuppressWarnings("DataFlowIssue")
        String ip = IPAddressUtil.getIPAddress(ctx.pathParam("ip")).toString();
        Pageable pageable = new Pageable(ctx);
        var builder = peerRecordDao.queryBuilder()
                .orderBy("lastTimeSeen", false);
        var where = builder
                .where()
                .eq("address", ip);
        builder.setWhere(where);
        ctx.json(new StdResp(true, null, peerRecordDao.queryByPaging(builder, pageable)));
    }


    @Override
    public void onDisable() {

    }

    public record PeerInfo(
            String address,
            long firstTimeSeen,
            long lastTimeSeen,
            long banCount,
            long torrentAccessCount,
            long uploadedToPeer,
            long downloadedFromPeer,
            IPGeoData geo
    ) {
    }
}
