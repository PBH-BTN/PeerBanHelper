package com.ghostchu.peerbanhelper.util.traversal.btstun;

import com.ghostchu.peerbanhelper.BanList;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.ipdb.IPDBManager;
import com.ghostchu.peerbanhelper.util.lab.Laboratory;
import com.ghostchu.peerbanhelper.util.portmapper.PBHPortMapper;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.Forwarder;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.TCPForwarderImpl;
import com.ghostchu.peerbanhelper.util.traversal.stun.StunListener;
import com.ghostchu.peerbanhelper.util.traversal.stun.tunnel.StunTcpTunnel;
import com.ghostchu.peerbanhelper.util.traversal.stun.tunnel.StunTcpTunnelImpl;
import inet.ipaddr.HostName;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
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
    private final BanList banList;
    private final Laboratory laboratory;
    private final IPDBManager ipdb;
    @Getter
    private StunTcpTunnel tunnel;
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    @Nullable
    private Forwarder tcpForwarder;
    private @Nullable TranslationComponent shutdownReason = null;

    public BTStunInstance(BanList banList, PBHPortMapper portMapper, Downloader downloader, BTStunManager manager, Laboratory laboratory, IPDBManager ipdb) {
        this.banList = banList;
        this.portMapper = portMapper;
        this.downloader = downloader;
        this.manager = manager;
        this.laboratory = laboratory;
        this.ipdb = ipdb;
        if (!downloader.getFeatureFlags().contains(DownloaderFeatureFlag.LIVE_UPDATE_BT_PROTOCOL_PORT)) {
            throw new IllegalArgumentException(tlUI(Lang.AUTOSTUN_DOWNLOADER_NOT_SUPPORT_LIVE_UPDATE_PORT, downloader.getName()));
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
            this.tunnel.createMapping(ExternalSwitch.parseInt("pbh.btstun.localPort." + downloader.getId(), 0));
        } catch (IOException e) {
            log.warn(tlUI(Lang.BTSTUN_UNABLE_START, downloader.getName()), e);
            try {
                tunnel.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onCreate(@NotNull InetSocketAddress inter, @NotNull InetSocketAddress outer) {
        log.info(tlUI(Lang.BTSTUN_ON_TUNNEL_CREATED, downloader.getName(), inter.getHostString() + ":" + inter.getPort(), outer.getHostString() + ":" + outer.getPort()));
        var forwarderServerPort = inter.getPort();
        var downloaderShouldListenOn = outer.getPort();
        var downloaderHost = URI.create(downloader.getEndpoint()).getHost();
        log.info(tlUI(Lang.BTSTUN_FORWARDER_CREATING, downloader.getName()));
        String hostAddress = null;
        HostName hostName = new HostName(downloaderHost);
        if (hostName.isAddress()) {
            hostAddress = hostName.getAddress().toNormalizedString();
        } else {
            try {
                hostAddress = InetAddress.getByName(hostName.getHost()).getHostAddress();
            } catch (UnknownHostException e) {
                log.debug(tlUI(Lang.AUTOSTUN_DOWNLOADER_UNABLE_RESOLVE_DOWNLOADER_HOST, downloader.getName(), e));
            }
        }

        if (hostAddress != null) {
            var ipAddrObj = IPAddressUtil.getIPAddress(hostAddress);
            if (!ipAddrObj.isLocal() && !ipAddrObj.isAnyLocal() && !ipAddrObj.isLoopback() && !ipAddrObj.isLinkLocal()
                    && !ipAddrObj.isZeroHost() && !ipAddrObj.isMulticast() && !ExternalSwitch.parseBoolean("pbh.btstun.allowPublicIpAsDownloaderHost", false)) {
                manager.unregister(downloader);
                close();
                this.shutdownReason = new TranslationComponent(Lang.AUTOSTUN_DOWNLOADER_HOST_NOT_LAN_ADDRESS, downloaderHost);
                log.warn(tlUI(Lang.AUTOSTUN_DOWNLOADER_HOST_NOT_LAN_ADDRESS, downloaderHost));
                return;
            }
        }

        this.tcpForwarder = new TCPForwarderImpl(banList,
                ExternalSwitch.parseBoolean("pbh.btstun.ipv6support", true) ? "[::]" : "0.0.0.0",
                forwarderServerPort, downloaderHost, downloaderShouldListenOn, ipdb);
        tcpForwarder.start();
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
                log.debug(tlUI(Lang.AUTOSTUN_DOWNLOADER_UNABLE_TO_CLOSE_UNCAUGHT, downloader.getName()), ex);
            }
        }
    }

    @Override
    public void onClose(@Nullable Throwable throwable) {
        log.warn(tlUI(Lang.BTSTUN_ON_TUNNEL_CLOSE, downloader.getName()), throwable);
        if (throwable == null) {
            this.shutdownReason = null;
        } else {
            this.shutdownReason = new TranslationComponent(Lang.AUTOSTUN_DOWNLOADER_TUNNEL_ERRORED, throwable.getClass().getName() + ": " + throwable.getMessage());
        }
    }

    @Override
    public void onNotApplicable(@NotNull TranslationComponent reason) {
        try {
            close();
            this.shutdownReason = reason;
            log.warn(tlUI(Lang.BTSTUN_ON_TUNNEL_CLOSE_WITH_REASON, downloader.getName(), reason));
        } catch (Exception e) {
            log.error(tlUI(Lang.AUTOSTUN_DOWNLOADER_UNABLE_TO_SHUTDOWN), e);
        }
    }

    public @Nullable TranslationComponent getShutdownReason() {
        return shutdownReason;
    }

    public @Nullable Forwarder getTcpForwarder() {
        return tcpForwarder;
    }

    @Override
    public void close() {
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
