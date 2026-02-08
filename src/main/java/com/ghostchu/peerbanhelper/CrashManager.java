package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.alert.AlertLevel;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Slf4j/**/
@Component
public final class CrashManager {
    private final File file;
    private final AlertManager alertManager;


    public CrashManager(AlertManager alertManager) {
        this.file = new File(Main.getDataDirectory(), "running.marker");
        this.alertManager = alertManager;
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
            Sentry.captureException(e);
        }
    }

    public boolean isRunningFlagExists() {
        return this.file.exists();
    }

    public void checkCrashRecovery() {
        for (String startupArg : Main.getStartupArgs()) {
            if (!startupArg.startsWith("crashRecovery")) continue;
            String[] args = startupArg.split(":");
            if (args.length != 2) {
                log.error("Invalid crash recovery argument: {}", startupArg);
                continue;
            }
            String pid = args[1];
            processCrashRecovery(pid);
            break;
        }
    }

    private void processCrashRecovery(String pid) {
        File crashInDataDirectory = new File(Main.getDataDirectory(), "hs_err_pid" + pid + ".log");
        log.info(crashInDataDirectory.getAbsolutePath());
        if (!crashInDataDirectory.exists()) {
            crashInDataDirectory = new File(new File(Optional.ofNullable(System.getenv("LOCALAPPDATA")).orElse("."), "PeerBanHelper"), "hs_err_pid" + pid + ".log");
            log.info(crashInDataDirectory.getAbsolutePath());
        }
        if (!crashInDataDirectory.exists()) {
            crashInDataDirectory = new File(new File(System.getProperty("java.io.tmpdir", "")), "hs_err_pid" + pid + ".log");
            log.info(crashInDataDirectory.getAbsolutePath());
        }
        if (!crashInDataDirectory.exists()) {
            crashInDataDirectory = new File(new File(System.getProperty("user.home", "")), "hs_err_pid" + pid + ".log");
            log.info(crashInDataDirectory.getAbsolutePath());
        }
        if (!crashInDataDirectory.exists()) {
            crashInDataDirectory = new File("hs_err_pid" + pid + ".log"); // %WORKDIR%
            log.info(crashInDataDirectory.getAbsolutePath());
        }
        if (!crashInDataDirectory.exists()) {
            log.warn("No crash file found for pid: {}", pid);
        }

        if (crashInDataDirectory.exists()) {
            SentryEvent event = new SentryEvent();
            event.setTag("type", "jvm-fatal-crash");
            event.setLevel(SentryLevel.FATAL);
            StringBuilder builder = new StringBuilder("JVM Fatal Crash detected\n\n");
            try {
                builder.append(Files.readString(crashInDataDirectory.toPath()));
            } catch (IOException e) {
                builder.append("Unable to read crash file: ").append(e.getClass().getName()).append(": ").append(e.getMessage());
            }
            Message message = new Message();
            message.setMessage(builder.toString());
            event.setMessage(message);
            Sentry.captureEvent(event);
        }

        alertManager.publishAlert(true, AlertLevel.FATAL, "peerbanhelper-crash-recovery-" + UUID.randomUUID(),
                new TranslationComponent(Lang.CRASH_MANAGER_CRASH_RECOVERY_ALERT_TITLE),
                new TranslationComponent(Lang.CRASH_MANAGER_CRASH_RECOVERY_ALERT_DESCRIPTION,
                        pid,
                        MsgUtil.getDateFormatter().format(new Date()),
                        !crashInDataDirectory.exists() ? "N/A" : crashInDataDirectory.getAbsolutePath()));
    }
}
