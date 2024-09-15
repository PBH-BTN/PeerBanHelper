package com.ghostchu.peerbanhelper.telemetry;

import java.util.Map;

public interface ErrorReporter {
    /**
     * 注册此 Thread 由 Telemetry 工具捕获未处理的异常
     */
    void handleUncaughtErrors();

    void error(Throwable throwable, Map<String, Object> custom);

    void warning(Throwable throwable, Map<String, Object> custom);

    void error(String description, Map<String, Object> custom);

    void warning(String description, Map<String, Object> custom);

    default void error(Throwable throwable) {
        error(throwable,null);
    }

    default void warning(Throwable throwable) {
        warning(throwable,null);
    }

}
