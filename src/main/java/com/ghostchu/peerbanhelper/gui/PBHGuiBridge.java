package com.ghostchu.peerbanhelper.gui;

import com.ghostchu.peerbanhelper.PeerBanHelper;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

@Component
public class PBHGuiBridge {
    private final PeerBanHelper peerBanHelper;
    private final JavalinWebContainer javalinWebContainer;

    public PBHGuiBridge(PeerBanHelper peerBanHelper, JavalinWebContainer javalinWebContainer) {
        // Constructor logic if needed
        this.peerBanHelper = peerBanHelper;
        this.javalinWebContainer = javalinWebContainer;
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
}
