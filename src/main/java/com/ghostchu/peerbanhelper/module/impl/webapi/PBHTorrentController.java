package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ghostchu.peerbanhelper.databasent.service.HistoryService;
import com.ghostchu.peerbanhelper.databasent.service.PeerRecordService;
import com.ghostchu.peerbanhelper.databasent.service.TorrentService;
import com.ghostchu.peerbanhelper.databasent.table.HistoryEntity;
import com.ghostchu.peerbanhelper.databasent.table.PeerRecordEntity;
import com.ghostchu.peerbanhelper.databasent.table.TorrentEntity;
import com.ghostchu.peerbanhelper.downloader.DownloaderManagerImpl;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.BanLogDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PeerRecordEntityDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.TorrentEntityDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.TorrentInfoDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.query.Orderable;
import com.ghostchu.peerbanhelper.util.query.PBHPage;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@Slf4j
public final class PBHTorrentController extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;
    private final TorrentService torrentService;
    private final PeerRecordService peerRecordService;
    private final HistoryService historyService;
    private final DownloaderManagerImpl downloaderManager;

    @Autowired
    public PBHTorrentController(JavalinWebContainer javalinWebContainer, TorrentService torrentService, PeerRecordService peerRecordService, HistoryService historyService, DownloaderManagerImpl downloaderManager) {
        this.javalinWebContainer = javalinWebContainer;
        this.torrentService = torrentService;
        this.historyService = historyService;
        this.peerRecordService = peerRecordService;
        this.downloaderManager = downloaderManager;
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

    private void handleBanHistory(Context ctx) {
        var torrent = torrentService.queryByInfoHash(ctx.pathParam("infoHash"));
        if (torrent == null) {
            ctx.status(404);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.TORRENT_NOT_FOUND), null));
            return;
        }
        Pageable pageable = new Pageable(ctx);
        Orderable orderable = new Orderable(Map.of("ban_at", false), ctx)
                .addRemapping("banAt", "ban_at")
                .addRemapping("unbanAt", "unban_at")
                .addRemapping("peerIp", "ip")
                .addRemapping("peerPort", "port")
                .addRemapping("peerId", "peer_id")
                .addRemapping("peerClientName", "peer_client_name")
                .addRemapping("peerUploaded", "peer_uploaded")
                .addRemapping("peerDownloaded", "peer_downloaded")
                .addRemapping("peerProgress", "peer_progress")
                .addRemapping("torrentInfoHash", "torrent_info_hash")
                .addRemapping("module", "module_name")
                .addRemapping("rule", "rule_name")
                .addRemapping("description", "description");

        IPage<HistoryEntity> page = historyService.queryBanHistoryByTorrentId(pageable.toPage(), torrent.getId(), orderable);
        var result = page.convert(r -> new BanLogDTO(locale(ctx), downloaderManager, r, TorrentEntityDTO.from(torrent)));
        ctx.json(new StdResp(true, null, PBHPage.from(result)));
    }


    private void handleTorrentQuery(Context ctx) {
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
                countSortAscending = parts.length < 2 || (!"desc".equalsIgnoreCase(parts[1]) && !"descend".equalsIgnoreCase(parts[1]));
                break;
            }
        }

        Page<TorrentEntity> pageRequest = pageable.toPage();
        String keyword = ctx.queryParam("keyword");

        IPage<TorrentEntity> torrentEntityPage = torrentService.search(pageRequest, keyword,
                new Orderable(Map.of("id", false), ctx)
                        .addRemapping("infoHash", "info_hash")
                , needsCountSort ? countSortField : null, countSortAscending);

        // 批量查询计数 - 优化 N+1 查询问题
        List<Long> torrentIds = torrentEntityPage.getRecords().stream()
                .map(TorrentEntity::getId)
                .toList();

        Map<Long, Long> banCountMap = historyService.countByTorrentIds(torrentIds);
        Map<Long, Long> accessCountMap = peerRecordService.countByTorrentIds(torrentIds);

        var results = torrentEntityPage.convert(result -> {
            long peerBanCount = banCountMap.getOrDefault(result.getId(), 0L);
            long peerAccessCount = accessCountMap.getOrDefault(result.getId(), 0L);
            return new TorrentInfoDTO(result.getInfoHash(), result.getName(), result.getSize() == null ? 0 : result.getSize(), peerBanCount, peerAccessCount);
        });
        ctx.json(new StdResp(true, null, PBHPage.from(results)));
    }


    private void handleTorrentInfo(Context ctx) {
        var torrent = torrentService.queryByInfoHash(ctx.pathParam("infoHash"));
        if (torrent == null) {
            ctx.status(404);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.TORRENT_NOT_FOUND), null));
            return;
        }
        long peerBanCount = historyService.countHistoriesByTorrentId(torrent.getId());
        long peerAccessCount = peerRecordService.countRecordsByTorrentId(torrent.getId());
        ctx.json(new StdResp(true, null, new TorrentInfoDTO(torrent.getInfoHash(),
                torrent.getName(), torrent.getSize(),
                peerBanCount, peerAccessCount)));
    }

    private void handleConnectHistory(Context ctx) {
        var torrent = torrentService.queryByInfoHash(ctx.pathParam("infoHash"));
        if (torrent == null) {
            ctx.status(404);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.TORRENT_NOT_FOUND), null));
            return;
        }
        Pageable pageable = new Pageable(ctx);
        Orderable orderable = new Orderable(Map.of("last_time_seen", false, "address", true, "port", true), ctx)
                .addRemapping("peerId", "peer_id")
                .addRemapping("clientName", "client_name")
                .addRemapping("firstTimeSeen", "first_time_seen")
                .addRemapping("lastTimeSeen", "last_time_seen");
        IPage<PeerRecordEntity> page = peerRecordService.queryAccessHistoryByTorrentId(pageable.toPage(), torrent.getId(), orderable);
        var result = page.convert(entity ->
                new PeerRecordEntityDTO(entity.getId(),
                        entity.getAddress(),
                        entity.getPort(),
                        TorrentEntityDTO.from(torrent),
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
                ));
        ctx.json(new StdResp(true, null, PBHPage.from(result)));
    }

    @Override
    public void onDisable() {

    }

}
