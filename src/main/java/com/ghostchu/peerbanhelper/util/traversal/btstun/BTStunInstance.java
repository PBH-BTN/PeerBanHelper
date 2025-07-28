package com.ghostchu.peerbanhelper.util.traversal.btstun;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.DownloaderFeatureFlag;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.PBHPortMapper;
import com.ghostchu.peerbanhelper.util.traversal.forwarder.TCPForwarder;
import com.ghostchu.peerbanhelper.util.traversal.stun.StunListener;
import com.ghostchu.peerbanhelper.util.traversal.stun.StunTcpTunnel;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class BTStunInstance implements StunListener, AutoCloseable {
    private final Downloader downloader;
    private final PBHPortMapper portMapper;
    private StunTcpTunnel tunnel;
    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public BTStunInstance(PBHPortMapper portMapper, Downloader downloader) {
        this.portMapper = portMapper;
        this.downloader = downloader;
        if (!downloader.getFeatureFlags().contains(DownloaderFeatureFlag.LIVE_UPDATE_BT_PROTOCOL_PORT)) {
            throw new IllegalArgumentException("Downloader does not support live update of BT protocol port");
        }
        sched.scheduleWithFixedDelay(() -> {
            try {
                if (shutdown.get()) return;
                restart();
            } catch (Exception e) {
                log.error("restart failed", e);
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
        this.tunnel = new StunTcpTunnel(portMapper, this);
        try {
            this.tunnel.createMapping(0);
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
        TCPForwarder forwarder = new TCPForwarder("0.0.0.0", inter.getPort(), downloaderHost, forwarderServerPort,
                inter.getHostString(), inter.getPort());
        try {
            forwarder.start();
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
            log.warn("(Uncaught) Unable to set BT protocol port for downloader: {}", downloader.getName(), e);
        }
    }

    @Override
    public void onClose(@Nullable Throwable throwable) {
        log.info(tlUI(Lang.BTSTUN_ON_TUNNEL_CLOSE, downloader.getName()), throwable);
    }

    @Override
    public void close() throws Exception {
        shutdown.set(true);
        sched.shutdownNow();
        if (this.tunnel != null) {
            try {
                this.tunnel.close();
            } catch (Exception ignored) {
            }
        }
    }
}
