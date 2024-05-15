package com.ghostchu.peerbanhelper.gui.impl.swing;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.window.MainWindow;
import com.ghostchu.peerbanhelper.text.Lang;
import com.jthemedetecor.OsThemeDetector;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.logging.Level;

@Getter
@Slf4j
public class SwingGuiImpl extends ConsoleGuiImpl implements GuiImpl {
    private MainWindow mainWindow;

    public SwingGuiImpl() {

    }

    @Override
    public void showConfigurationSetupDialog() {
        log.info(Lang.CONFIG_PEERBANHELPER);
        JOptionPane.showMessageDialog(null, Lang.CONFIG_PEERBANHELPER, "Dialog", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void setup() {
        super.setup();
        OsThemeDetector detector = OsThemeDetector.getDetector();
        onColorThemeChanged();
        detector.registerListener(isDark -> onColorThemeChanged());
    }

    @Override
    public void createMainWindow() {
        mainWindow = new MainWindow(this);
        initLoggerRedirection();
    }

    public boolean openWebpage(URI uri) {
        Desktop desktop = Desktop.getDesktop();
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(uri);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private void initLoggerRedirection() {
        SwingLoggerAppender.addLog4j2TextAreaAppender(mainWindow.getLoggerTextArea());
    }


    @Override
    public void sync() {
        mainWindow.sync();
        super.sync();
    }

    private void onColorThemeChanged() {
        OsThemeDetector detector = OsThemeDetector.getDetector();
        boolean isDarkThemeUsed = detector.isDark();
        if (isDarkThemeUsed) {
            setColorTheme(FlatDarculaLaf.class);
        } else {
            setColorTheme(FlatIntelliJLaf.class);
        }
    }

    public void setColorTheme(Class<?> clazz) {
        if (clazz.getName().equals(UIManager.getLookAndFeel().getClass().getName()))
            return;
        try {
            UIManager.setLookAndFeel(clazz.getName());
        } catch (Exception ex) {
            log.info("Failed to setup UI theme", ex);
        }
        // FlatLaf.updateUI();
    }

    @Override
    public void close() {
        // mainWindow.getWebviewManager().close();
    }

    @Override
    public void createNotification(Level level, String title, String description) {
        if (mainWindow.getTrayIcon() != null) {
            if (level.equals(Level.INFO)) {
                mainWindow.getTrayIcon().displayMessage(title, description, TrayIcon.MessageType.INFO);
            }
            if (level.equals(Level.WARNING)) {
                mainWindow.getTrayIcon().displayMessage(title, description, TrayIcon.MessageType.WARNING);
            }
            if (level.equals(Level.SEVERE)) {
                mainWindow.getTrayIcon().displayMessage(title, description, TrayIcon.MessageType.ERROR);
            }
        } else {
            super.createNotification(level, title, description);
        }
    }
}
