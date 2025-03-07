package com.ghostchu.lib.jni;

import com.ghostchu.peerbanhelper.Main;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.Locale;

@Slf4j
@Component
public final class EcoMode {

    public boolean ecoMode(boolean enable) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (!os.startsWith("win")) {
            throw new IllegalStateException("Only Windows OS support EcoMode API");
        }
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        try {
            File tmpFile = new File(Main.getDataDirectory(), "pbh-jni-lib.dll");
            tmpFile.deleteOnExit();
            if (arch.contains("aarch64")) {
                Files.write(tmpFile.toPath(), Main.class.getResourceAsStream("/native/windows/ghost-common-jni_vc2015_aarch64.dll").readAllBytes());
            } else {
                Files.write(tmpFile.toPath(), Main.class.getResourceAsStream("/native/windows/ghost-common-jni_vc2015_amd64.dll").readAllBytes());
            }
            System.load(tmpFile.getAbsolutePath());
        } catch (Throwable e) {
            log.error("Unable load JNI native libraries", e);
        }
        try {
            String data = setEcoMode(enable);
            return data.equals("SUCCESS");
        } catch (Throwable e) {
            return false;
        }
    }

    private native static String setEcoMode(boolean enable);
}
