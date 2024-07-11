//package com.ghostchu.peerbanhelper.downloader.impl.rtorrent.bean;
//
//import com.ghostchu.peerbanhelper.downloader.PeerFlag;
//import com.ghostchu.peerbanhelper.peer.Peer;
//import com.ghostchu.peerbanhelper.util.StrUtil;
//import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
//import lombok.AllArgsConstructor;
//import lombok.Data;
//import org.jetbrains.annotations.Nullable;
//
//@AllArgsConstructor
//@Data
//public final class RPeer implements Peer {
//    /*
//            "2D7142343630302D217078332E4C5828364E454E",
//        "46.185.189.249",
//        "qBittorrent 4.6.0.0",
//        "0",
//        "1",
//        "0",
//        "100",
//        "192079",
//        "0",
//        "6402",
//        "0",
//        "-qB4600-%21px3%2ELX%286NEN",
//        "0",
//        "0",
//        "5694"
//     */
//    private final String id;
//    private final String address;
//    private final String clientVersion;
//    private final boolean incoming;
//    private final boolean encrypted;
//    private final boolean snubbed;
//    private final long completedPercent;
//    private final long downTotal;
//    private final long upTotal;
//    private final long downRate;
//    private final long upRate;
//    private final String idHtml;
//    private final long peerRate;
//    private final long peerTotal;
//    private final int port;
//    private transient PeerAddress peerAddress;
//
//    public RPeer(String[] args) {
//        this.id = args[0];
//        this.address = args[1];
//        this.clientVersion = args[2];
//        this.incoming = Boolean.parseBoolean(args[3]);
//        this.encrypted = Boolean.parseBoolean(args[4]);
//        this.snubbed = Boolean.parseBoolean(args[5]);
//        this.completedPercent = Long.parseLong(args[6]);
//        this.downTotal = Long.parseLong(args[7]);
//        this.upTotal = Long.parseLong(args[8]);
//        this.downRate = Long.parseLong(args[9]);
//        this.upRate = Long.parseLong(args[10]);
//        this.idHtml = args[11];
//        this.peerRate = Long.parseLong(args[12]);
//        this.peerTotal = Long.parseLong(args[13]);
//        this.port = Integer.parseInt(args[14]);
//    }
//
//    @Override
//    public PeerAddress getPeerAddress() {
//        if (this.peerAddress == null) {
//            this.peerAddress = new PeerAddress(address, port);
//        }
//        return this.peerAddress;
//    }
//
//    @Override
//    public String getPeerId() {
//        return StrUtil.toStringHex(id);
//    }
//
//    @Override
//    public String getClientName() {
//        return clientVersion;
//    }
//
//    @Override
//    public long getDownloadSpeed() {
//        return downRate;
//    }
//
//    @Override
//    public long getDownloaded() {
//        return downTotal;
//    }
//
//    @Override
//    public long getUploadSpeed() {
//        return upRate;
//    }
//
//    @Override
//    public long getUploaded() {
//        return upTotal;
//    }
//
//    @Override
//    public double getProgress() {
//        return completedPercent / 100.0d;
//    }
//
//    @Override
//    public @Nullable PeerFlag getFlags() {
//        return null;
//    }
//}
