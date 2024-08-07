package com.ghostchu.peerbanhelper.gui.impl.swing;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.gui.impl.GuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.log4j2.SwingLoggerAppender;
import com.jthemedetecor.OsThemeDetector;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.Arrays;
import java.util.logging.Level;

import static javax.swing.SwingUtilities.invokeLater;

@Getter
@Slf4j
public class SwingGuiImpl extends ConsoleGuiImpl implements GuiImpl {
    @Getter
    private final boolean silentStart;
    private MainWindow mainWindow;

    public SwingGuiImpl(String[] args) {
        super(args);
        this.silentStart = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("silent"));
    }


    @Override
    public void setup() {
        super.setup();
        //FlatIntelliJLaf.setup();
        Main.getEventBus().register(this);
        OsThemeDetector detector = OsThemeDetector.getDetector();
        detector.registerListener(this::updateTheme);
        updateTheme(detector.isDark());
    }

    private void updateTheme(Boolean isDark) {
        if (isDark) {
            FlatDarculaLaf.setup();
        } else {
            FlatIntelliJLaf.setup();
        }
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
        SwingLoggerAppender.registerListener(event -> {
            try {
                invokeLater(() -> {
                    JTextArea textArea = mainWindow.getLoggerTextArea();
                    try {
                        textArea.append(event.message() + "\n");
                        int linesToCut = (textArea.getLineCount() - event.maxLines()) + (event.maxLines() / 5);
                        linesToCut = Math.min(linesToCut, textArea.getLineCount());
                        if (linesToCut > 0) {
                            int posOfLastLineToTrunk = textArea.getLineEndOffset(linesToCut - 1);
                            textArea.replaceRange("", 0, posOfLastLineToTrunk);
                        }
                        textArea.setCaretPosition(textArea.getDocument().getLength());
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
            } catch (IllegalStateException exception) {
                exception.printStackTrace();
            }
        });
    }


    @Override
    public void sync() {
        mainWindow.sync();
        super.sync();
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
    }

    @Override
    public void createDialog(Level level, String title, String description) {
        int msgType = JOptionPane.PLAIN_MESSAGE;
        if (level == Level.INFO) {
            msgType = JOptionPane.INFORMATION_MESSAGE;
        }
        if (level == Level.WARNING) {
            msgType = JOptionPane.WARNING_MESSAGE;
        }
        if (level == Level.SEVERE) {
            msgType = JOptionPane.ERROR_MESSAGE;
        }
        JOptionPane.showMessageDialog(null, description, title, msgType);
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
