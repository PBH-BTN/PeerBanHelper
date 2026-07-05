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
import java.awt.image.BufferedImage;
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
    private GlowTextPane textPane;
    private List<Object> contentItems;
    private int currentIndex = 0;
    private Timer printTimer;
    private Timer cursorTimer;
    private boolean cursorVisible = false;
    private int delay = 100;
    private static final int PRINT_TICK_MS = 16;
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

    private record ColorCommand(Color fg, Color bg, Color glow, int glowRadius, float glowIntensity, float glowBoost, boolean glowSpecified) {
    }

    private static class ClearCommand {
    }

    // 新增命令类
    private static class WindowMaximizedCommand {
    }

    private static class GlowTextPane extends JTextPane {
        private Color glowColor = new Color(255, 107, 143);
        private int glowRadius = 14;
        private float glowIntensity = 0.65f;
        private float glowBoost = 1.8f;

        private void setGlow(Color color, int radius, float intensity, float boost) {
            this.glowColor = color;
            this.glowRadius = Math.max(0, radius);
            this.glowIntensity = Math.max(0f, Math.min(1f, intensity));
            this.glowBoost = Math.max(0.5f, Math.min(4f, boost));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (glowRadius <= 0 || glowIntensity <= 0f || getWidth() <= 0 || getHeight() <= 0) {
                super.paintComponent(g);
                return;
            }
            Rectangle clip = g.getClipBounds();
            if (clip == null) {
                clip = new Rectangle(0, 0, getWidth(), getHeight());
            }
            int pad = glowRadius * 2 + 2;
            Rectangle effectBounds = new Rectangle(
                    Math.max(0, clip.x - pad),
                    Math.max(0, clip.y - pad),
                    Math.min(getWidth() - Math.max(0, clip.x - pad), clip.width + pad * 2),
                    Math.min(getHeight() - Math.max(0, clip.y - pad), clip.height + pad * 2)
            );
            if (effectBounds.width <= 0 || effectBounds.height <= 0) {
                super.paintComponent(g);
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setClip(clip);
            g2.setColor(getBackground());
            g2.fillRect(clip.x, clip.y, clip.width, clip.height);

            BufferedImage textLayer = new BufferedImage(effectBounds.width, effectBounds.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D textGraphics = textLayer.createGraphics();
            textGraphics.setClip(0, 0, effectBounds.width, effectBounds.height);
            textGraphics.translate(-effectBounds.x, -effectBounds.y);
            renderTextLayer(textGraphics);
            textGraphics.dispose();

            int[] glowAlpha = createGlowAlpha(textLayer, getBackground(), glowIntensity, glowBoost);
            int[] blurredGlowAlpha = boxBlurAlpha(glowAlpha, effectBounds.width, effectBounds.height, glowRadius);
            BufferedImage blurredGlow = composeGlowImage(blurredGlowAlpha, effectBounds.width, effectBounds.height, glowColor);
            g2.drawImage(blurredGlow, effectBounds.x, effectBounds.y, null);
            renderTextLayer(g2);
            g2.dispose();
        }

        private void renderTextLayer(Graphics2D graphics) {
            boolean oldOpaque = isOpaque();
            setOpaque(false);
            try {
                super.paintComponent(graphics);
            } finally {
                setOpaque(oldOpaque);
            }
        }

        private int[] createGlowAlpha(BufferedImage source, Color background, float intensity, float boost) {
            int width = source.getWidth();
            int height = source.getHeight();
            int[] src = source.getRGB(0, 0, width, height, null, 0, width);
            int[] out = new int[src.length];
            int bgR = background.getRed();
            int bgG = background.getGreen();
            int bgB = background.getBlue();
            for (int i = 0; i < src.length; i++) {
                int pixel = src[i];
                int alpha = (pixel >>> 24) & 0xFF;
                if (alpha <= 0) {
                    continue;
                }
                int r = (pixel >>> 16) & 0xFF;
                int g = (pixel >>> 8) & 0xFF;
                int b = pixel & 0xFF;
                int coverage = Math.max(Math.abs(r - bgR), Math.max(Math.abs(g - bgG), Math.abs(b - bgB)));
                if (coverage <= 1) {
                    continue;
                }
                int glowAlpha = Math.round((coverage / 255f) * alpha * intensity * boost);
                out[i] = Math.min(255, glowAlpha);
            }
            return out;
        }

        private int[] boxBlurAlpha(int[] sourceAlpha, int width, int height, int radius) {
            if (radius <= 0) {
                return sourceAlpha;
            }
            int[] tmp = new int[sourceAlpha.length];
            int[] out = new int[sourceAlpha.length];
            boxBlurHorizontalAlpha(sourceAlpha, tmp, width, height, radius);
            boxBlurVerticalAlpha(tmp, out, width, height, radius);
            return out;
        }

        private BufferedImage composeGlowImage(int[] alpha, int width, int height, Color glow) {
            int[] out = new int[alpha.length];
            int rgb = (glow.getRed() << 16) | (glow.getGreen() << 8) | glow.getBlue();
            for (int i = 0; i < alpha.length; i++) {
                if (alpha[i] <= 0) {
                    continue;
                }
                out[i] = (alpha[i] << 24) | rgb;
            }
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, out, 0, width);
            return image;
        }

        private void boxBlurHorizontalAlpha(int[] src, int[] dst, int width, int height, int radius) {
            int kernelSize = radius * 2 + 1;
            for (int y = 0; y < height; y++) {
                int row = y * width;
                int sum = 0;
                for (int i = -radius; i <= radius; i++) {
                    int xi = Math.max(0, Math.min(width - 1, i));
                    sum += src[row + xi];
                }
                for (int x = 0; x < width; x++) {
                    dst[row + x] = sum / kernelSize;
                    int removeX = Math.max(0, Math.min(width - 1, x - radius));
                    int addX = Math.max(0, Math.min(width - 1, x + radius + 1));
                    sum += src[row + addX] - src[row + removeX];
                }
            }
        }

        private void boxBlurVerticalAlpha(int[] src, int[] dst, int width, int height, int radius) {
            int kernelSize = radius * 2 + 1;
            for (int x = 0; x < width; x++) {
                int sum = 0;
                for (int i = -radius; i <= radius; i++) {
                    int yi = Math.max(0, Math.min(height - 1, i));
                    sum += src[yi * width + x];
                }
                for (int y = 0; y < height; y++) {
                    dst[y * width + x] = sum / kernelSize;
                    int removeY = Math.max(0, Math.min(height - 1, y - radius));
                    int addY = Math.max(0, Math.min(height - 1, y + radius + 1));
                    sum += src[addY * width + x] - src[removeY * width + x];
                }
            }
        }
    }

    static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AboutWindow(Map.of(
                "{version}", "1.0.0",
                "{username}", System.getProperty("user.name")
        )));
    }
}