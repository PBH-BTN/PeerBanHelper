package com.ghostchu.peerbanhelper.peer;

import com.ghostchu.peerbanhelper.downloader.PeerFlag;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import org.jetbrains.annotations.Nullable;

public interface Peer extends Comparable<Peer> {
    /**
     * 获取此对等体的 IP:端口 组
     *
     * @return 地址包装器
     */
    PeerAddress getPeerAddress();

    /**
     * 获取此对等体的 PeerId
     *
     * @return PeerId，示例：-qB4250-
     */
    String getPeerId();

    /**
     * 获取此对等体的客户端名称
     *
     * @return 客户端名称，示例：qBittorrent/4.2.5
     */

    String getClientName();

    /**
     * 获取您从此对等体获取数据的速度
     *
     * @return 您从此对等体获取数据的速度 (bytes per second)
     */
    long getDownloadSpeed();

    /**
     * 获取您从此对等体获取数据的累计大小
     *
     * @return 获取的数据大小(bytes)
     */

    long getDownloaded();

    /**
     * 获取您向此对等体分享数据的速度
     *
     * @return 您向此对等体分享数据的速度 (bytes per second)
     */

    long getUploadSpeed();

    /**
     * 获取您向此对等体提供数据的累计大小
     *
     * @return 提供的数据大小(bytes)
     */
    long getUploaded();

    /**
     * 对等体的下载进度
     *
     * @return 对等体当前文件的下载速度
     */
    double getProgress();

    /**
     * 对等体的 Flag 信息
     *
     * @return Flag
     */
    @Nullable
    PeerFlag getFlags();

    @Override
    default int compareTo(Peer o) {
        return this.getPeerAddress().compareTo(o.getPeerAddress());
    }

    default String getCacheKey() {
        //return "pa=" + this.getPeerAddress().toString() + ",pid=" + this.getPeerId() + ",pname=" + this.getClientName();
        return getPeerAddress().getIp() + ':' + getPeerAddress().getPort();
    }
}
