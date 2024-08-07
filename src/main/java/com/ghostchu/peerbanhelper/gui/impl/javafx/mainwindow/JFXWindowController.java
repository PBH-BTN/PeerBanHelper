package com.ghostchu.peerbanhelper.gui.impl.javafx.mainwindow;

import com.ghostchu.peerbanhelper.gui.impl.javafx.ListLogEntry;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import lombok.Getter;

import java.net.URL;
import java.util.ResourceBundle;

public class JFXWindowController implements Initializable {
    @FXML
    @Getter
    private TabPane tabPane;
    @FXML
    @Getter
    private Tab tabLogs;
    @FXML
    @Getter
    private ListView<ListLogEntry> logsListView;
    @FXML
    @Getter
    private Menu menuProgram;
    @FXML
    @Getter
    private Menu menuWebui;
    @FXML
    @Getter
    private MenuItem menuProgramOpenInGithub;
    @FXML
    @Getter
    private MenuItem menuProgramQuit;
    @FXML
    @Getter
    private MenuItem menuWebUIOpenInBrowser;
    @FXML
    @Getter
    private MenuItem menuWebUICopyWebuiToken;
    @FXML
    @Getter
    private MenuItem menuProgramOpenDataDirectory;
    @FXML
    @Getter
    private Menu menuDebug;
    @FXML
    @Getter
    private MenuItem menuDebugReload;
    @FXML
    @Getter
    private MenuItem menuDebugHeapDump;
    @FXML
    @Getter
    private MenuItem menuDebugPrintThreads;
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

    }
}
