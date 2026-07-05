package com.ghostchu.peerbanhelper.gui.impl.swing.toolwindow;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.MIDIPlayer;
import io.sentry.Sentry;
import lombok.Cleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.text.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AboutWindow {
    private static final Logger log = LoggerFactory.getLogger(AboutWindow.class);
    private JFrame frame;
    private final Map<String, String> replaces;
    private JTextPane textPane;
    private List<Object> contentItems;
    private int currentIndex = 0;
    private Timer printTimer;
    private Timer cursorTimer;
    private boolean cursorVisible = false;
    private int delay = 100;
    private static final int PRINT_TICK_MS = 16;
    private Color fgColor = new Color(238, 205, 86);
    private Color bgColor = Color.BLACK;

    // 状态跟踪变量
    private String currentString = "";
    private int charIndex = 0;
    private long nextCharTimeNanos = 0L;
    private boolean processingString = false;
    private boolean cursorLock = false;
    private final MIDIPlayer midiPlayer;

    public AboutWindow(Map<String, String> replaces) {
        List<String> playList = new ArrayList<>();
        playList.add("/assets/midi/Remember.mid");
        playList.add("/assets/midi/Starry_Sea.mid");
        playList.add("/assets/midi/A_Symphony_of_Moments.mid");
        Collections.shuffle(playList, new Random());
        playList.add("/assets/midi/Reply.mid"); // Make sure it plays at last one
        midiPlayer = new MIDIPlayer(playList.stream().map(Main.class::getResourceAsStream).toArray(InputStream[]::new));
        initializeUI();
        this.replaces = replaces;
        String content = "Missing no";
        try {
            @Cleanup var is = Main.class.getResourceAsStream("/assets/credit.txt");
            if (is != null) {
                content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.error("Failed to load credit.txt", e);
        }
        loadContent(content);
        setupTimers();
        try {
            playMidi();
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    private void playMidi() throws MidiUnavailableException {
        midiPlayer.play();
    }


    private void initializeUI() {
        this.frame = new JFrame("[>_] PeerBanHelper Repair Terminal (Mode: Credit)") {
            @Override
            public void dispose() {
                super.dispose();
                if (midiPlayer != null) {
                    midiPlayer.close();
                }
            }
        };
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1000, 500);

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setBackground(bgColor);
        textPane.setForeground(fgColor);
        textPane.setFont(new Font("Consolas", Font.PLAIN, 14));
        textPane.setCaret(new DefaultCaret() {
            @Override
            public boolean isVisible() {
                return false;
            }
        });
        frame.add(new JScrollPane(textPane));
        frame.setVisible(true);
    }

    private void loadContent(String content) {
        contentItems = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("[speed:")) {
                    handleSpeedCommand(line);
                } else if (line.startsWith("[f:") && line.contains(",b:")) {
                    handleColorCommand(line);
                } else if ("[clear]".equals(line)) {
                    contentItems.add(new ClearCommand());
                } else if ("[window_maximized]".equals(line)) { // 新增命令检测
                    contentItems.add(new WindowMaximizedCommand());
                } else {
                    for (Map.Entry<String, String> entry : replaces.entrySet()) {
                        line = line.replace(entry.getKey(), entry.getValue());
                    }
                    contentItems.add(line);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Sentry.captureException(e);
        }
    }

    private void handleSpeedCommand(String line) {
        Matcher m = Pattern.compile("\\[speed:(\\d+)\\]").matcher(line);
        if (m.find()) {
            contentItems.add(new SpeedCommand(Integer.parseInt(m.group(1))));
        }
    }

    private void handleColorCommand(String line) {
        Matcher m = Pattern.compile("\\[f:(#\\w{6}),b:(#\\w{6})\\]").matcher(line);
        if (m.find()) {
            contentItems.add(new ColorCommand(
                    Color.decode(m.group(1)),
                    Color.decode(m.group(2))
            ));
        }
    }

    private void cleanupTimers() {
        if (printTimer != null) {
            printTimer.stop();
            printTimer = null;
        }
        if (cursorTimer != null) {
            cursorTimer.stop();
            cursorTimer = null;
        }
    }

    private void setupTimers() {
        cleanupTimers();
        // 固定 tick + 真实时间补偿，避免输出速度受 EDT 卡顿影响
        printTimer = new Timer(PRINT_TICK_MS, e -> processContent());

        // 光标闪烁定时器（与打印互斥）
        cursorTimer = new Timer(500, evt -> {
            if (cursorLock) return;

            try {
                StyledDocument doc = textPane.getStyledDocument();
                int len = doc.getLength();

                if (cursorVisible && len > 0) {
                    removeLastCursor(doc);
                } else if (!processingString) {
                    addNewCursor(doc);
                }
            } catch (BadLocationException ex) {
                ex.printStackTrace();
                Sentry.captureException(ex);
            }
        });

        printTimer.start();
        cursorTimer.start();
    }

    private void processContent() {
        if (currentIndex >= contentItems.size()) {
            printTimer.stop();
            return;
        }

        Object item = contentItems.get(currentIndex);

        if (item instanceof String) {
            processString((String) item);
        } else {
            processCommand(item);
            currentIndex++;
        }
    }

    private void processString(String text) {
        if (!processingString) {
            currentString = text;
            charIndex = 0;
            nextCharTimeNanos = System.nanoTime();
            processingString = true;
        }

        cursorLock = true;
        try {
            StyledDocument doc = textPane.getStyledDocument();

            // 移除现有光标
            if (cursorVisible) {
                removeLastCursor(doc);
            }

            // 按真实时间补写字符，UI 卡顿后会自动追平输出进度
            int charsToWrite = 0;
            if (charIndex < currentString.length()) {
                long now = System.nanoTime();
                if (delay <= 0) {
                    charsToWrite = currentString.length() - charIndex;
                } else if (now >= nextCharTimeNanos) {
                    long delayNanos = delay * 1_000_000L;
                    long due = ((now - nextCharTimeNanos) / delayNanos) + 1;
                    int remain = currentString.length() - charIndex;
                    charsToWrite = (int) Math.min(due, remain);
                    nextCharTimeNanos += charsToWrite * delayNanos;
                }
            }

            if (charsToWrite > 0) {
                SimpleAttributeSet attr = new SimpleAttributeSet();
                StyleConstants.setForeground(attr, fgColor);
                doc.insertString(doc.getLength(),
                        currentString.substring(charIndex, charIndex + charsToWrite), attr);
                charIndex += charsToWrite;
            }

            // 添加新光标
            if (charIndex < currentString.length()) {
                addNewCursor(doc);
            } else {
                doc.insertString(doc.getLength(), "\n", null);
                processingString = false;
                currentIndex++;
            }

            textPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ex) {
            ex.printStackTrace();
            Sentry.captureException(ex);
        } finally {
            cursorLock = false;
        }
    }

    private void processCommand(Object item) {
        try {
            if (item instanceof SpeedCommand(int speed)) {
                delay = speed;
            } else if (item instanceof ColorCommand(Color fg, Color bg)) {
                fgColor = fg;
                bgColor = bg;
                textPane.setForeground(fgColor);
                textPane.setBackground(bgColor);
            } else if (item instanceof ClearCommand) {
                textPane.setText("");
            } else if (item instanceof WindowMaximizedCommand) { // 处理最大化命令
                SwingUtilities.invokeLater(() -> frame.setExtendedState(Frame.MAXIMIZED_BOTH));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Sentry.captureException(ex);
        }
    }

    private synchronized void removeLastCursor(StyledDocument doc) throws BadLocationException {
        int len = doc.getLength();
        if (len > 0 && cursorVisible) {
            doc.remove(len - 1, 1);
            cursorVisible = false;
        }
    }

    private synchronized void addNewCursor(StyledDocument doc) throws BadLocationException {
        if (!cursorVisible) {
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setForeground(attr, fgColor);
            doc.insertString(doc.getLength(), "▉", attr);
            cursorVisible = true;
        }
    }

    // 命令类定义
    private record SpeedCommand(int speed) {
    }

    private record ColorCommand(Color fg, Color bg) {
    }

    private static class ClearCommand {
    }

    // 新增命令类
    private static class WindowMaximizedCommand {
    }

    static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AboutWindow(Map.of(
                "{version}", "1.0.0",
                "{username}", System.getProperty("user.name")
        )));
    }
}