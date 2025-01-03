package com.ghostchu.peerbanhelper.peer;

import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

    /**
     * 对等体是否连接中或者握手中（总之就是还没准备好传输数据）
     * @return 是否连接中或者握手中
     */
    boolean isHandshaking();

    /**
     * 获取此 Peer 支持的消息集合，需要下载器支持
     * 不支持的下载器此处将返回空集合
     * @return 支持的消息集合
     */
    List<PeerMessage> getSupportedMessages();

    @Override
    default int compareTo(Peer o) {
        return this.getPeerAddress().compareTo(o.getPeerAddress());
    }

    default String getCacheKey() {
        //return "pa=" + this.getPeerAddress().toString() + ",pid=" + this.getPeerId() + ",pname=" + this.getClientName();
        return getPeerAddress().getIp() + ':' + getPeerAddress().getPort();
    }

    /**
     * 获取该 Peer 的原始 IP 表示法，用于返回给下载器封禁 Peer
     *
     * @return 原始IP
     */
    String getRawIp();
}
