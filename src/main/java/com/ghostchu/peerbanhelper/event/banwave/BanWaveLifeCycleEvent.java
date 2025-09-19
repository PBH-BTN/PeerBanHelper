package com.ghostchu.peerbanhelper.event.banwave;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class BanWaveLifeCycleEvent {
    private final Stage stage;
    public enum Stage {
        STARTING,
        PRE_REAPPLY_BAN_LIST,
        POST_REAPPLY_BAN_LIST,
        PRE_EXECUTE_DOWNLOADER_SCHEDULED_TASKS,
        POST_EXECUTE_DOWNLOADER_SCHEDULED_TASKS,
        PRE_REMOVE_EXPIRED_BANS,
        POST_REMOVE_EXPIRED_BANS,
        PRE_COLLECT_PEERS,
        POST_COLLECT_PEERS,
        PRE_UPDATE_LIVE_PEERS,
        POST_UPDATE_LIVE_PEERS,
        PRE_NOTIFY_BATCH_MONITOR_MODULES,
        POST_NOTIFY_BATCH_MONITOR_MODULES,
        PRE_NOTIFY_MONITOR_MODULES,
        POST_NOTIFY_MONITOR_MODULES,
        PRE_CHECK_BANS,
        POST_CHECK_BANS,
        PRE_PROCESS_SCHEDULED_TASKS,
        POST_PROCESS_SCHEDULED_TASKS,
        PRE_HANDLE_BAN_ENTRIES,
        POST_HANDLE_BAN_ENTRIES,
        PRE_APPLY_BAN_LIST,
        POST_APPLIED_BAN_LIST,
        ENDED
    }
}
