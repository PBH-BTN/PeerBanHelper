package com.ghostchu.peerbanhelper.gui.impl.swing.toolwindow;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.gui.impl.swing.components.GlowTextPane;
import com.ghostchu.peerbanhelper.util.MIDIPlayer;
import io.sentry.Sentry;
import lombok.Cleanup;
import okhttp3.Request;
import okhttp3.Response;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AboutWindow {
    private static final Logger log = LoggerFactory.getLogger(AboutWindow.class);
    private JFrame frame;
    private final Map<String, String> replaces;
    private GlowTextPane textPane;
    private List<Object> contentItems;
    private int currentIndex = 0;
    private Timer printTimer;
    private Timer cursorTimer;
    private boolean cursorVisible = false;
    private int delay = 100;
    private static final int PRINT_TICK_MS = 16;
    private int fontSize = 14;
    private Color fgColor = new Color(238, 205, 86);
    private Color bgColor = Color.BLACK;
    private Color glowColor = new Color(255, 107, 143);
    private int glowRadius = 14;
    private float glowIntensity = 0.65f;
    private float glowBoost = 1.8f;

    // 状态跟踪变量
    private String currentString = "";
    private int charIndex = 0;
    private long nextCharTimeNanos = 0L;
    private boolean processingString = false;
    private boolean cursorLock = false;
    private final MIDIPlayer midiPlayer;
    private final AtomicReference<String> sponsors = new AtomicReference<>("Unable to load sponsors");

    public AboutWindow(Map<String, String> replaces) {
        CompletableFuture.runAsync(this::loadSponsorList);
        List<String> playList = new ArrayList<>();
        playList.add("/assets/midi/Remember.mid");
        playList.add("/assets/midi/Starry_Sea.mid");
        playList.add("/assets/midi/A_Symphony_of_Moments.mid");
        Collections.shuffle(playList, new Random());
        playList.add(1, "/assets/midi/Reply.mid"); // Make sure it plays at second one
        midiPlayer = new MIDIPlayer(playList.stream().map(Main.class::getResourceAsStream).toArray(InputStream[]::new));

        // https://pbhbtn-afdian-sponors.ghostchu.workers.dev/raw/PBH-BTN/afdian-sponsors-list/master/list.txt

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
    }

    private void loadSponsorList() {
        var server = Main.getServer();
        if (server == null) {
            return;
        }
        Request request = new Request.Builder()
                .url("https://ghp.pbh-btn.com/raw/PBH-BTN/sponsors-list/master/list.txt")
                .get()
                .header("Content-Type", "text/plain")
                .build();

        try (Response response = server.getHttpUtil().newBuilder().build().newCall(request).execute()) {
            if (response.isSuccessful()) {
                StringBuilder builder = new StringBuilder("    ");
                int inline = 0;
                for (String s : response.body().string().split("\n")) {
                    inline++;
                    builder.append(s);
                    if (inline >= 5) {
                        builder.append("\n    ");
                        inline = 0;
                    } else {
                        builder.append(", ");
                    }
                }
                this.sponsors.set(builder.toString());
            }
        } catch (Exception e) {
            log.debug("Unable to load sponsors list", e);
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

        textPane = new GlowTextPane();
        textPane.setEditable(false);
        textPane.setBackground(bgColor);
        textPane.setForeground(fgColor);
        textPane.setGlow(glowColor, glowRadius, glowIntensity, glowBoost);
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
        contentItems = Collections.synchronizedList(new ArrayList<>());
        try (BufferedReader br = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("[speed:")) {
                    handleSpeedCommand(line);
                } else if (line.startsWith("[size:")) {
                    handleSizeCommand(line);
                } else if (line.startsWith("[f:") && line.contains(",b:")) {
                    handleColorCommand(line);
                } else if ("[sponsors]".equals(line)) {
                    contentItems.add(new SponsorsCommand());
                } else if ("[clear]".equals(line)) {
                    contentItems.add(new ClearCommand());
                } else if ("[window_maximized]".equals(line)) { // 新增命令检测
                    contentItems.add(new WindowMaximizedCommand());
                } else if ("[play_midi]".equals(line)) {
                    contentItems.add(new PlayMidiCommand());
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
        if (!line.startsWith("[") || !line.endsWith("]")) {
            return;
        }
        String body = line.substring(1, line.length() - 1);
        String[] tokens = body.split(",");
        Map<String, String> args = new HashMap<>();
        for (String token : tokens) {
            String[] kv = token.split(":", 2);
            if (kv.length == 2) {
                args.put(kv[0].trim(), kv[1].trim());
            }
        }

        if (!args.containsKey("f") || !args.containsKey("b")) {
            return;
        }
        try {
            Color fg = Color.decode(args.get("f"));
            Color bg = Color.decode(args.get("b"));
            boolean glowSpecified = args.containsKey("g") || args.containsKey("gr") || args.containsKey("gi") || args.containsKey("gb");
            Color glow = args.containsKey("g") ? Color.decode(args.get("g")) : glowColor;
            int radius = args.containsKey("gr") ? Math.max(0, Integer.parseInt(args.get("gr"))) : glowRadius;
            float intensity = args.containsKey("gi")
                    ? clamp(Float.parseFloat(args.get("gi")), 0f, 1f)
                    : glowIntensity;
            float boost = args.containsKey("gb")
                    ? clamp(Float.parseFloat(args.get("gb")), 0.5f, 4f)
                    : glowBoost;
            contentItems.add(new ColorCommand(fg, bg, glow, radius, intensity, boost, glowSpecified));
        } catch (IllegalArgumentException ignored) {
            log.warn("Invalid color command: {}", line);
        }
    }

    private void handleSizeCommand(String line) {
        Matcher m = Pattern.compile("\\[size:(\\d+)\\]").matcher(line);
        if (m.find()) {
            int size = Math.clamp(Integer.parseInt(m.group(1)), 1, 256);
            contentItems.add(new SizeCommand(size));
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
        } else if (item instanceof SponsorsCommand) {
            processSponsorsCommand();
            currentIndex++;
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

            if (charsToWrite <= 0 && charIndex < currentString.length()) {
                return;
            }

            // 只有真正输出新字符时才更新光标，避免高频无效重绘
            if (cursorVisible) {
                removeLastCursor(doc);
            }

            if (charsToWrite > 0) {
                SimpleAttributeSet attr = new SimpleAttributeSet();
                StyleConstants.setForeground(attr, fgColor);
                StyleConstants.setFontSize(attr, fontSize);
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
            Rectangle visibleRect = textPane.getVisibleRect();
            if (visibleRect.width > 0 && visibleRect.height > 0) {
                textPane.repaint(visibleRect.x, visibleRect.y, visibleRect.width, visibleRect.height);
            } else {
                textPane.repaint();
            }
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
            } else if (item instanceof SizeCommand(int size)) {
                fontSize = size;
            } else if (item instanceof ColorCommand command) {
                fgColor = command.fg();
                bgColor = command.bg();
                textPane.setForeground(fgColor);
                textPane.setBackground(bgColor);
                if (command.glowSpecified()) {
                    glowColor = command.glow();
                    glowRadius = command.glowRadius();
                    glowIntensity = command.glowIntensity();
                    glowBoost = command.glowBoost();
                }
                textPane.setGlow(glowColor, glowRadius, glowIntensity, glowBoost);
            } else if (item instanceof ClearCommand) {
                textPane.setText("");
            } else if (item instanceof WindowMaximizedCommand) { // 处理最大化命令
                SwingUtilities.invokeLater(() -> frame.setExtendedState(Frame.MAXIMIZED_BOTH));
            } else if (item instanceof PlayMidiCommand) {
                try {
                    playMidi();
                } catch (Exception ignored) {
                    ignored.printStackTrace();
                }
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
            StyleConstants.setFontSize(attr, fontSize);
            doc.insertString(doc.getLength(), "▉", attr);
            cursorVisible = true;
        }
    }

    // 命令类定义
    private record SpeedCommand(int speed) {
    }

    private record SizeCommand(int size) {
    }

    private record ColorCommand(Color fg, Color bg, Color glow, int glowRadius, float glowIntensity, float glowBoost,
                                boolean glowSpecified) {
    }

    private static class SponsorsCommand {
    }

    private static class ClearCommand {
    }

    private static class PlayMidiCommand {
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

    private void processSponsorsCommand() {
        // 在命中 [sponsors] 的当下读取最新内容，避免提前固化
        String sponsorContent = sponsors.get();
        if (sponsorContent == null || sponsorContent.isBlank()) {
            return;
        }
        int insertAt = currentIndex + 1;
        String[] lines = sponsorContent.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (Map.Entry<String, String> entry : replaces.entrySet()) {
                line = line.replace(entry.getKey(), entry.getValue());
            }
            contentItems.add(insertAt + i, line);
        }
    }
}