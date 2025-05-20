package com.ghostchu.peerbanhelper.downloader.impl.biglybt;

public final class BiglyBTDownloadManagerStateConst {
    public static final int STATE_START_OF_DAY = -1;   // should never actually see this one

    public static final int STATE_WAITING = 0;
    public static final int STATE_INITIALIZING = 5;
    public static final int STATE_INITIALIZED = 10;

    public static final int STATE_ALLOCATING = 20;
    public static final int STATE_CHECKING = 30;

    // Ready: Resources allocated

    public static final int STATE_READY = 40;
    public static final int STATE_DOWNLOADING = 50;
    public static final int STATE_FINISHING = 55;
    public static final int STATE_SEEDING = 60;
    public static final int STATE_STOPPING = 65;

    // Stopped: can't be automatically started

    public static final int STATE_STOPPED = 70;
    public static final int STATE_CLOSED = 71;    // download never *has* this state, just used to inform
    // when stopping for az closedown

    // Queued: Same as stopped, except can be automatically started

    public static final int STATE_QUEUED = 75;

    public static final int STATE_ERROR = 100;

}
