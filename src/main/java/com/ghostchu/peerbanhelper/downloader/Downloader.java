package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

public interface Downloader extends AutoCloseable {
    String getEndpoint();

    String getName();

    /**
     * 登录到此下载器
     * @return 登陆是否成功
     */
    boolean login() throws URISyntaxException;

    /**
     * 获取此下载器的所有目前正在活动的 Torrent 列表
     * @return 返回所有活动的 Torrents
     */
    List<Torrent> getTorrents();

    /**
     * 获取指定 Torrent 的对等体列表
     * @param torrent Torrent
     * @return 对等体列表
     */
    List<Peer> getPeers(Torrent torrent);

    /**
     * 获取此下载器已设置的 BanList
     * @return BanList
     */
    List<PeerAddress> getBanList();

    /**
     * 设置并使新的 BanList 生效
     * @param peerAddresses BanList
     */
    void setBanList(Collection<PeerAddress> peerAddresses);

    /**
     * 如有需要，重启 Torrent 任务
     * 有些客户端（如 Transmission）需要重启 Torrent 任务才能断开已连接的 Peers 来使屏蔽列表生效
     * @param torrents Torrent 任务列表
     */
    void relaunchTorrentIfNeeded(Collection<Torrent> torrents);
}
