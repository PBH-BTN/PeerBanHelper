package com.ghostchu.peerbanhelper.gui.impl.javafx.mainwindow;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import lombok.Getter;

import java.net.URL;
import java.util.ResourceBundle;

@Getter
public class JFXWindowController implements Initializable {
    @FXML
    private TabPane tabPane;
    @FXML
    private Tab tabLogs;
    @FXML
    private TextArea logsTextArea;
    @FXML
    private Menu menuProgram;
    @FXML
    private Menu menuWebui;
    @FXML
    private MenuItem menuProgramOpenInGithub;
    @FXML
    private MenuItem menuProgramQuit;
    @FXML
    private MenuItem menuProgramOpenInBrowser;
    @FXML
    private MenuItem menuProgramCopyWebuiToken;
    @FXML
    private MenuItem menuProgramOpenDataDirectory;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

    }
}
