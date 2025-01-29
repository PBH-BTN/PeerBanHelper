package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TorrentDao;
import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.ghostchu.peerbanhelper.database.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.database.table.TorrentEntity;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@IgnoreScan
@Slf4j
public final class PBHTorrentController extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;
    private final TorrentDao torrentDao;
    private final PeerRecordDao peerRecordDao;
    private final HistoryDao historyDao;

    public PBHTorrentController(JavalinWebContainer javalinWebContainer, TorrentDao torrentDao, PeerRecordDao peerRecordDao, HistoryDao historyDao) {
        this.javalinWebContainer = javalinWebContainer;
        this.torrentDao = torrentDao;
        this.historyDao = historyDao;
        this.peerRecordDao = peerRecordDao;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "Torrent Controller";
    }

    @Override
    public @NotNull String getConfigName() {
        return "torrent-controller";
    }

    @Override
    public void onEnable() {
        javalinWebContainer
                .javalin()
                //.get("/api/torrent", this::handleTorrentQuery, Role.USER_READ)
                .get("/api/torrent/query", this::handleTorrentQuery, Role.USER_READ)
                .get("/api/torrent/{infoHash}", this::handleTorrentInfo, Role.USER_READ)
                .get("/api/torrent/{infoHash}/accessHistory", this::handleConnectHistory, Role.USER_READ, Role.PBH_PLUS)
                .get("/api/torrent/{infoHash}/banHistory", this::handleBanHistory, Role.USER_READ, Role.PBH_PLUS);
    }

    private void handleBanHistory(Context ctx) throws SQLException {
        var torrent = torrentDao.queryByInfoHash(ctx.pathParam("infoHash"));
        if (torrent.isEmpty()) {
            ctx.status(404);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.TORRENT_NOT_FOUND), null));
            return;
        }
        Pageable pageable = new Pageable(ctx);
        var t = torrent.get();
        Page<HistoryEntity> page = historyDao.queryByPaging(
                historyDao.queryBuilder()
                        .orderBy("banAt", false)
                        .where()
                        .eq("torrent_id", new SelectArg(t))
                        .queryBuilder()
                , pageable);
        var result = page.getResults().stream().map(r -> new PBHBanController.BanLogResponse(locale(ctx), r)).toList();
        ctx.json(new StdResp(true, null, new Page<>(pageable, page.getTotal(), result)));
    }


    private void handleTorrentQuery(Context ctx) throws SQLException {
        Pageable pageable = new Pageable(ctx);
        Page<TorrentEntity> torrentEntityPage;
        if (ctx.queryParam("keyword") == null) {
            torrentEntityPage = torrentDao.queryByPaging(
                    torrentDao.queryBuilder()
                            .orderBy("id", false),
                    pageable);
        } else {
            torrentEntityPage = torrentDao.queryByPaging(
                    torrentDao.queryBuilder()
                            .orderBy("id", false)
                            .where()
                            .like("name", new SelectArg("%" + ctx.queryParam("keyword") + "%"))
                            .or()
                            .like("infoHash", new SelectArg("%" + ctx.queryParam("keyword") + "%"))
                            .queryBuilder()
                    , pageable);
        }
        List<TorrentInfo> infoList = new ArrayList<>();
        for (TorrentEntity result : torrentEntityPage.getResults()) {
            var peerBanCount = historyDao.queryBuilder()
                    .where()
                    .eq("torrent_id", result.getId())
                    .countOf();
            var peerAccessCount = peerRecordDao.queryBuilder()
                    .orderBy("lastTimeSeen", false)
                    .where()
                    .eq("torrent_id", result.getId())
                    .countOf();
            infoList.add(new TorrentInfo(result.getInfoHash(), result.getName(), result.getSize(), peerBanCount, peerAccessCount));
        }
        ctx.json(new StdResp(true, null, new Page<>(pageable, torrentEntityPage.getTotal(), infoList)));
    }


    private void handleTorrentInfo(Context ctx) throws SQLException {
        var torrent = torrentDao.queryByInfoHash(ctx.pathParam("infoHash"));
        if (torrent.isEmpty()) {
            ctx.status(404);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.TORRENT_NOT_FOUND), null));
            return;
        }
        var t = torrent.get();
        var peerBanCount = historyDao.queryBuilder()
                .where()
                .eq("torrent_id",t.getId())
                .countOf();
        var peerAccessCount = peerRecordDao.queryBuilder()
                .orderBy("lastTimeSeen", false)
                .where()
                .eq("torrent_id", t.getId())
                .countOf();

        ctx.json(new StdResp(true, null, new TorrentInfo(t.getInfoHash(),
                t.getName(), t.getSize(),
                peerBanCount, peerAccessCount)));
    }

    private void handleConnectHistory(Context ctx) throws SQLException {
        var torrent = torrentDao.queryByInfoHash(ctx.pathParam("infoHash"));
        if (torrent.isEmpty()) {
            ctx.status(404);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.TORRENT_NOT_FOUND), null));
            return;
        }
        Pageable pageable = new Pageable(ctx);
        var t = torrent.get();
        var queryBuilder = peerRecordDao.queryBuilder().orderBy("lastTimeSeen", false);
        var queryWhere = queryBuilder.where().eq("torrent_id", t);
        queryBuilder.setWhere(queryWhere);
        Page<PeerRecordEntity> page = peerRecordDao.queryByPaging(queryBuilder, pageable);
        ctx.json(new StdResp(true, null, page));
    }

    @Override
    public void onDisable() {

    }

    public record TorrentInfo(
            String infoHash,
            String name,
            long size,
            long peerBanCount,
            long peerAccessCount
    ) {

    }
}
