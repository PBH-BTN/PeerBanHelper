package com.ghostchu.peerbanhelper.decentralized.ipfs;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.decentralized.ipfs.impl.HybirdDHTRecordStore;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.exception.IPAddressBannedException;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import io.ipfs.multiaddr.MultiAddress;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.crypto.PubKey;
import io.libp2p.crypto.keys.Ed25519Kt;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.peergos.BlockRequestAuthoriser;
import org.peergos.EmbeddedIpfs;
import org.peergos.HostBuilder;
import org.peergos.blockstore.FileBlockstore;
import org.peergos.config.IdentitySection;
import org.peergos.protocol.http.HttpProtocol;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class IPFS implements Reloadable {
    private static final File dataDirectory = new File(Main.getDataDirectory(), "ipfs");
    private static final File blockStoreDirectory = new File(dataDirectory, "blockstore");

    static {
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }
        if (!blockStoreDirectory.exists()) {
            blockStoreDirectory.mkdirs();
        }
    }

    private final JavalinWebContainer webContainer;
    private final HybirdDHTRecordStore hybirdDHTRecordStore;

    @Getter
    private EmbeddedIpfs ipfs;
    @Getter
    private PrivKey identityEd25519Private;
    @Getter
    private PubKey identityEd25519Public;

    public IPFS(JavalinWebContainer webContainer, HybirdDHTRecordStore hybirdDHTRecordStore) throws IOException {
        this.webContainer = webContainer;
        this.hybirdDHTRecordStore = hybirdDHTRecordStore;
        Main.getReloadManager().register(this);
        Thread.ofVirtual().start(() -> {
            try {
                init(Main.getMainConfig().getInt("ipfs.port"), false);
            } catch (IOException e) {
                log.error("Unable to startup IPFS", e);
            }
        });
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    private void reloadConfig() throws IOException {
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }
        File privateKeyFile = new File(dataDirectory, "private-ed25519.key");
        File publicKeyFile = new File(dataDirectory, "public-ed25519.pub");
        if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
            var keyPair = Ed25519Kt.generateEd25519KeyPair();
            Files.write(privateKeyFile.toPath(), keyPair.component1().bytes());
            Files.write(publicKeyFile.toPath(), keyPair.component2().bytes());
        } else {
            identityEd25519Private = Ed25519Kt.unmarshalEd25519PrivateKey(Files.readAllBytes(privateKeyFile.toPath()));
            identityEd25519Public = Ed25519Kt.unmarshalEd25519PublicKey(Files.readAllBytes(publicKeyFile.toPath()));
        }
    }

    public void init(int port, boolean isRelayNode) throws IOException {
        reloadConfig();
        webContainer.javalin().beforeMatched("/p2p-api", ctx -> {
            if (ctx.routeRoles().isEmpty()) {
                return;
            }
            if (ctx.routeRoles().contains(Role.ANYONE)) {
                return;
            }
            if (!webContainer.allowAttemptLogin(ctx.ip())) {
                throw new IPAddressBannedException();
            }
            var token = ctx.header("X-P2P-LoopBack-WebAPI-Token");
            if (!webContainer.getToken().equals(token)) {
                ctx.status(403);
                ctx.json(new StdResp(false, "Unable to access P2P loop-back endpoints without valid webapi token (external access?)", null));
                webContainer.markLoginFailed(ctx.ip());
            }
        });

        List<MultiAddress> swarmAddresses = List.of(new MultiAddress("/ip6/::/tcp/" + port));
        List<MultiAddress> bootstrapAddresses = List.of(
                new MultiAddress("/dnsaddr/bootstrap.libp2p.io/p2p/QmQCU2EcMqAqQPR2i9bChDtGNJchTbq5TbXJJ16u19uLTa"),
                new MultiAddress("/dnsaddr/bootstrap.libp2p.io/p2p/QmbLHAnMoJPWSCR5Zhtx6BHJX9KiKNN6tpvbUcqanj75Nb"),
                new MultiAddress("/dnsaddr/bootstrap.libp2p.io/p2p/QmcZf59bWwK5XFi76CZX8cbJ4BhTzzA3gU1ZjYZcYW3dwt"),
                new MultiAddress("/ip4/104.131.131.82/tcp/4001/p2p/QmaCpDMGvV2BGHeYERUEnRQAwe3N8SzbUtfsmvsqQLuvuJ"),
                new MultiAddress("/ip4/25.196.147.100/tcp/4001/p2p/QmaMqSwWShsPg2RbredZtoneFjXhim7AQkqbLxib45Lx4S"),
                new MultiAddress("/ip4/104.131.131.82/tcp/4001/p2p/QmaCpDMGvV2BGHeYERUEnRQAwe3N8SzbUtfsmvsqQLuvuJ"), // mars.i.ipfs.io
                new MultiAddress("/ip4/104.131.131.82/tcp/4001/ipfs/QmaCpDMGvV2BGHeYERUEnRQAwe3N8SzbUtfsmvsqQLuvuJ"),
                new MultiAddress("/ip4/104.236.179.241/tcp/4001/ipfs/QmSoLPppuBtQSGwKDZT2M73ULpjvfd3aZ6ha4oFGL1KrGM"),
                new MultiAddress("/ip4/128.199.219.111/tcp/4001/ipfs/QmSoLSafTMBsPKadTEgaXctDQVcqN88CNLHXMkTNwMKPnu"),
                new MultiAddress("/ip4/104.236.76.40/tcp/4001/ipfs/QmSoLV4Bbm51jM9C4gDYZQ9Cy3U6aXMJDAbzgu2fzaDs64"),
                new MultiAddress("/ip4/178.62.158.247/tcp/4001/ipfs/QmSoLer265NRgSp2LA3dPaeykiS1J6DifTC88f5uVQKNAd"),
                new MultiAddress("/ip6/2604:a880:1:20:0:0:203:d001/tcp/4001/ipfs/QmSoLPppuBtQSGwKDZT2M73ULpjvfd3aZ6ha4oFGL1KrGM"),
                new MultiAddress("/ip6/2400:6180:0:d0:0:0:151:6001/tcp/4001/ipfs/QmSoLSafTMBsPKadTEgaXctDQVcqN88CNLHXMkTNwMKPnu"),
                new MultiAddress("/ip6/2604:a880:800:10:0:0:4a:5001/tcp/4001/ipfs/QmSoLV4Bbm51jM9C4gDYZQ9Cy3U6aXMJDAbzgu2fzaDs64"),
                new MultiAddress("/ip6/2a03:b0c0:0:1010:0:0:23:1001/tcp/4001/ipfs/QmSoLer265NRgSp2LA3dPaeykiS1J6DifTC88f5uVQKNAd")
        );
        BlockRequestAuthoriser authoriser = (cid, peerid, auth) -> CompletableFuture.completedFuture(true);
        HostBuilder builder = new HostBuilder().setPrivKey(identityEd25519Private);
        PrivKey privKey = builder.getPrivateKey();
        PeerId peerId = builder.getPeerId();
        IdentitySection identity = new IdentitySection(privKey.bytes(), peerId);
        boolean provideBlocks = true;
        EmbeddedIpfs ipfs = EmbeddedIpfs.build(
                hybirdDHTRecordStore,
                new FileBlockstore(blockStoreDirectory.toPath()),
                provideBlocks,
                swarmAddresses,
                bootstrapAddresses,
                identity,
                authoriser,
                Optional.of(proxyHandler())
        );
        log.info(tlUI(Lang.IPFS_STARTING));
        ipfs.start();
        log.info(tlUI(Lang.IPFS_STARTED));
        this.ipfs = ipfs;
        if (isRelayNode) {
            PBHRelay.advertise(ipfs.dht, ipfs.node);
            log.info(tlUI(Lang.IPFS_ADVERTISE_RELAY));
        }
    }

    private void initJavalinEndpoints() {
        webContainer.javalin()
                .get("/p2p-api/heartbeat", ctx -> ctx.status(HttpStatus.NO_CONTENT))
                .get("/p2p-api/manifest", this::manifest)
                .post("/p2p-api/keypair/sign", this::sign);
    }

    private void sign(Context context) {
        context.result(new String(identityEd25519Private.sign(context.bodyAsBytes())));
    }

    private void manifest(Context context) {
        context.json(new IPFS.Manifest(Main.getMeta().getVersion()));
    }


    private HttpProtocol.HttpRequestProcessor proxyHandler() {
        return (s, req, h) -> {
            var host = webContainer.getHost();
            if (host.equals("0.0.0.0")) {
                host = "127.0.0.1";
            }
            if (host.equals("::") || host.equals("[::]")) {
                host = "::1";
            }
            SocketAddress httpTarget = new InetSocketAddress(host, webContainer.javalin().port());
            if (!req.uri().startsWith("/p2p-api/")) {
                var ct = "Access denied - P2P can only access /p2p-api/ endpoints";
                FullHttpResponse replyOk = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN, Unpooled.wrappedBuffer(ct.getBytes(StandardCharsets.UTF_8)));
                replyOk.headers().set(HttpHeaderNames.CONTENT_LENGTH, ct.length());
                h.accept(replyOk.retain());
                return;
            }
            req.headers().add("X-P2P-PeerID", s.remotePeerId().toBase58());
            req.headers().add("X-P2P-LoopBack-WebAPI-Token", webContainer.getToken());
            HttpProtocol.proxyRequest(req, httpTarget, h);
        };
    }

    public record Manifest(
            String version
    ) {

    }
