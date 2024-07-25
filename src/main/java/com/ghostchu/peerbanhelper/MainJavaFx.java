package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.gui.impl.javafx.mainwindow.JFXWindowController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class MainJavaFx extends Application {
    public static AtomicBoolean ready = new AtomicBoolean(false);
    public static MainJavaFx INSTANCE;
    @Getter
    private static Stage stage;
    @Getter
    private JFXWindowController controller;

    public static void launchApp(String[] args) {
        launch(args);
    }

    public static void main(String[] args) {
        Main.main(args);
    }

    @Override
    public void start(Stage st) throws Exception {
        INSTANCE = this;
        stage = st;
        FXMLLoader fxmlLoader = new FXMLLoader(MainJavaFx.class.getResource("/javafx/main_window.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);
        st.setTitle("PeerBanHelper (JavaFx) - Loading...");
        st.setScene(scene);
        st.setWidth(1000);
        st.setHeight(600);
        st.getIcons().add(new Image("assets/icon.png"));
        st.show();
        controller = fxmlLoader.getController();
        ready.set(true);
    }

    private void closeWindowEvent(WindowEvent windowEvent) {

    }
}