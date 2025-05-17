package com.ghostchu.peerbanhelper.gui.impl.swt;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.MIDIPlayer;
import lombok.Cleanup;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.midi.MidiUnavailableException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwtAboutWindow {
    private static final Logger log = LoggerFactory.getLogger(SwtAboutWindow.class);
    private Shell shell;
    private Display display;
    private final Map<String, String> replaces;
    private StyledText textPane;
    private List<Object> contentItems;
    private int currentIndex = 0;
    private boolean cursorVisible = false;
    private int delay = 100;
    private org.eclipse.swt.graphics.Color fgColor;
    private org.eclipse.swt.graphics.Color bgColor;

    // 状态跟踪变量
    private String currentString = "";
    private int charIndex = 0;
    private boolean processingString = false;
    private AtomicBoolean cursorLock = new AtomicBoolean(false);

    private final MIDIPlayer midiPlayer = new MIDIPlayer(
            Main.class.getResourceAsStream("/assets/midi/ABOUT-MiSide-MusicMenu.mid"),
            Main.class.getResourceAsStream("/assets/midi/ABOUT-MiSide-MusicMenu-Update.mid"));

    public SwtAboutWindow(Display display, Map<String, String> replaces) {
        this.display = display;
        this.replaces = replaces;
        initializeUI();
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
        this.shell = new Shell(display, SWT.SHELL_TRIM | SWT.APPLICATION_MODAL);
        shell.setText("[>_] PeerBanHelper Repair Terminal (Mode: Credit)");
        shell.setSize(1000, 500);

        // 创建布局
        GridLayout layout = new GridLayout();
        shell.setLayout(layout);

        // 创建并配置文本显示区域
        textPane = new StyledText(shell, SWT.MULTI | SWT.V_SCROLL | SWT.WRAP | SWT.READ_ONLY);
        textPane.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // 设置颜色
        fgColor = new Color(display, 238, 205, 86);
        bgColor = new Color(display, 0, 0, 0);
        textPane.setForeground(fgColor);
        textPane.setBackground(bgColor);

        // 设置字体
        FontData[] fontData = display.getSystemFont().getFontData();
        for (FontData fd : fontData) {
            fd.setName("Consolas");
            fd.setHeight(11);
            fd.setStyle(SWT.NORMAL);
        }
        Font customFont = new Font(display, fontData);
        textPane.setFont(customFont);

        // 设置关闭事件
        shell.addDisposeListener(e -> {
            if (midiPlayer != null) {
                midiPlayer.close();
            }
            if (customFont != null && !customFont.isDisposed()) {
                customFont.dispose();
            }
            if (fgColor != null && !fgColor.isDisposed()) {
                fgColor.dispose();
            }
            if (bgColor != null && !bgColor.isDisposed()) {
                bgColor.dispose();
            }
        });

        shell.open();
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
                } else if (line.equals("[window_maximized]")) {
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
                    m.group(1),
                    m.group(2)
            ));
        }
    }

    private void setupTimers() {
        // 主打印定时器
        display.timerExec(delay, new Runnable() {
            @Override
            public void run() {
                if (shell.isDisposed()) return;

                processContent();

                if (currentIndex < contentItems.size()) {
                    display.timerExec(delay, this);
                }
            }
        });

        // 光标闪烁定时器
        display.timerExec(500, new Runnable() {
            @Override
            public void run() {
                if (shell.isDisposed()) return;

                if (!cursorLock.get()) {
                    try {
                        if (cursorVisible && textPane.getCharCount() > 0) {
                            removeLastCursor();
                        } else if (!processingString) {
                            addNewCursor();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

                display.timerExec(500, this);
            }
        });
    }

    private void processContent() {
        if (currentIndex >= contentItems.size()) {
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

        cursorLock.set(true);
        try {
            // 移除现有光标
            if (cursorVisible) {
                removeLastCursor();
            }

            // 添加新字符
            if (charIndex < currentString.length()) {
                int length = textPane.getCharCount();
                textPane.append(String.valueOf(currentString.charAt(charIndex)));

                StyleRange styleRange = new StyleRange();
                styleRange.start = length;
                styleRange.length = 1;
                styleRange.foreground = fgColor;
                textPane.setStyleRange(styleRange);

                charIndex++;
            }

            // 添加新光标
            if (charIndex < currentString.length()) {
                addNewCursor();
            } else {
                textPane.append("\n");
                processingString = false;
                currentIndex++;
            }

            textPane.setTopIndex(textPane.getLineCount() - 1);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            cursorLock.set(false);
        }
    }

    private void processCommand(Object item) {
        try {
            if (item instanceof SpeedCommand) {
                SpeedCommand cmd = (SpeedCommand) item;
                delay = cmd.speed;
            } else if (item instanceof ColorCommand) {
                ColorCommand cmd = (ColorCommand) item;

                // 释放旧颜色资源
                if (fgColor != null && !fgColor.isDisposed()) {
                    fgColor.dispose();
                }
                if (bgColor != null && !bgColor.isDisposed()) {
                    bgColor.dispose();
                }

                // 创建新颜色
                fgColor = new Color(display,
                        Integer.parseInt(cmd.fg.substring(1, 3), 16),
                        Integer.parseInt(cmd.fg.substring(3, 5), 16),
                        Integer.parseInt(cmd.fg.substring(5, 7), 16));

                bgColor = new Color(display,
                        Integer.parseInt(cmd.bg.substring(1, 3), 16),
                        Integer.parseInt(cmd.bg.substring(3, 5), 16),
                        Integer.parseInt(cmd.bg.substring(5, 7), 16));

                textPane.setForeground(fgColor);
                textPane.setBackground(bgColor);
            } else if (item instanceof ClearCommand) {
                textPane.setText("");
            } else if (item instanceof WindowMaximizedCommand) {
                shell.setMaximized(true);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private synchronized void removeLastCursor() {
        int len = textPane.getCharCount();
        if (len > 0 && cursorVisible) {
            textPane.replaceTextRange(len - 1, 1, "");
            cursorVisible = false;
        }
    }

    private synchronized void addNewCursor() {
        if (!cursorVisible) {
            int length = textPane.getCharCount();
            textPane.append("▉");

            StyleRange styleRange = new StyleRange();
            styleRange.start = length;
            styleRange.length = 1;
            styleRange.foreground = fgColor;
            textPane.setStyleRange(styleRange);

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
        final String fg, bg;

        ColorCommand(String fg, String bg) {
            this.fg = fg;
            this.bg = bg;
        }
    }

    private static class ClearCommand {
    }

    private static class WindowMaximizedCommand {
    }

    public static void main(String[] args) {
        Display display = new Display();
        new SwtAboutWindow(display, Map.of(
                "{version}", "1.0.0",
                "{username}", System.getProperty("user.name")
        ));

        while (!display.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
    }
}
