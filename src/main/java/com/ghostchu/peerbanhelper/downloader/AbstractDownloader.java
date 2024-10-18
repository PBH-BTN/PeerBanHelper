package com.ghostchu.peerbanhelper.downloader;

import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.wrapper.TorrentWrapper;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public abstract class AbstractDownloader implements Downloader {
    protected String name;
    private DownloaderLastStatus lastStatus = DownloaderLastStatus.UNKNOWN;
    private TranslationComponent statusMessage;
    private int failedLoginAttempts = 0;
    private long nextLoginTry = 0L;

    public AbstractDownloader(String name) {
        this.name = name;
    }

    @Override
    public DownloaderLoginResult login() {
        if (nextLoginTry >= System.currentTimeMillis()) {
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
    public List<DownloaderFeatureFlag> getDownloaderFeatureFlags() {
        return Collections.emptyList();
    }
}
