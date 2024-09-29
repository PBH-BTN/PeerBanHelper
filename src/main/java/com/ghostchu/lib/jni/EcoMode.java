package com.ghostchu.lib.jni;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.telemetry.rollbar.RollbarErrorReporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Slf4j
@Component
public class EcoMode {
    private final RollbarErrorReporter rollbarErrorReporter;

    public EcoMode(RollbarErrorReporter rollbarErrorReporter) {
        this.rollbarErrorReporter = rollbarErrorReporter;
    }

    public boolean ecoMode(boolean enable) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (!os.startsWith("win")) {
            throw new IllegalStateException("Only Windows OS support EcoMode API");
        }
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        try {
            File tmpFile = Files.createTempFile("pbh-jni-lib", ".dll").toFile();
            tmpFile.deleteOnExit();
            if (arch.contains("aarch64")) {
                Files.copy(Main.class.getResourceAsStream("/native/windows/ghost-common-jni_vc2015_aarch64.dll"), tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(Main.class.getResourceAsStream("/native/windows/ghost-common-jni_vc2015_amd64.dll"), tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            System.load(tmpFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Unable load JNI native libraries", e);
            rollbarErrorReporter.warning(e);
        }
        try {
            String data = setEcoMode(enable);
            return data.equals("SUCCESS");
        } catch (Throwable e) {
            rollbarErrorReporter.warning(e);
            return false;
        }
    }

    private native static String setEcoMode(boolean enable);
}
