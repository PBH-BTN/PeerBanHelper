package com.ghostchu.peerbanhelper.gui.impl.swt;

import com.ghostchu.peerbanhelper.gui.ProgressDialog;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

@Slf4j
public final class SwtProgressDialog implements ProgressDialog {

    private Shell shell;
    private final Display display;
    private ProgressBar progressBar;
    private Label descriptionLabel;
    private Label commentLabel;
    private Button stopButton;
    private float progress;

    public SwtProgressDialog(String title, String description, String buttonText, Runnable buttonEvent, boolean allowCancel) {
        // 获取当前显示器
        this.display = Display.getDefault();
        display.syncExec(() -> {
            // 创建主窗口
            this.shell = new Shell(display, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
            this.shell.setText(title);
            this.shell.setSize(400, 200);

            // 设置布局
            GridLayout layout = new GridLayout(2, false);
            layout.marginWidth = 10;
            layout.marginHeight = 10;
            layout.horizontalSpacing = 10;
            shell.setLayout(layout);

            // 创建信息图标
            Label iconLabel = new Label(shell, SWT.NONE);
            try {
                Image infoIcon = new Image(display, display.getSystemImage(SWT.ICON_INFORMATION).getImageData());
                iconLabel.setImage(infoIcon);
                // 注册销毁事件以释放图像资源
                shell.addDisposeListener(e -> infoIcon.dispose());
            } catch (Exception e) {
                log.warn("Failed to load information icon", e);
            }
            GridData iconData = new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false);
            iconLabel.setLayoutData(iconData);

            // 创建描述文本
            this.descriptionLabel = new Label(shell, SWT.WRAP);
            this.descriptionLabel.setText(description);
            GridData descData = new GridData(SWT.FILL, SWT.FILL, true, true);
            descData.widthHint = 350;
            descData.heightHint = 80;
            descriptionLabel.setLayoutData(descData);

            // 创建注释标签
            this.commentLabel = new Label(shell, SWT.NONE);
            GridData commentData = new GridData(SWT.FILL, SWT.CENTER, true, false);
            commentData.horizontalSpan = 2;
            commentLabel.setLayoutData(commentData);

            // 创建进度条
            this.progressBar = new ProgressBar(shell, SWT.SMOOTH);
            GridData progressData = new GridData(SWT.FILL, SWT.CENTER, true, false);
            progressData.horizontalSpan = 1;
            progressBar.setLayoutData(progressData);

            // 默认设置为不确定模式
            setProgressDisplayIndeterminate(true);

            // 创建按钮
            this.stopButton = new Button(shell, SWT.PUSH);
            this.stopButton.setText(buttonText);
            this.stopButton.setEnabled(allowCancel);
            GridData buttonData = new GridData(SWT.END, SWT.CENTER, false, false);
            stopButton.setLayoutData(buttonData);

            if (buttonEvent != null) {
                stopButton.addListener(SWT.Selection, e -> buttonEvent.run());
            }

            // 居中显示
            shell.pack();
            centerShell(shell);
        });
    }

    private void centerShell(Shell shell) {
        Monitor primary = display.getPrimaryMonitor();
        org.eclipse.swt.graphics.Rectangle bounds = primary.getBounds();
        org.eclipse.swt.graphics.Rectangle rect = shell.getBounds();
        int x = bounds.x + (bounds.width - rect.width) / 2;
        int y = bounds.y + (bounds.height - rect.height) / 2;
        shell.setLocation(x, y);
    }

    @Override
    public void updateProgress(float progress) {
        this.progress = progress;
        display.asyncExec(() -> {
            if (!progressBar.isDisposed()) {
                progressBar.setSelection((int) (progress * 100));
            }
        });
    }

    @Override
    public void show() {
        display.asyncExec(() -> {
            if (!shell.isDisposed() && !shell.isVisible()) {
                shell.open();
            }
        });
    }

    @Override
    public void close() {
        display.asyncExec(() -> {
            if (!shell.isDisposed() && shell.isVisible()) {
                shell.close();
            }
        });
    }

    @Override
    public void setTitle(String title) {
        display.asyncExec(() -> {
            if (!shell.isDisposed()) {
                shell.setText(title);
            }
        });
    }

    @Override
    public void setDescription(String description) {
        display.asyncExec(() -> {
            if (!descriptionLabel.isDisposed()) {
                descriptionLabel.setText(description);
                shell.layout(true, true);
            }
        });
    }

    @Override
    public void setButtonText(String buttonText) {
        display.asyncExec(() -> {
            if (!stopButton.isDisposed()) {
                stopButton.setText(buttonText);
            }
        });
    }

    @Override
    public void setButtonEvent(Runnable buttonEvent) {
        display.asyncExec(() -> {
            if (!stopButton.isDisposed()) {
                // 移除现有的所有监听器
                for (Listener listener : stopButton.getListeners(SWT.Selection)) {
                    stopButton.removeListener(SWT.Selection, listener);
                }

                // 添加新的监听器
                if (buttonEvent != null) {
                    stopButton.addListener(SWT.Selection, e -> buttonEvent.run());
                }
            }
        });
    }

    @Override
    public void setAllowCancel(boolean allowCancel) {
        display.asyncExec(() -> {
            if (!stopButton.isDisposed()) {
                stopButton.setEnabled(allowCancel);
            }
        });
    }

    @Override
    public void setProgressDisplayIndeterminate(boolean indeterminate) {
        display.asyncExec(() -> {
            if (!progressBar.isDisposed()) {
                if (indeterminate) {
                    progressBar.setState(SWT.INDETERMINATE);
                } else {
                    progressBar.setState(SWT.NORMAL);
                    progressBar.setSelection((int) (progress * 100));
                }
            }
        });
    }

    @Override
    public void setComment(String comment) {
        display.asyncExec(() -> {
            if (!commentLabel.isDisposed()) {
                commentLabel.setText(comment != null ? comment : "");
                shell.layout();
            }
        });
    }
}
