package com.ghostchu.peerbanhelper.gui.impl.console;

import com.ghostchu.peerbanhelper.gui.ProgressDialog;
import com.ghostchu.peerbanhelper.gui.TaskbarControl;
import com.ghostchu.peerbanhelper.gui.TaskbarState;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.util.logger.JListAppender;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;

import java.util.concurrent.CountDownLatch;

@Slf4j
public class ConsoleGuiImpl implements GuiImpl {
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    public ConsoleGuiImpl(String[] args) {
    }

    @Override
    public boolean isDarkMode() {
        return false;
    }

    @Override
    public void setup() {
        System.setProperty("java.awt.headless", "true");
    }

    @Override
    public void createMainWindow() {
        JListAppender.allowWriteLogEntryDeque.set(false);
        JListAppender.logEntryDeque.clear();
    }

    @SneakyThrows
    @Override
    public void sync() {
        countDownLatch.await();
    }

    @Override
    public void close() {
        countDownLatch.countDown();
    }

    @Override
    public void createNotification(Level level, String title, String description) {
        if (level.equals(Level.INFO)) {
            log.info("{}: {}", title, description);
        }
        if (level.equals(Level.WARN)) {
            log.warn("{}: {}", title, description);
        }
        if (level.equals(Level.ERROR)) {
            log.error("{}: {}", title, description);
        }
    }

    @Override
    public void createYesNoDialog(Level level, String title, String description, @Nullable Runnable yesEvent, @Nullable Runnable noEvent) {

    }

    @Override
    public void openUrlInBrowser(String url) {

    }

    @Override
    public void createDialog(Level level, String title, String description, Runnable clickEvent) {
        if (level.equals(Level.INFO)) {
            log.info("{}: {}", title, description);
        }
        if (level.equals(Level.WARN)) {
            log.warn("{}: {}", title, description);
        }
        if (level.equals(Level.ERROR)) {
            log.error("{}: {}", title, description);
        }
    }

    @Override
    public ProgressDialog createProgressDialog(String title, String description, String buttonText, Runnable buttonEvent, boolean allowCancel) {
        return new ConsoleProgressDialog(title, description, buttonText, buttonEvent, allowCancel);
    }

    @Override
    public TaskbarControl taskbarControl() {
        return new TaskbarControl() {
            @Override
            public void updateProgress(Object window, TaskbarState state, float progress) {

            }

            @Override
            public void requestUserAttention(Object window, boolean critical) {

            }
        };
    }

    @Override
    public boolean isGuiAvailable() {
        return false;
    }

    @Override
    public String getName() {
        return "CONSOLE";
    }

    @Override
    public boolean supportInteractive() {
        return false;
    }


}
