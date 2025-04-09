package com.ghostchu.peerbanhelper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Slf4j/**/
@Component
public class CrashManager {
    private final File file;


    public CrashManager() {
        this.file = new File(Main.getDataDirectory(), "running.marker");
    }

    public void putRunningFlag() {
        try {
            this.file.createNewFile();
            this.file.deleteOnExit();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (this.file.exists()) {
                    this.file.delete();
                }
            }));
        } catch (IOException e) {
            log.error("Unable to create running flag file", e);
        }
    }

    public boolean isRunningFlagExists() {
        return this.file.exists();
    }
}
