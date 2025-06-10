//package com.ghostchu.peerbanhelper.gui.impl.qt;
//
//import com.ghostchu.peerbanhelper.Main;
//import com.ghostchu.peerbanhelper.text.Lang;
//import io.qt.core.Qt;
//import io.qt.gui.QFont;
//import io.qt.widgets.*;
//import lombok.extern.slf4j.Slf4j;
//
//import java.util.Map;
//
//import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;
//
//@Slf4j
//public class QtAboutWindow extends QDialog {
//    private final Map<String, String> replaces;
//    private QTextEdit creditsTextArea;
//
//    public QtAboutWindow(QWidget parent, Map<String, String> replaces) {
//        super(parent);
//        this.replaces = replaces;
//        setWindowTitle(tlUI(Lang.GUI_MENU_ABOUT));
//        setModal(true);
//        resize(600, 400);
//
//        setupUI();
//        loadCreditsText();
//
//        show();
//    }
//
//    private void setupUI() {
//        QVBoxLayout layout = new QVBoxLayout(this);
//
//        // 标题和版本信息
//        QLabel titleLabel = new QLabel("PeerBanHelper");
//        QFont titleFont = titleLabel.font();
//        titleFont.setPointSize(18);
//        titleFont.setBold(true);
//        titleLabel.setFont(titleFont);
//        titleLabel.setAlignment(Qt.AlignmentFlag.AlignCenter);
//        layout.addWidget(titleLabel);
//
//        QLabel versionLabel = new QLabel("Version: " + Main.getMeta().getVersion());
//        versionLabel.setAlignment(Qt.AlignmentFlag.AlignCenter);
//        layout.addWidget(versionLabel);
//
//        layout.addSpacing(10);
//
//        // 创建标签页控件
//        QTabWidget tabWidget = new QTabWidget();
//        // 关于标签页
//        QWidget aboutTab = createAboutTab();
//        tabWidget.addTab(aboutTab, "About");
//
//        // 致谢标签页
//        QWidget creditsTab = createCreditsTab();
//        tabWidget.addTab(creditsTab, "Credits");
//
//        layout.addWidget(tabWidget);
//        // 关闭按钮
//        QPushButton closeButton = new QPushButton(tlUI(Lang.GUI_COMMON_CANCEL));
//        closeButton.clicked.connect(this::accept);
//
//        QHBoxLayout buttonLayout = new QHBoxLayout();
//        buttonLayout.addStretch();
//        buttonLayout.addWidget(closeButton);
//
//        layout.addLayout(buttonLayout);
//        setLayout(layout);
//    }
//
//    private QWidget createAboutTab() {
//        QWidget widget = new QWidget();
//        QVBoxLayout layout = new QVBoxLayout(widget);
//
//        QTextEdit aboutText = new QTextEdit();
//        aboutText.setReadOnly(true);
//        aboutText.setHtml("""
//                <html>
//                <body>
//                <h3>PeerBanHelper About</h3>
//                </body>
//                </html>
//                """);
//
//        layout.addWidget(aboutText);
//        return widget;
//    }
//
//    private QWidget createCreditsTab() {
//        QWidget widget = new QWidget();
//        QVBoxLayout layout = new QVBoxLayout(widget);
//
//        creditsTextArea = new QTextEdit();
//        creditsTextArea.setReadOnly(true);
//        creditsTextArea.setFont(new QFont("Consolas", 10));
//
//        layout.addWidget(creditsTextArea);
//        return widget;
//    }
//
//    private void loadCreditsText() {
//        try {
//            var inputStream = Main.class.getResourceAsStream("/assets/credits.txt");
//            if (inputStream != null) {
//                String creditsContent = new String(inputStream.readAllBytes());
//
//                // 替换占位符
//                for (Map.Entry<String, String> entry : replaces.entrySet()) {
//                    creditsContent = creditsContent.replace(entry.getKey(), entry.getValue());
//                }
//
//                creditsTextArea.setPlainText(creditsContent);
//            } else {
//                creditsTextArea.setPlainText("Credits file not found.");
//            }
//        } catch (Exception e) {
//            log.warn("Failed to load credits text", e);
//            creditsTextArea.setPlainText("Failed to load credits: " + e.getMessage());
//        }
//    }
//}
