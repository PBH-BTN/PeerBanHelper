package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.dao.impl.HistoryDao;
import com.ghostchu.peerbanhelper.database.dao.impl.PeerRecordDao;
import com.ghostchu.peerbanhelper.database.dao.impl.TorrentDao;
import com.ghostchu.peerbanhelper.decentralized.ipfs.IPFS;
import com.ghostchu.peerbanhelper.friend.FriendManager;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
@IgnoreScan
@Slf4j
public class PBHFriendController extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;
    private final TorrentDao torrentDao;
    private final PeerRecordDao peerRecordDao;
    private final HistoryDao historyDao;
    private final FriendManager friendManager;
    private final IPFS ipfs;


    public PBHFriendController(JavalinWebContainer javalinWebContainer, TorrentDao torrentDao, PeerRecordDao peerRecordDao, HistoryDao historyDao, FriendManager friendManager, IPFS ipfs) {
        this.javalinWebContainer = javalinWebContainer;
        this.torrentDao = torrentDao;
        this.historyDao = historyDao;
        this.peerRecordDao = peerRecordDao;
        this.friendManager = friendManager;
        this.ipfs = ipfs;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "Friend Controller";
    }

    @Override
    public @NotNull String getConfigName() {
        return "friend-controller";
    }

    @Override
    public void onEnable() {
        javalinWebContainer
                .javalin()
                //.get("/api/torrent", this::handleTorrentQuery, Role.USER_READ)
                .get("/api/friend/list", this::handleFriendList, Role.USER_READ)
                .get("/api/friend/metadata", this::handleFriendMetadata, Role.USER_READ);
    }

    private void handleFriendMetadata(Context context) {
        Map<String, Object> meta = new HashMap<>();
        if (ipfs.getIpfs() == null) {
            context.json(new StdResp(false, "IPFS starting up", null));
            return;
        }
        meta.put("publicKey", Base64.getEncoder().encodeToString(ipfs.getIdentityEd25519Public().bytes()));
        meta.put("peerId", ipfs.getIpfs().node.getPeerId().toHex());
        context.json(new StdResp(true, null, meta));
    }

    private void handleFriendList(Context context) {
        var list = friendManager.getFriends().stream().map(
                friend -> new FriendDto(friend.getPeerId(),
                        Base64.getEncoder().encodeToString(friend.getPubKey()),
                        friend.getLastAttemptConnectTime(),
                        friend.getLastCommunicationTime(),
                        friend.getLastRecordedPBHVersion(),
                        friend.isConnected(),
                        friend.getLastRecordedConnectionStatus())
        ).toList();
        context.json(new StdResp(true, null, list));
    }

    @Override
    public void onDisable() {

    }

    public record FriendDto(
            String peerId,
            String pubKey,
            long lastAttemptConnectTime,
            long lastCommunicationTime,
            String lastRecordedPBHVersion,
            boolean connected,
            String lastRecordedConnectionStatus
    ) {

    }
}
