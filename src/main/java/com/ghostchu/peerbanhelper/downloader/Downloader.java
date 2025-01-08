package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;
import com.google.gson.JsonObject;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface Downloader extends AutoCloseable {

    YamlConfiguration saveDownloader();

    JsonObject saveDownloaderJson();

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
    String getName();

    /**
     * 下载器类型
     *
     * @return 类型
     */
    String getType();

    /**
 * Attempts to log in to the downloader.
 *
 * @return A {@code DownloaderLoginResult} indicating the outcome of the login attempt
 * @throws RuntimeException if login encounters unexpected errors
 */
    DownloaderLoginResult login();

    /**
 * Checks if the downloader is currently in a paused state.
 *
 * @return true if the downloader is paused, false otherwise
 */
boolean isPaused();

    /**
 * Sets the paused state of the downloader.
 *
 * @param paused A boolean indicating whether the downloader should be paused (true) or resumed (false)
 */
void setPaused(boolean paused);

    /**
     * Executes scheduled tasks during a ban wave operation.
     * 
     * <p>This default method is typically invoked within the context of a ban wave process.
     * Implementing classes can override this method to provide specific scheduled task logic
     * if required. By default, it is a no-op method.</p>
     * 
     * @implNote This method is designed to be a hook for performing periodic or scheduled
     * maintenance tasks related to the downloader during a ban wave cycle.
     */
    default void runScheduleTasks() {
    }

    /**
     * 获取此下载器的所有目前正在活动的 Torrent 列表
     *
     * @return 返回所有活动的 Torrents
     */
    List<Torrent> getTorrents();

    /**
     * 获取指定 Torrent 的对等体列表
     *
     * @param torrent Torrent
     * @return 对等体列表
     */
    List<Peer> getPeers(Torrent torrent);


    /**
     * 设置并使新的 BanList 生效
     *
     * @param fullList      全量列表
     * @param added         新增列表
     * @param removed       移除列表
     * @param applyFullList 强制应用全量列表
     */
    void setBanList(Collection<PeerAddress> fullList, @Nullable Collection<BanMetadata> added, @Nullable Collection<BanMetadata> removed, boolean applyFullList);

    /**
     * 如有需要，重启 Torrent 任务
     * 有些客户端（如 Transmission）需要重启 Torrent 任务才能断开已连接的 Peers 来使屏蔽列表生效
     *
     * @param torrents Torrent 任务列表
     */
    void relaunchTorrentIfNeeded(Collection<Torrent> torrents);

    /**
     * 如有需要，重启 Torrent 任务
     * 有些客户端（如 Transmission）需要重启 Torrent 任务才能断开已连接的 Peers 来使屏蔽列表生效
     *
     * @param torrents Torrent 任务列表
     */
    void relaunchTorrentIfNeededByTorrentWrapper(Collection<TorrentWrapper> torrents);

    /**
     * 获取客户端最后一次请求的状态
     *
     * @return 最后请求状态
     */
    DownloaderLastStatus getLastStatus();

    /**
     * 获取客户端最后一次请求的状态
     *
     * @param lastStatus 最后请求状态
     */
    void setLastStatus(DownloaderLastStatus lastStatus, TranslationComponent statusMessage);

    /**
     * 获取客户端状态描述说明
     *
     * @return 状态描述说明
     */
    TranslationComponent getLastStatusMessage();

    /**
     * 获取下载器的统计数据
     *
     * @return 统计数据
     */
    DownloaderStatistics getStatistics();

    /**
     * 获取下载器扩展特性标记
     * @return 扩展特性标记列表
     */
    List<DownloaderFeatureFlag> getFeatureFlags();

    int getMaxConcurrentPeerRequestSlots();
}
