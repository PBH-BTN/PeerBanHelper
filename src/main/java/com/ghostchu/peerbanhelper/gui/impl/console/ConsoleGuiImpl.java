package com.ghostchu.peerbanhelper.gui.impl.console;

import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ConsoleGuiImpl implements GuiImpl {
    private AtomicBoolean wakeLock = new AtomicBoolean(false);

    @Override
    public void showConfigurationSetupDialog() {
        log.info(Lang.CONFIG_PEERBANHELPER);
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
    }

    @Override
    public void setup() {
        // do nothing
    }

    @Override
    public void createMainWindow() {

    }

    @SneakyThrows
    @Override
    public void sync() {
        while (!wakeLock.get()) {
            synchronized (wakeLock) {
                wakeLock.wait(1000 * 5);
            }
        }
    }

    @Override
    public void close() {
        wakeLock.set(true);
        wakeLock.notifyAll();
    }
}
