package com.ghostchu.peerbanhelper.gui.impl.swing;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.gui.ProgressDialog;

import javax.swing.*;
import java.awt.*;

public final class SwingProgressDialog implements ProgressDialog {

    private final JProgressBar progressBar = new JProgressBar();
    private final JFrame frame = new JFrame("Unknown Title");
    private final JLabel descriptionLabel = new JLabel("Unknown Description");
    private final JLabel commentLabel = new JLabel("");
    private final JButton stopButton;
    private float progress;

    public SwingProgressDialog(String title, String description, String buttonText, Runnable buttonEvent, boolean allowCancel) {
        // 创建主窗口
        setTitle(title);

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(400, 200);
        frame.setLayout(new BorderLayout(10, 10));

        // 创建一个面板来存放图标和描述文本
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

        // 使用 Swing 内置的信息图标
        JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.informationIcon"));
        topPanel.add(iconLabel);

        // 创建描述文本
        setDescription("<html>" + description + "</html>");
        descriptionLabel.setLayout(new FlowLayout(FlowLayout.LEFT));
        descriptionLabel.setVerticalAlignment(SwingConstants.TOP);
        // 根据文本自动调整大小
        descriptionLabel.setPreferredSize(new Dimension(350, 80));
        topPanel.add(descriptionLabel);

        setProgressDisplayIndeterminate(true);
        Main.getGuiManager().taskbarControl().updateProgress(frame, Taskbar.State.INDETERMINATE, 0.0f);

        // 创建按钮
        this.stopButton = new JButton(buttonText);
        if (!allowCancel) {
            stopButton.setEnabled(false);
        }
        stopButton.addActionListener(e -> {
            if (buttonEvent != null) {
                buttonEvent.run();
            }
        });

        // 创建底部面板
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.add(commentLabel, BorderLayout.NORTH);
        bottomPanel.add(progressBar, BorderLayout.CENTER);
        bottomPanel.add(stopButton, BorderLayout.EAST);

        // 添加面板到窗口
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(bottomPanel, BorderLayout.SOUTH);
        frame.pack();
        frame.setVisible(false);
    }

    public void updateProgress(float progress) {
        // 让进度条显示具体进度
        this.progress = progress;
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue((int) (progress * 100));  // 更新进度条的值，0 到 100
            Main.getGuiManager().taskbarControl().updateProgress(frame, Taskbar.State.NORMAL, progress);
        });
    }

    @Override
    public void show() {
        // 显示窗口
        SwingUtilities.invokeLater(() -> frame.setVisible(true));
    }

    @Override
    public void close() {
        SwingUtilities.invokeLater(() -> frame.setVisible(false));
        Main.getGuiManager().taskbarControl().updateProgress(frame, Taskbar.State.OFF, -1);
    }

    @Override
    public void setTitle(String title) {
        SwingUtilities.invokeLater(() -> frame.setTitle(title));
    }

    @Override
    public void setDescription(String description) {
        SwingUtilities.invokeLater(() -> {
            descriptionLabel.setText(description);
        });
    }

    @Override
    public void setButtonText(String buttonText) {
        SwingUtilities.invokeLater(() -> {
            stopButton.setText(buttonText);
        });
    }

    @Override
    public void setButtonEvent(Runnable buttonEvent) {
        SwingUtilities.invokeLater(() -> {
            stopButton.removeActionListener(stopButton.getActionListeners()[0]);
            stopButton.addActionListener(e -> {
                if (buttonEvent != null) {
                    buttonEvent.run();
                }
            });
        });
    }

    @Override
    public void setAllowCancel(boolean allowCancel) {
        SwingUtilities.invokeLater(() -> {
            stopButton.setEnabled(allowCancel);
        });
    }

    @Override
    public void setProgressDisplayIndeterminate(boolean indeterminate) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(indeterminate);
            progressBar.setStringPainted(!indeterminate);
            Main.getGuiManager().taskbarControl().updateProgress(frame, indeterminate ? Taskbar.State.INDETERMINATE : Taskbar.State.NORMAL, (progress * 100));
        });
    }

    @Override
    public void setComment(String comment) {
        SwingUtilities.invokeLater(() -> {
            commentLabel.setText(comment);
        });
    }
}
