//package com.ghostchu.peerbanhelper.gui.impl.qt;
//
//import com.ghostchu.peerbanhelper.gui.ProgressDialog;
//import io.qt.core.QTimer;
//import io.qt.gui.QFont;
//import io.qt.widgets.*;
//import lombok.extern.slf4j.Slf4j;
//
//@Slf4j
//public class QtProgressDialog implements ProgressDialog {
//    private final QDialog dialog;
//    private QProgressBar progressBar;
//    private QLabel messageLabel;
//    private QLabel titleLabel;
//    private QPushButton actionButton;
//    private QPushButton cancelButton;
//    private boolean cancelled = false;
//    private boolean disposed = false;
//    private Runnable buttonEvent;
//
//    public QtProgressDialog(String title, String description, String buttonText,
//                            Runnable buttonEvent, boolean allowCancel) {
//        this.dialog = new QDialog();
//        this.buttonEvent = buttonEvent;
//
//        dialog.setWindowTitle(title);
//        dialog.setModal(true);
//        dialog.resize(400, 150);
//
//        setupUI(title, description, buttonText, allowCancel);
//        // 显示对话框
//        QTimer.singleShot(0, () -> dialog.show());
//    }
//
//    private void setupUI(String title, String description, String buttonText, boolean allowCancel) {
//        QVBoxLayout layout = new QVBoxLayout(dialog);
//
//        // 标题
//        titleLabel = new QLabel(title);
//        QFont titleFont = titleLabel.font();
//        titleFont.setBold(true);
//        titleLabel.setFont(titleFont);
//        layout.addWidget(titleLabel);
//
//        // 描述
//        messageLabel = new QLabel(description);
//        layout.addWidget(messageLabel);
//
//        // 进度条
//        progressBar = new QProgressBar();
//        progressBar.setMinimum(0);
//        progressBar.setMaximum(100);
//        progressBar.setValue(0);
//        layout.addWidget(progressBar);
//
//        // 按钮布局
//        QHBoxLayout buttonLayout = new QHBoxLayout();
//        buttonLayout.addStretch();
//
//        if (buttonText != null && !buttonText.isEmpty()) {
//            actionButton = new QPushButton(buttonText);
//            actionButton.clicked.connect(() -> {
//                if (buttonEvent != null) {
//                    buttonEvent.run();
//                }
//            });
//            buttonLayout.addWidget(actionButton);
//        }
//        if (allowCancel) {
//            cancelButton = new QPushButton("取消");
//            cancelButton.clicked.connect(() -> {
//                cancelled = true;
//                dialog.accept(); // 关闭对话框
//            });
//            buttonLayout.addWidget(cancelButton);
//        }
//
//        layout.addLayout(buttonLayout);
//        dialog.setLayout(layout);
//    }
//
//    @Override
//    public void updateProgress(float progress) {
//        QTimer.singleShot(0, () -> {
//            if (progressBar != null) {
//                int progressInt = Math.round(progress * 100);
//                progressBar.setValue(progressInt);
//            }
//        });
//    }
//
//    @Override
//    public void setComment(String message) {
//        QTimer.singleShot(0, () -> {
//            if (messageLabel != null) {
//                messageLabel.setText(message);
//            }
//        });
//    }    // 实现ProgressDialog接口的close方法
//
//    @Override
//    public void close() {
//        QTimer.singleShot(0, () -> {
//            dialog.hide(); // 使用hide而不是accept
//            disposed = true;
//        });
//    }
//
//    // 添加一个cleanup方法用于资源清理
//    public void cleanup() {
//        if (!disposed) {
//            disposed = true;
//            dialog.hide();
//        }
//    }
//
//    public boolean isCancelled() {
//        return cancelled;
//    }
//
//    @Override
//    public void setProgressDisplayIndeterminate(boolean indeterminate) {
//        QTimer.singleShot(0, () -> {
//            if (progressBar != null) {
//                if (indeterminate) {
//                    progressBar.setMinimum(0);
//                    progressBar.setMaximum(0); // 无限进度条
//                } else {
//                    progressBar.setMinimum(0);
//                    progressBar.setMaximum(100);
//                }
//            }
//        });
//    }
//
//    @Override
//    public void setTitle(String title) {
//        QTimer.singleShot(0, () -> {
//            dialog.setWindowTitle(title);
//            if (titleLabel != null) {
//                titleLabel.setText(title);
//            }
//        });
//    }
//
//    @Override
//    public void setDescription(String description) {
//        setComment(description);
//    }
//
//    @Override
//    public void setButtonText(String buttonText) {
//        QTimer.singleShot(0, () -> {
//            if (actionButton != null) {
//                actionButton.setText(buttonText);
//            }
//        });
//    }
//
//    @Override
//    public void setButtonEvent(Runnable buttonEvent) {
//        this.buttonEvent = buttonEvent;
//        if (actionButton != null) {
//            // 重新连接按钮事件
//            actionButton.clicked.disconnect();
//            actionButton.clicked.connect(() -> {
//                if (this.buttonEvent != null) {
//                    this.buttonEvent.run();
//                }
//            });
//        }
//    }
//
//    @Override
//    public void setAllowCancel(boolean allowCancel) {
//        QTimer.singleShot(0, () -> {
//            if (cancelButton != null) {
//                cancelButton.setVisible(allowCancel);
//            }
//        });
//    }
//
//    @Override
//    public void show() {
//        QTimer.singleShot(0, () -> dialog.show());
//    }
//}
