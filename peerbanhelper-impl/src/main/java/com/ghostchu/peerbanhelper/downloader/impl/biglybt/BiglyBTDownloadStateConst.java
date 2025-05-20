package com.ghostchu.peerbanhelper.downloader.impl.biglybt;

public final class BiglyBTDownloadStateConst {
    /**
     * waiting to be told to start preparing
     */
    public static final int ST_WAITING = 1;
    /**
     * getting files ready (allocating/checking)
     */
    public static final int ST_PREPARING = 2;
    /**
     * ready to be started if required
     */
    public static final int ST_READY = 3;
    /**
     * downloading
     */
    public static final int ST_DOWNLOADING = 4;
    /**
     * seeding
     */
    public static final int ST_SEEDING = 5;
    /**
     * stopping
     */
    public static final int ST_STOPPING = 6;
    /**
     * stopped, do not auto-start!
     */
    public static final int ST_STOPPED = 7;
    /**
     * failed
     */
    public static final int ST_ERROR = 8;
    /**
     * stopped, but ready for auto-starting
     */
    public static final int ST_QUEUED = 9;
}
