package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.dao.impl.*;
import com.ghostchu.peerbanhelper.database.table.HistoryEntity;
import com.ghostchu.peerbanhelper.database.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.database.table.TorrentEntity;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BanLogDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PeerRecordEntityDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.TorrentEntityDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.TorrentInfoDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import com.ghostchu.peerbanhelper.util.query.Page;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@Slf4j
public final class PBHTorrentController extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;
    private final TorrentDao torrentDao;
    private final PeerRecordDao peerRecordDao;
    private final HistoryDao historyDao;
    private final DownloaderManagerImpl downloaderManager;
    private final RuleDao ruleDao;
    private final ModuleDao moduleDao;

    public PBHTorrentController(JavalinWebContainer javalinWebContainer, TorrentDao torrentDao, PeerRecordDao peerRecordDao, HistoryDao historyDao, DownloaderManagerImpl downloaderManager, RuleDao ruleDao, ModuleDao moduleDao) {
        this.javalinWebContainer = javalinWebContainer;
        this.torrentDao = torrentDao;
        this.historyDao = historyDao;
        this.peerRecordDao = peerRecordDao;
        this.downloaderManager = downloaderManager;
        this.ruleDao = ruleDao;
        this.moduleDao = moduleDao;
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
                new Orderable(Map.of("banAt", false), ctx)
                        .addMapping("torrent.name", "torrentName")
                        .addMapping("torrent.infoHash", "torrentInfoHash")
                        .addMapping("torrent.size", "torrentSize")
                        .addMapping("module.name", "module")
                        .addMapping("rule.rule", "rule")
                        .addMapping("ip", "peerIp")
                        .apply(
                        historyDao.queryBuilder()
                                .where()
                                .eq("torrent_id", new SelectArg(t))
                                .queryBuilder()
                                .join(torrentDao.queryBuilder().setAlias("torrent"), QueryBuilder.JoinType.LEFT, QueryBuilder.JoinWhereOperation.AND)
                                .join(ruleDao.queryBuilder().setAlias("rule")
                                                .join(moduleDao.queryBuilder().setAlias("module"), QueryBuilder.JoinType.LEFT, QueryBuilder.JoinWhereOperation.AND)
                                        , QueryBuilder.JoinType.LEFT, QueryBuilder.JoinWhereOperation.AND)
                        )
                , pageable);
        var result = page.getResults().stream().map(r -> new BanLogDTO(locale(ctx), downloaderManager, r)).toList();
        ctx.json(new StdResp(true, null, new Page<>(pageable, page.getTotal(), result)));
    }


    private void handleTorrentQuery(Context ctx) throws SQLException {
        Pageable pageable = new Pageable(ctx);

        // 检查是否需要按计数字段排序
        boolean needsCountSort = false;
        String countSortField = null;
        boolean countSortAscending = true;

        for (String orderByParam : ctx.queryParams("orderBy")) {
            String[] parts = orderByParam.split("\\|");
            String field = parts[0];
            if ("peerBanCount".equals(field) || "peerAccessCount".equals(field)) {
                needsCountSort = true;
                countSortField = field;
                countSortAscending = parts.length < 2 || (!parts[1].equalsIgnoreCase("desc") && !parts[1].equalsIgnoreCase("descend"));
                break;
            }
        }

        Page<TorrentEntity> torrentEntityPage;
        QueryBuilder<TorrentEntity, Long> qb = torrentDao.queryBuilder();
        // 添加搜索条件
        if (ctx.queryParam("keyword") != null) {
            qb.where()
                    .like("name", new SelectArg("%" + ctx.queryParam("keyword") + "%"))
                    .or()
                    .like("infoHash", new SelectArg("%" + ctx.queryParam("keyword") + "%"));
        }
        if (needsCountSort) {
            // 使用 SQL 子查询进行排序
            String subQueryTable = "peerBanCount".equals(countSortField) ? "history" : "peer_record";
            String sortDirection = countSortAscending ? "ASC" : "DESC";
            // 使用 orderByRaw 添加子查询排序
            // 注意：使用实际表名 'torrents' 而不是别名 'torrent'
            qb.orderByRaw("(SELECT COUNT(*) FROM " + subQueryTable +
                    " WHERE " + subQueryTable + ".torrent_id = torrents.id) " + sortDirection);
        } else {
            // 普通排序（按数据库字段）
            new Orderable(Map.of("id", false), ctx).apply(qb);
        }
        torrentEntityPage = torrentDao.queryByPaging(qb, pageable);

        // 构建结果列表（始终需要查询计数）
        List<TorrentInfoDTO> infoList = new ArrayList<>();
        for (TorrentEntity result : torrentEntityPage.getResults()) {
            var peerBanCount = historyDao.queryBuilder()
                    .where()
                    .eq("torrent_id", result.getId())
                    .countOf();
            var peerAccessCount = peerRecordDao.queryBuilder()
                    .where()
                    .eq("torrent_id", result.getId())
                    .countOf();
            infoList.add(new TorrentInfoDTO(result.getInfoHash(), result.getName(), result.getSize(), peerBanCount, peerAccessCount));
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
                .eq("torrent_id", t.getId())
                .countOf();
        var peerAccessCount = peerRecordDao.queryBuilder()
                .orderBy("lastTimeSeen", false)
                .where()
                .eq("torrent_id", t.getId())
                .countOf();

        ctx.json(new StdResp(true, null, new TorrentInfoDTO(t.getInfoHash(),
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
        var queryBuilder = new Orderable(Map.of("lastTimeSeen", false, "address", true, "port", true), ctx)
                .apply(peerRecordDao.queryBuilder().join(torrentDao.queryBuilder(), QueryBuilder.JoinType.LEFT, QueryBuilder.JoinWhereOperation.AND));
        queryBuilder.where().eq("torrent_id", t);
        Page<PeerRecordEntity> page = peerRecordDao.queryByPaging(queryBuilder, pageable);
        ctx.json(new StdResp(true, null,  Page.map(page, (entity)-> new PeerRecordEntityDTO(entity.getId(),
                entity.getAddress(),
                entity.getPort(),
                TorrentEntityDTO.from(entity.getTorrent()),
                downloaderManager.getDownloadInfo(entity.getDownloader()),
                entity.getPeerId(),
                entity.getClientName(),
                entity.getUploaded(),
                entity.getUploadedOffset(),
                entity.getUploadSpeed(),
                entity.getDownloaded(),
                entity.getDownloadedOffset(),
                entity.getDownloadSpeed(),
                entity.getLastFlags(),
                entity.getFirstTimeSeen(),
                entity.getLastTimeSeen()
        ))));
    }

    @Override
    public void onDisable() {

    }

}
