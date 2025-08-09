package com.ghostchu.peerbanhelper.util.traversal.btstun;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.PBHPortMapper;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.Forwarder;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.TCPForwarderImpl;
import com.ghostchu.peerbanhelper.util.traversal.stun.StunListener;
import com.ghostchu.peerbanhelper.util.traversal.stun.tunnel.StunTcpTunnel;
import com.ghostchu.peerbanhelper.util.traversal.stun.tunnel.StunTcpTunnelImpl;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class BTStunInstance implements StunListener, AutoCloseable, NatAddressProvider {
    private final Downloader downloader;
    private final PBHPortMapper portMapper;
    private final BTStunManager manager;
    private final Map<PeerAddress, ?> banList;
    private StunTcpTunnel tunnel;
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    @Nullable
    private Forwarder tcpForwarder;

    public BTStunInstance(Map<PeerAddress, ?> banList, PBHPortMapper portMapper, Downloader downloader, BTStunManager manager) {
        this.banList = banList;
        this.portMapper = portMapper;
        this.downloader = downloader;
        this.manager = manager;
        if (!downloader.getFeatureFlags().contains(DownloaderFeatureFlag.LIVE_UPDATE_BT_PROTOCOL_PORT)) {
            throw new IllegalArgumentException("Downloader does not support live update of BT protocol port");
        }
        sched.scheduleWithFixedDelay(() -> {
            try {
                if (shutdown.get()) return;
                restart();
            } catch (Exception e) {
                log.error(tlUI(Lang.BTSTUN_RESTART_FAILED, downloader.getName()), e);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private synchronized void restart() {
        if (this.tunnel != null) {
            if (this.tunnel.isValid()) return;
            try {
                this.tunnel.close();
            } catch (Exception ignored) {
            }
        }
        log.info(tlUI(Lang.BTSTUN_RESTART, downloader.getName()));
        this.tunnel = new StunTcpTunnelImpl(portMapper, this);
        try {
            this.tunnel.createMapping(ExternalSwitch.parseInt("pbh.btstun.localPort", 0));
        } catch (IOException e) {
            log.warn(tlUI(Lang.BTSTUN_UNABLE_START, downloader.getName()), e);
            try {
                tunnel.close();
            } catch (Exception ignored) {
            }
        }
    }

    public StunTcpTunnel getTunnel() {
        return tunnel;
    }

    @Override
    public void onCreate(@NotNull InetSocketAddress inter, @NotNull InetSocketAddress outer) {
        log.info(tlUI(Lang.BTSTUN_ON_TUNNEL_CREATED, downloader.getName(), inter.getHostString() + ":" + inter.getPort(), outer.getHostString() + ":" + outer.getPort()));
        var forwarderServerPort = inter.getPort();
        var downloaderShouldListenOn = outer.getPort();
        var downloaderHost = URI.create(downloader.getEndpoint()).getHost();
        log.info(tlUI(Lang.BTSTUN_FORWARDER_CREATING, downloader.getName()));
        this.tcpForwarder = new TCPForwarderImpl(banList,
                ExternalSwitch.parseBoolean("pbh.btstun.ipv6support", true) ? "[::]" : "0.0.0.0",
                forwarderServerPort, downloaderHost, downloaderShouldListenOn);
        try {
            tcpForwarder.start();
        } catch (IOException e) {
            log.info(tlUI(Lang.BTSTUN_FORWARDER_EXCEPTION, downloader.getName()), e);
            try {
                tunnel.close();
            } catch (Exception ignored) {
            }
        }
        try {
            if (downloader.getBTProtocolPort() != downloaderShouldListenOn) {
                log.info(tlUI(Lang.BTSTUN_MODIFY_DOWNLOADER_BT_PORT, downloader.getName(), downloaderShouldListenOn));
                downloader.setBTProtocolPort(downloaderShouldListenOn);
                log.info(tlUI(Lang.BTSTUN_TUNNEL_CREATE_SUCCESSFULLY, downloader.getName(), downloaderHost + ":" + downloaderShouldListenOn, inter.getHostString() + ":" + inter.getPort(), outer.getHostString() + ":" + outer.getPort()));
            }
        } catch (Exception e) {
            log.warn(tlUI(Lang.AUTOSTUN_DOWNLOADER_TUNNEL_UPDATE_PORT_FAILED, downloader.getName()), e);
            try {
                manager.unregister(downloader);
            } catch (Exception ex) {
                log.debug("(Uncaught) Unable to close", ex);
            }
        }
    }

    @Override
    public void onClose(@Nullable Throwable throwable) {
        log.info(tlUI(Lang.BTSTUN_ON_TUNNEL_CLOSE, downloader.getName()), throwable);
    }

    public @Nullable Forwarder getTcpForwarder() {
        return tcpForwarder;
    }

    @Override
    public void close() throws Exception {
        shutdown.set(true);
        sched.shutdown();
        if (this.tunnel != null) {
            try {
                this.tunnel.close();
            } catch (Exception ignored) {
            }
        }
        try {
            if (!sched.awaitTermination(10, TimeUnit.SECONDS)) {
                sched.shutdownNow();
            }
        } catch (InterruptedException e) {
            sched.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public @Nullable InetSocketAddress translate(@Nullable InetSocketAddress nattedAddress) {
        if (tcpForwarder != null) {
            return tcpForwarder.translate(nattedAddress);
        }
        return null;
    }
}
