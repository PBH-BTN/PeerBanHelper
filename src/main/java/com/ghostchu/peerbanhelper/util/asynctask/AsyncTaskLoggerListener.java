package com.ghostchu.peerbanhelper.util.asynctask;

public interface AsyncTaskLoggerListener {
    void onLog(AsyncTask task, String log);

    void onTaskClose(AsyncTask task);
}
