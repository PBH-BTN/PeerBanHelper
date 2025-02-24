package com.ghostchu.lib.jni;

import com.ghostchu.peerbanhelper.Main;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Slf4j
@Component
public final class EcoMode {

    public boolean ecoMode(boolean enable) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (!os.startsWith("win")) {
            throw new IllegalStateException("Only Windows OS support EcoMode API");
        }
        String osVersion = System.getProperty("os.version");
        if (!isSupportedVersion(osVersion)) { // 检查是否为版本号大于22621
            throw new IllegalStateException("EcoQoS is only supported on Windows version 22621 or higher");
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

    // 判断是否为受支持的版本
    private boolean isSupportedVersion(String osVersion) {
        try {
            int versionNumber = Integer.parseInt(osVersion.replaceAll("\\.", ""));
            return versionNumber >= 10022621; // 10.0.22621 开始支持
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private native static String setEcoMode(boolean enable);
}
