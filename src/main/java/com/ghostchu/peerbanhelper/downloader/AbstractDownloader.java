package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public abstract class AbstractDownloader implements Downloader {
    public final AlertManager alertManager;
    protected String name;
    private DownloaderLastStatus lastStatus = DownloaderLastStatus.UNKNOWN;
    private TranslationComponent statusMessage;
    private int failedLoginAttempts = 0;
    private long nextLoginTry = 0L;

    public AbstractDownloader(String name, AlertManager alertManager) {
        this.name = name;
        this.alertManager = alertManager;
    }

    @Override
    public DownloaderLoginResult login() {
        if(isPaused()){
            lastStatus = DownloaderLastStatus.PAUSED;
            statusMessage = new TranslationComponent(Lang.STATUS_TEXT_PAUSED);
            return new DownloaderLoginResult(DownloaderLoginResult.Status.PAUSED, new TranslationComponent(Lang.DOWNLOADER_PAUSED));
        }
        if (nextLoginTry >= System.currentTimeMillis()) {
            alertManager.publishAlert(true,
                    AlertLevel.WARN,
                    "downloader-too-many-failed-attempt-" + getName(),
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
            if (result.getStatus() == DownloaderLoginResult.Status.INCORRECT_CREDENTIAL)
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

    @Override
    public void relaunchTorrentIfNeeded(Collection<Torrent> torrents) {

    }

    @Override
    public void relaunchTorrentIfNeededByTorrentWrapper(Collection<TorrentWrapper> torrents) {

    }

    public abstract DownloaderLoginResult login0() throws Exception;

    @Override
    public DownloaderLastStatus getLastStatus() {
        return lastStatus;
    }

    @Override
    public void setLastStatus(DownloaderLastStatus lastStatus, TranslationComponent statusMessage) {
        this.lastStatus = lastStatus;
        this.statusMessage = statusMessage;
    }

    @Override
    public TranslationComponent getLastStatusMessage() {
        return statusMessage;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public DownloaderStatistics getStatistics() {
        return new DownloaderStatistics(0, 0);
    }

    @Override
    public List<DownloaderFeatureFlag> getFeatureFlags() {
        return List.of(DownloaderFeatureFlag.UNBAN_IP);
    }

    @Override
    public int getMaxConcurrentPeerRequestSlots() {
        return 16;
    }
}
