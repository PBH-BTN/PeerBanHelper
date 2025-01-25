package com.ghostchu.peerbanhelper.gui.impl.console;

import com.ghostchu.peerbanhelper.gui.ProgressDialog;
import com.ghostchu.peerbanhelper.gui.TaskbarControl;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

@Slf4j
public class ConsoleGuiImpl implements GuiImpl {
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    public ConsoleGuiImpl(String[] args) {
    }

    @Override
    public void setup() {
        System.setProperty("java.awt.headless", "true");
    }

    @Override
    public void createMainWindow() {

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
        if (level.equals(Level.WARNING)) {
            log.warn("{}: {}", title, description);
        }
        if (level.equals(Level.SEVERE)) {
            log.error("{}: {}", title, description);
        }
    }

    @Override
    public void createDialog(Level level, String title, String description) {
        if (level.equals(Level.INFO)) {
            log.info("{}: {}", title, description);
        }
        if (level.equals(Level.WARNING)) {
            log.warn("{}: {}", title, description);
        }
        if (level.equals(Level.SEVERE)) {
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
            public void updateProgress(Window window, Taskbar.State state, float progress) {

            }

            @Override
            public void requestUserAttention(Window window, boolean critical) {

            }
        };
    }


}