//
//    public void checkRelays(PrivKey identityEd25519) {
//        relayListOperationLock.lock();
//        try {
//            var newRelays = PBHRelay.findRelays(ipfs.dht, ipfs.node);
//            if (relays.isEmpty()) {
//                return;
//            }
//            relays.clear();
//            relays.addAll(newRelays);;
//        } finally {
//            relays.forEach(pa->);
//            relayListOperationLock.unlock();
//        }
//    }
//
//    public void reserveOnRelay(PrivKey identityEd25519, PeerAddresses relay){
//        HostBuilder builder = new HostBuilder().setPrivKey(identityEd25519);
//        Multiaddr relayAddr = Multiaddr.fromString(relay.getPublicAddresses().get(0).toString())
//                .withP2P(PeerId.fromBase58(relay.peerId.toBase58()));
//        CircuitHopProtocol.HopController hop = builder.getRelayHop().get().dial(ipfs.node, relayAddr).getController().join();
//        // ???
//        hop.reserve();
//    }
//
//    public byte[] getData(Cid cid, @Nullable Set<PeerId> retrieveFrom, boolean persist) {
//        List<Want> wants = List.of(new Want(cid));
//        boolean addToLocal = true;
//        List<HashedBlock> blocks = ipfs.getBlocks(wants, retrieveFrom, addToLocal);
//        byte[] data = blocks.get(0).block;
//    }
}
