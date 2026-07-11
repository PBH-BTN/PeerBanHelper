package com.ghostchu.peerbanhelper.downloader.impl.aria2next.bean;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.peer.PeerFlag;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class A2Peer implements Peer {
    private Boolean amChoking;
    private String bitfield;
    private Long downloadSpeed;
    private String ip;
    private Boolean peerChoking;
    @SerializedName("peerId")
    private String peerIdEncoded;
    private Integer port;
    private Boolean seeder;
    private Long uploadSpeed;

    @Override
    public @NotNull PeerAddress getPeerAddress() {
        return new PeerAddress(ip, port, ip);
    }

    public String getPeerId(){
        return URLDecoder.decode(peerIdEncoded, StandardCharsets.ISO_8859_1);
    }

    @Override
    public @Nullable String getClientName() {
        return "";
    }

    @Override
    public long getDownloaded() {
        return 0;
    }

    @Override
    public long getUploaded() {
        return 0;
    }

    @Override
    public double getProgress() {
        return 0;
    }

    @Override
    public @Nullable PeerFlag getFlags() {
        return null;
    }

    @Override
    public boolean isHandshaking() {
        return false;
    }

    public double getPercent(){
      return calculateProgress(bitfield);
    }

    public static double calculateProgress(String hexBitfield) {
        if (hexBitfield == null || hexBitfield.isEmpty()) {
            return 0.0;
        }
        // 1. 一个十六进制字符代表 4 个比特 (bits)
        int totalBits = hexBitfield.length() * 4;
        // 2. 将十六进制字符串转换为 BigInteger
        // 核心痛点：如果 bitfield 以 0 开头（例如 "0f...")，
        // new BigInteger(hex, 16) 会吞掉高位的 0，导致总长度变短。
        // 但 totalBits = hexBitfield.length() * 4 已经锁定了正确的“理论总分片数”。
        BigInteger bi = new BigInteger(hexBitfield, 16);
        // 3. 统计二进制中 1 的个数（即已下载的分片数）
        // bitCount() 方法在 BigInteger 中专门用来数 1 的个数
        int downloadedPieces = bi.bitCount();
        // 4. 计算百分比
        double progress = ((double) downloadedPieces / totalBits) * 100;
        // 保留两位小数
        return Math.round(progress * 100.0) / 100.0;
    }
}
