package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.bittorrent.tracker.Tracker;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.gson.JsonObject;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface Downloader extends AutoCloseable {

    @NotNull
    YamlConfiguration saveDownloader();
    @NotNull
    JsonObject saveDownloaderJson();
    @NotNull
    String getEndpoint();

//    String getWebUIEndpoint();

//    @Nullable
//    DownloaderBasicAuth getDownloaderBasicAuth();
//
//    @Nullable
//    WebViewScriptCallback getWebViewJavaScript();
//
//    boolean isSupportWebview();

    /**
     * 下载器用户定义名称
     *
     * @return 用户定义名称
     */
    @NotNull
    String getName();
    @NotNull
    String getId();

    /**
     * 下载器类型
     *
     * @return 类型
     */
    @NotNull
    String getType();

    /**
     * 登录到此下载器
     *
     * @return 登陆是否成功
     */
    @NotNull
    DownloaderLoginResult login();

    /**
     * Check if the downloader is in paused state
     *
     * @return true if paused, false otherwise
     */
    boolean isPaused();

    /**
     * Set the paused state of the downloader
     *
     * @param paused true to pause, false to resume
     */
    void setPaused(boolean paused);

    /**
     * 一个执行调度任务的窗口，该方法总是在 banWave 中调用
     */
    default void runScheduleTasks() {
    }

    /**
     * 获取此下载器的所有目前正在活动的 Torrent 列表
     *
     * @return 返回所有活动的 Torrents
     */
    @NotNull
    List<Torrent> getTorrents();

    /**
     * 获取此下载器的所有 Torrents
     *
     * @return 返回所有的 Torrents
     */
    @NotNull
    List<Torrent> getAllTorrents();

    /**
     * 获取指定 Torrent 的对等体列表
     *
     * @param torrent Torrent
     * @return 对等体列表
     */
    @NotNull
    List<Peer> getPeers(@NotNull Torrent torrent);

    /**
     * 获取指定 Torrent 的 Tracker 列表
     * @param torrent Torrent
     * @return Tracker 列表
     */
    @NotNull
    List<Tracker> getTrackers(@NotNull Torrent torrent);

    /**
     * 设置指定 Torrent 的 Tracker 列表
     * @param torrent Torrent
     * @param trackers Tracker 列表
     */
    void setTrackers(@NotNull Torrent torrent, @NotNull List<Tracker> trackers);

    /**
     * 设置并使新的 BanList 生效
     *
     * @param fullList      全量列表
     * @param added         新增列表
     * @param removed       移除列表
     * @param applyFullList 强制应用全量列表
     */
    void setBanList(@NotNull Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList);

    /**
     * 获取客户端最后一次请求的状态
     *
     * @return 最后请求状态
     */
    @NotNull
    DownloaderLastStatus getLastStatus();

    /**
     * 获取客户端最后一次请求的状态
     *
     * @param lastStatus 最后请求状态
     */
    void setLastStatus(@NotNull DownloaderLastStatus lastStatus, @NotNull TranslationComponent statusMessage);

    /**
     * 获取客户端状态描述说明
     *
     * @return 状态描述说明
     */
    @NotNull
    TranslationComponent getLastStatusMessage();

    /**
     * 获取下载器的统计数据
     *
     * @return 统计数据
     */
    @NotNull
    DownloaderStatistics getStatistics();

    /**
     * 获取下载器扩展特性标记
     * @return 扩展特性标记列表
     */
    @NotNull
    List<DownloaderFeatureFlag> getFeatureFlags();

    int getMaxConcurrentPeerRequestSlots();

//    /**
//     * 添加标签到指定种子
//     * @param torrent Torrent
//     * @param tag 标签
//     */
//    void addTag(Torrent torrent, String tag);
//
//    /**
//     * 从种子上移除指定的标签
//     * @param torrent Torrent
//     * @param tag 标签
//     */
//    void removeTag(Torrent torrent, String tag);
//
//    /**
//     * 以纯文本方式获取指定 Torrent 上的所有标签
//     * @param torrent Torrent
//     * @return 标签列表
//     */
//    List<String> getTags(Torrent torrent);
//
//    /**
//     * 暂停 Torrent
//     * @param torrent Torrent
//     */
//    void pauseTorrent(Torrent torrent);
//
//    /**
//     * 开始 Torrent
//     * @param torrent Torrent
//     */
//    void startTorrent(Torrent torrent);

    /**
     * 获取当前下载器的限速配置
     * @return 限速配置，如果不支持或者请求错误，则可能返回 null
     */
    @Nullable
    DownloaderSpeedLimiter getSpeedLimiter();

    /**
     * 设置当前下载器的限速配置
     * @param speedLimiter 限速配置
     */
    void setSpeedLimiter(@Nullable DownloaderSpeedLimiter speedLimiter);

    int getBTProtocolPort();
    void setBTProtocolPort(int port);
}
