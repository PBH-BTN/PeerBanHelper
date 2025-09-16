package com.ghostchu.peerbanhelper.gui;

import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.database.table.AlertEntity;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Component
public class PBHGuiBridge {
    private final JavalinWebContainer javalinWebContainer;
    private final AlertManager alertManager;

    public PBHGuiBridge(JavalinWebContainer javalinWebContainer, AlertManager alertManager) {
        // Constructor logic if needed
        this.javalinWebContainer = javalinWebContainer;
        this.alertManager = alertManager;
    }

    public Optional<String> getWebUiToken() {
        return Optional.ofNullable(javalinWebContainer.getToken());
    }

    public Optional<URI> getWebUiUrl(){
        if(javalinWebContainer.isStarted()){
            return Optional.of(URI.create("http://127.0.0.1:" +javalinWebContainer.javalin().port() + "?token=" + javalinWebContainer.getToken()));
        }else{
            return  Optional.empty();
        }
    }

    public List<AlertEntity> getAlerts() {
        return alertManager.getUnreadAlerts();
    }
}
