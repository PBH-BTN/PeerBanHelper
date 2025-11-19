package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.util.traversal.NatAddressProvider;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddress;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.List;

public abstract class AbstractDownloader implements Downloader {
    public final AlertManager alertManager;
    protected final String id;
    private final NatAddressProvider natAddressProvider;
    private DownloaderLastStatus lastStatus = DownloaderLastStatus.UNKNOWN;
    private TranslationComponent statusMessage = new TranslationComponent(Lang.STATUS_TEXT_UNKNOWN);
    private int failedLoginAttempts = 0;
    private long nextLoginTry = 0L;

    public AbstractDownloader(String id, AlertManager alertManager, NatAddressProvider natAddressProvider) {
        this.id = id;
        this.alertManager = alertManager;
        this.natAddressProvider = natAddressProvider;
    }

    @NotNull
    public PeerAddress natTranslate(PeerAddress peerAddress) {
        InetSocketAddress inetSocketAddress = new InetSocketAddress(peerAddress.getIp(), peerAddress.getPort());
        var translate = natAddressProvider.translate(inetSocketAddress);
        if (translate == null) return peerAddress;
        return peerAddress.setNat(translate.getHostString(), translate.getPort());
    }

    @Override
    public @NotNull String getId() {
        return id;
    }

    @Override
    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    @Override
    public @NotNull DownloaderLoginResult login() {
        if (isPaused()) {
            lastStatus = DownloaderLastStatus.PAUSED;
            statusMessage = new TranslationComponent(Lang.STATUS_TEXT_PAUSED);
            return new DownloaderLoginResult(DownloaderLoginResult.Status.PAUSED, new TranslationComponent(Lang.DOWNLOADER_PAUSED));
        }
        if (nextLoginTry >= System.currentTimeMillis()) {
            alertManager.publishAlert(true,
                    AlertLevel.WARN,
                    "downloader-too-many-failed-attempt-" + getId(),
                    new TranslationComponent(Lang.DOWNLOADER_ALERT_TOO_MANY_FAILED_ATTEMPT_TITLE, getName()),
                    new TranslationComponent(Lang.DOWNLOADER_ALERT_TOO_MANY_FAILED_ATTEMPT_DESCRIPTION, getName(),
                            getLastStatus(),
                            getLastStatusMessage()));
            return new DownloaderLoginResult(DownloaderLoginResult.Status.REQUIRE_TAKE_ACTIONS
                    , new TranslationComponent(Lang.TOO_MANY_FAILED_ATTEMPT, MsgUtil.getDateFormatter().format(new Date(nextLoginTry)))
            );
        }
        DownloaderLoginResult result;
        try {
            result = login0();
            if (result.success()) {
                failedLoginAttempts = 0;
                return result;
            }
            if (result.status() == DownloaderLoginResult.Status.INCORRECT_CREDENTIAL)
                failedLoginAttempts++;
            return result;
        } catch (Throwable e) {
            failedLoginAttempts++;
            return new DownloaderLoginResult(DownloaderLoginResult.Status.EXCEPTION, new TranslationComponent(e.getMessage()));
        } finally {
            if (failedLoginAttempts >= 15) {
                nextLoginTry = System.currentTimeMillis() + (1000 * 60 * 30);
                failedLoginAttempts = 0;
            }
        }
    }

    @Override
    public synchronized void setPaused(boolean paused) {
        if (paused) {
            lastStatus = DownloaderLastStatus.PAUSED;
            statusMessage = new TranslationComponent(Lang.STATUS_TEXT_PAUSED);
        } else {
            lastStatus = DownloaderLastStatus.UNKNOWN;
            statusMessage = null;
        }
    }


    public abstract DownloaderLoginResult login0() throws Exception;

    @Override
    public @NotNull DownloaderLastStatus getLastStatus() {
        return lastStatus;
    }

    @Override
    public void setLastStatus(@NotNull DownloaderLastStatus lastStatus, @NotNull TranslationComponent statusMessage) {
        this.lastStatus = lastStatus;
        this.statusMessage = statusMessage;
    }

    @Override
    public @NotNull TranslationComponent getLastStatusMessage() {
        return statusMessage;
    }

    @Override
    public @NotNull DownloaderStatistics getStatistics() {
        return new DownloaderStatistics(0, 0);
    }

    @Override
    public @NotNull List<DownloaderFeatureFlag> getFeatureFlags() {
        return List.of(DownloaderFeatureFlag.UNBAN_IP);
    }

    @Override
    public int getMaxConcurrentPeerRequestSlots() {
        return ExternalSwitch.parseInt("pbh.downloader.AbstractDownloader.maxConcurrentPeerRequestSlots", 16);
    }

    @NotNull
    public IPAddress remapBanListAddress(@NotNull IPAddress banAddress) {
        return IPAddressUtil.remapBanListAddress(banAddress);
    }

    @Override
    public boolean equals(Object obj) {
        // check id if equals
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AbstractDownloader that = (AbstractDownloader) obj;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
