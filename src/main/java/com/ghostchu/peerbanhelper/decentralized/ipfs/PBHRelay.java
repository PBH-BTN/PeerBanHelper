package com.ghostchu.peerbanhelper.decentralized.ipfs;

import io.ipfs.multihash.Multihash;
import io.libp2p.core.Host;
import org.peergos.Hash;
import org.peergos.PeerAddresses;
import org.peergos.protocol.dht.Kademlia;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class PBHRelay {
    public static final Multihash RELAY_RENDEZVOUS_NAMESPACE;

    static {
        RELAY_RENDEZVOUS_NAMESPACE = new Multihash(Multihash.Type.sha2_256, Hash.sha256("/peerbanhelper/relay".getBytes(StandardCharsets.UTF_8)));
    }

    public PBHRelay() {
    }

    public static void advertise(Kademlia dht, Host us) {
        dht.provideBlock(RELAY_RENDEZVOUS_NAMESPACE, us, PeerAddresses.fromHost(us)).join();
    }

    public static List<PeerAddresses> findRelays(Kademlia dht, Host us) {
        return dht.findProviders(RELAY_RENDEZVOUS_NAMESPACE, us, 20).join();
    }
}
