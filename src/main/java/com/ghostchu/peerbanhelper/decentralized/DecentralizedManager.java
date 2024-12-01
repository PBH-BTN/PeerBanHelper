package com.ghostchu.peerbanhelper.decentralized;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.database.dao.impl.DHTRecordDao;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.PBHPortMapper;
import com.offbynull.portmapper.mapper.PortType;
import io.ipfs.cid.Cid;
import io.ipfs.multiaddr.MultiAddress;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.crypto.keys.Ed25519Kt;
import lombok.extern.slf4j.Slf4j;
import org.peergos.BlockRequestAuthoriser;
import org.peergos.EmbeddedIpfs;
import org.peergos.HostBuilder;
import org.peergos.Want;
import org.peergos.blockstore.FileBlockstore;
import org.peergos.config.IdentitySection;
import org.peergos.protocol.http.HttpProtocol;
import org.peergos.util.Logging;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class DecentralizedManager {
    private final File directory;
    private final int listeningPort;
    private final File ipfsDirectory;
    private final PBHPortMapper pbhPortMapper;
    private final DHTRecordDao dhtRecordDao;
    private EmbeddedIpfs ipfs;
    private PrivKey privKey;
    private PeerId peerId;
    private IdentitySection identity;

    public DecentralizedManager(PBHPortMapper pbhPortMapper, DHTRecordDao dhtRecordDao) throws IOException {
        this.pbhPortMapper = pbhPortMapper;
        this.dhtRecordDao = dhtRecordDao;
        this.directory = new File(Main.getDataDirectory(), "decentralized");
        if (!this.directory.exists()) {
            this.directory.mkdirs();
        }
        this.ipfsDirectory = new File(directory, "ipfs-blockstore");
        if (!this.ipfsDirectory.exists()) {
            this.ipfsDirectory.mkdirs();
        }
        this.listeningPort = Main.getMainConfig().getInt("decentralized.port", 9897);
        this.privKey = getPrivKey();
        this.peerId = PeerId.fromPubKey(privKey.publicKey());
        this.identity = new IdentitySection(privKey.bytes(), peerId);
        this.startupNabu();
    }

    private void startupNabu() {
        List<MultiAddress> swarmAddresses = List.of(new MultiAddress("/ip6/::/tcp/" + listeningPort));
        List<MultiAddress> bootstrapAddresses = List.of(new MultiAddress("/dnsaddr/bootstrap.libp2p.io/p2p/QmQCU2EcMqAqQPR2i9bChDtGNJchTbq5TbXJJ16u19uLTa"));
        BlockRequestAuthoriser authoriser = (cid, peerid, auth) -> CompletableFuture.completedFuture(true);
        boolean provideBlocks = true;
        SocketAddress httpTarget = new InetSocketAddress("localhost", 10000);
        Optional<HttpProtocol.HttpRequestProcessor> httpProxyTarget =
                Optional.of((s, req, h) -> HttpProtocol.proxyRequest(req, httpTarget, h));
        Logging.LOG().setFilter(record -> {
            Level slf4jLevel = switch (record.getLevel().intValue()) {
                case 1000 -> Level.TRACE;
                case 900 -> Level.DEBUG;
                case 800 -> Level.INFO;
                case 700 -> Level.WARN;
                case 500 -> Level.ERROR;
                default -> Level.INFO;
            };
            LoggerFactory.getLogger(record.getLoggerName())
                    .makeLoggingEventBuilder(slf4jLevel)
                    .addArgument(record.getParameters())
                    .log(record.getMessage(), record.getThrown());
            return false;
        });
        this.ipfs = EmbeddedIpfs.build(new HybirdDHTRecordStore(dhtRecordDao),
                new FileBlockstore(ipfsDirectory.toPath()),
                provideBlocks,
                swarmAddresses,
                bootstrapAddresses,
                identity,
                authoriser,
                httpProxyTarget
        );
        ipfs.start();

        var mapper = pbhPortMapper.getPortMapper();
        if (mapper != null) {
            pbhPortMapper.mapPort(mapper, PortType.TCP, listeningPort).thenAccept(mappedPort -> {
                if (mappedPort != null) {
                    log.info(tlUI(Lang.DECENTRALIZED_PORT_FORWARDED, mappedPort.getInternalPort(), mappedPort.getExternalPort(), mappedPort.getExternalAddress().getHostAddress()));
                }
            });
        }
    }

    public void publishValue(byte[] data, long seq, int hoursTTL){;
        ipfs.publishValue(privKey,data,seq,hoursTTL);
    }

    public void publishValueToIpns(Multihash ipns, byte[] data, long seq, int hoursTTL){
        // WTF?
    }

    public PrivKey getPrivKey() throws IOException {
        File privKey = new File(directory, "ipfs.key");
        if (!privKey.exists()) {
            HostBuilder builder = new HostBuilder().generateIdentity();
            Files.write(privKey.toPath(), builder.getPrivateKey().bytes());
        }
        return Ed25519Kt.unmarshalEd25519PrivateKey(Files.readAllBytes(privKey.toPath()));
    }

    public CompletableFuture<byte[]> getBlockFromIPNS(Cid ipnsPointerCid) {
        return CompletableFuture.supplyAsync(() -> {
            var future = ipfs.dht.resolveIpnsValue(ipnsPointerCid, ipfs.node, 1);
            String contentCid = future.join();
            return getBlockFromCid(Cid.decode(contentCid)).join();
        });
    }

    public CompletableFuture<byte[]> getBlockFromCid(Cid cid) {
        return CompletableFuture.supplyAsync(() -> {
            var blocks = ipfs.getBlocks(List.of(new Want(cid)), null, true);
            // there have multiple blocks, we need connect all byte[]
            return blocks.getFirst().block;
        });
    }
}
