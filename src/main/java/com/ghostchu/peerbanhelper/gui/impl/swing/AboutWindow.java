package com.ghostchu.peerbanhelper.gui.impl.swing;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.MIDIPlayer;
import lombok.Cleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.midi.MidiUnavailableException;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private Color fgColor = new Color(238, 205, 86);
    private Color bgColor = Color.BLACK;

    // 状态跟踪变量
    private String currentString = "";
    private int charIndex = 0;
    private boolean processingString = false;
    private boolean cursorLock = false;
    private final MIDIPlayer midiPlayer = new MIDIPlayer(
            Main.class.getResourceAsStream("/assets/midi/ABOUT-MiSide-MusicMenu.mid"),
            Main.class.getResourceAsStream("/assets/midi/ABOUT-MiSide-MusicMenu-Update.mid"));

    public AboutWindow(Map<String, String> replaces) {
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
                } else if (line.equals("[clear]")) {
                    contentItems.add(new ClearCommand());
                } else if (line.equals("[window_maximized]")) { // 新增命令检测
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
        // 主打印定时器
        printTimer = new Timer(delay, e -> processContent());

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
            processingString = true;
        }

        cursorLock = true;
        try {
            StyledDocument doc = textPane.getStyledDocument();

            // 移除现有光标
            if (cursorVisible) {
                removeLastCursor(doc);
            }

            // 添加新字符
            if (charIndex < currentString.length()) {
                SimpleAttributeSet attr = new SimpleAttributeSet();
                StyleConstants.setForeground(attr, fgColor);
                doc.insertString(doc.getLength(),
                        String.valueOf(currentString.charAt(charIndex)), attr);
                charIndex++;
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
        } finally {
            cursorLock = false;
        }
    }

    private void processCommand(Object item) {
        try {
            if (item instanceof SpeedCommand) {
                SpeedCommand cmd = (SpeedCommand) item;
                delay = cmd.speed;
                printTimer.setDelay(delay);
            } else if (item instanceof ColorCommand) {
                ColorCommand cmd = (ColorCommand) item;
                fgColor = cmd.fg;
                bgColor = cmd.bg;
                textPane.setForeground(fgColor);
                textPane.setBackground(bgColor);
            } else if (item instanceof ClearCommand) {
                textPane.setText("");
            } else if (item instanceof WindowMaximizedCommand) { // 处理最大化命令
                SwingUtilities.invokeLater(() -> frame.setExtendedState(Frame.MAXIMIZED_BOTH));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
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
    private static class SpeedCommand {
        final int speed;

        SpeedCommand(int speed) {
            this.speed = speed;
        }
    }

    private static class ColorCommand {
        final Color fg, bg;

        ColorCommand(Color fg, Color bg) {
            this.fg = fg;
            this.bg = bg;
        }
    }

    private static class ClearCommand {
    }

    // 新增命令类
    private static class WindowMaximizedCommand {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new AboutWindow(Map.of(
                    "{version}", "1.0.0",
                    "{username}", System.getProperty("user.name")
            ));
        });
    }
}