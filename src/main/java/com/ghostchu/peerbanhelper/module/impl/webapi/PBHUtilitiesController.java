package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.dao.impl.AlertDao;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.torrent.Tracker;
import com.ghostchu.peerbanhelper.torrent.TrackerImpl;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Component
@IgnoreScan
public final class PBHUtilitiesController extends AbstractFeatureModule {
    private static final Logger log = LoggerFactory.getLogger(PBHUtilitiesController.class);
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private AlertDao alertDao;

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Utilities";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-utilities";
    }

    @Override
    public void onEnable() {
        webContainer.javalin()
                .post("/api/utilities/replaceTracker", this::handleReplaceTracker, Role.USER_WRITE)
        ;
    }

    private void handleReplaceTracker(Context context) {
        ReplaceTrackerDTO dto = context.bodyAsClass(ReplaceTrackerDTO.class);
        if (dto == null || dto.from() == null || dto.to() == null || dto.from().isEmpty() || dto.to().isEmpty()) {
            context.status(400);
            return;
        }
        AtomicInteger count = new AtomicInteger(0);
        for (Downloader downloader : getServer().getDownloaders()) {
            if (dto.downloaders() != null && !dto.downloaders().isEmpty() && !dto.downloaders().contains(downloader.getName())) {
                continue;
            }
            if (downloader.login().success()) {
                try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                    for (Torrent torrent : downloader.getAllTorrents()) {
                        executor.submit(() -> {
                            try {
                                List<Tracker> liveTrackers = downloader.getTrackers(torrent);
                                List<Tracker> newTrackers = createTrackerListForReplace(dto.from(), dto.to(), liveTrackers);
                                if (newTrackers != null) {
                                    downloader.setTrackers(torrent, newTrackers);
                                    count.incrementAndGet();
                                }
                            } catch (Exception e) {
                                log.error("Failed to replace tracker for torrent: {}", torrent.getName(), e);
                            }
                        });
                    }
                } catch (Exception e) {
                    log.error("Failed to replace tracker for downloader, unable to retrieve all torrents: {}", downloader.getName(), e);
                }
            }
        }
        context.json(new StdResp(true, tl(locale(context), Lang.UTILITIES_TRACKER_REPLACED, count.get()), count.get()));
    }

    /**
     * 创建用于替换原始 Tracker 列表的列表
     * @param from
     * @param to
     * @param trackers
     * @return 如果有任何修改则返回新的 Tracker 列表，否则返回 null
     */
    @Nullable
    private List<Tracker> createTrackerListForReplace(String from, String to, List<Tracker> trackers) {
        boolean anyModification = false;
        List<Tracker> newTrackers = new ArrayList<>();
        for (Tracker tracker : trackers) {
            List<String> newTrackerGroup = new ArrayList<>();
            for (String trackerUrl : tracker.getTrackersInGroup()) {
                if (trackerUrl.contains(from)) {
                    newTrackerGroup.add(trackerUrl.replace(from, to));
                    anyModification = true;
                } else {
                    newTrackerGroup.add(trackerUrl);
                }
            }
            newTrackers.add(new TrackerImpl(newTrackerGroup));
        }
        if (anyModification) {
            return newTrackers;
        } else {
            return null;
        }
    }


    @Override
    public void onDisable() {

    }

    record ReplaceTrackerDTO(String from, String to, List<String> downloaders) {

    }
}