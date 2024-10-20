package com.ghostchu.peerbanhelper.friend;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.decentralized.ipfs.IPFS;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.PeerId;
import io.libp2p.crypto.keys.Ed25519Kt;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import lombok.Getter;
import org.peergos.EmbeddedIpfs;
import org.peergos.protocol.http.HttpProtocol;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public class Friend {
    @Getter
    private final String peerId;
    @Getter
    private final byte[] pubKey;
    private final transient IPFS ipfs;
    @Getter
    private long lastAttemptConnectTime;
    @Getter
    private long lastCommunicationTime;
    @Getter
    private String lastRecordedPBHVersion;
    @Getter
    private transient boolean connected;
    @Getter
    private String lastRecordedConnectionStatus;
    private transient HttpProtocol.HttpController controller;

    public Friend(IPFS ipfs, String peerId, byte[] pubKey, long lastAttemptConnectTime, long lastCommunicationTime, String lastRecordedPBHVersion) {
        this.ipfs = ipfs;
        this.pubKey = pubKey;
        this.peerId = peerId;
        this.lastAttemptConnectTime = lastAttemptConnectTime;
        this.lastCommunicationTime = lastCommunicationTime;
        this.lastRecordedPBHVersion = lastRecordedPBHVersion;
    }

    public HttpProtocol.HttpController connectAndGetController() {
        if (!connect()) {
            return null;
        }
        return controller;
    }

    public boolean connect() {
        if (controller != null) {
            FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/p2p-api/heartbeat");
            httpRequest.headers().add("User-Agent", Main.getUserAgent());
            if (controller.send(httpRequest).join().status() == HttpResponseStatus.NO_CONTENT) {
                return connectSuccess("Connection valid", controller);
            }
        }
        lastAttemptConnectTime = System.currentTimeMillis();
        var ipfs = this.ipfs.getIpfs();
        if (ipfs == null) {
            return connectFailed("IPFS component not ready, try again later.");
        }
        try {
            var addrs = EmbeddedIpfs.getAddresses(ipfs.node, ipfs.dht, Multihash.fromBase58(peerId));
            HttpProtocol.HttpController proxier = ipfs.p2pHttp.get().dial(ipfs.node, PeerId.fromBase58(peerId), addrs).getController().join();
            byte[] proofData = UUID.randomUUID().toString().getBytes(StandardCharsets.ISO_8859_1);
            FullHttpRequest peerTrustTest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/p2p-api/keypair/sign", Unpooled.wrappedBuffer(proofData));
            peerTrustTest.headers().set(HttpHeaderNames.CONTENT_LENGTH, proofData.length);
            if (!Ed25519Kt.unmarshalEd25519PublicKey(pubKey).verify(proofData, extractString(proxier.send(peerTrustTest).join().content()).getBytes(StandardCharsets.ISO_8859_1))) {
                return connectFailed("Signature verify failed, MITM?");
            }
            FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/manifest");
            var response = proxier.send(httpRequest).join();
            if (response.status() == HttpResponseStatus.OK) {
                var manifest = JsonUtil.standard().fromJson(response.content().toString(), IPFS.Manifest.class);
                lastRecordedPBHVersion = manifest.version();
                return connectSuccess("Connected", proxier);
            } else {
                return connectFailed("Connected, but no excepted response received.");
            }
        } catch (Exception e) {
            return connectFailed(e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private String extractString(ByteBuf buf) {
        return buf.getCharSequence(0, buf.readableBytes(), StandardCharsets.ISO_8859_1).toString();
    }

    private boolean connectSuccess(String reason, HttpProtocol.HttpController controller) {
        lastRecordedConnectionStatus = reason;
        lastCommunicationTime = System.currentTimeMillis();
        connected = true;
        this.controller = controller;
        System.out.println("Connected: " + reason);
        return true;
    }

    private boolean connectFailed(String reason) {
        lastRecordedConnectionStatus = reason;
        connected = false;
        System.out.println("Connect failed: " + reason);
        return false;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Friend friend = (Friend) object;
        return Objects.equals(peerId, friend.peerId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(peerId);
    }

}
