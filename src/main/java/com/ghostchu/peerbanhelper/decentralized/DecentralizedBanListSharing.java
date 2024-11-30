package com.ghostchu.peerbanhelper.decentralized;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.event.PeerBanEvent;
import com.ghostchu.peerbanhelper.event.PeerUnbanEvent;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.google.common.eventbus.Subscribe;
import io.ipfs.multihash.Multihash;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class DecentralizedBanListSharing {
    private final DecentralizedManager decentralizedManager;
    private final PeerBanHelperServer peerBanHelperServer;

    public DecentralizedBanListSharing(DecentralizedManager decentralizedManager, PeerBanHelperServer peerBanHelperServer) {
        this.decentralizedManager = decentralizedManager;
        this.peerBanHelperServer = peerBanHelperServer;
        Main.getEventBus().register(this);
        onPeerUpdate();
    }

    @Subscribe
    public void onPeerBanEvent(PeerBanEvent event){
        onPeerUpdate();
    }

    @Subscribe
    public void onPeerUnBanEvent(PeerUnbanEvent event){
        onPeerUpdate();
    }

    private void onPeerUpdate() {
        var peerId = decentralizedManager.getPeerId();
        decentralizedManager.publishValueToIpns(
                Multihash.deserialize(peerId.getBytes()),
                JsonUtil.standard().toJson(peerBanHelperServer.getBannedPeers()).getBytes(StandardCharsets.UTF_8),
                System.currentTimeMillis() / 1000,1000*60*60*24*7);
        Thread.startVirtualThread(()->{
            try {
                Thread.sleep(1000*30);
                System.out.println(new String(decentralizedManager.getBlockFromIPNS(Multihash.deserialize(peerId.getBytes())).join(), StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

}
