package com.ghostchu.peerbanhelper.decentralized;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.database.dao.impl.DHTRecordDao;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.PBHPortMapper;
import com.offbynull.portmapper.mapper.PortType;
import io.ipfs.cid.Cid;
import io.ipfs.multiaddr.MultiAddress;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.peergos.*;
import org.peergos.blockstore.FileBlockstore;
import org.peergos.config.IdentitySection;
import org.peergos.protocol.http.HttpProtocol;
import org.peergos.util.Logging;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class DecentralizedManager {
    private final File directory;
    private final int listeningPort;
    private final boolean runNoBootstrap;
    private final @NotNull List<String> bootstrapNodes;
    private final File ipfsDirectory;
    private final PBHPortMapper pbhPortMapper;
    private final DHTRecordDao dhtRecordDao;
    private EmbeddedIpfs ipfs;

    public DecentralizedManager(PBHPortMapper pbhPortMapper, DHTRecordDao dhtRecordDao) {
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
        this.runNoBootstrap = Main.getMainConfig().getBoolean("decentralized.no-bootstrap", false);
        this.bootstrapNodes = Main.getMainConfig().getStringList("decentralized.bootstrap-nodes");
        Thread.startVirtualThread(this::startupNabu);
    }

    private void startupNabu() {
        List<MultiAddress> swarmAddresses = List.of(new MultiAddress("/ip6/::/tcp/" + listeningPort));
        List<MultiAddress> bootstrapAddresses = List.of(new MultiAddress("/dnsaddr/bootstrap.libp2p.io/p2p/QmQCU2EcMqAqQPR2i9bChDtGNJchTbq5TbXJJ16u19uLTa"));
        BlockRequestAuthoriser authoriser = (cid, peerid, auth) -> CompletableFuture.completedFuture(true);
        HostBuilder builder = new HostBuilder().generateIdentity();
        PrivKey privKey = builder.getPrivateKey();
        PeerId peerId = builder.getPeerId();
        IdentitySection identity = new IdentitySection(privKey.bytes(), peerId);
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

        List<Want> wants = List.of(new Want(Cid.decode("zdpuAwfJrGYtiGFDcSV3rDpaUrqCtQZRxMjdC6Eq9PNqLqTGg")));
        Set<PeerId> retrieveFrom = Set.of(PeerId.fromBase58("QmVdFZgHnEgcedCS2G2ZNiEN59LuVrnRm7z3yXtEBv2XiF"));
        boolean addToLocal = true;
        List<HashedBlock> blocks = ipfs.getBlocks(wants, retrieveFrom, addToLocal);
        byte[] data = blocks.get(0).block;
    }
}
