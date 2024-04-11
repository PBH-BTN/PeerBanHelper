package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.config.ConfigManager;
import com.ghostchu.peerbanhelper.text.Lang;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogManager;

@Slf4j
public class Main {
    @Getter
    private static final File dataDirectory = new File("data");
    @Getter
    private static final File logsDirectory = new File(dataDirectory, "logs");
    @Getter
    private static final File configDirectory = new File(dataDirectory, "config");
    @Getter
    private static final File pluginDirectory = new File(dataDirectory, "plugins");

    @Getter
    private static BuildMeta meta = new BuildMeta();

    private static final AtomicInteger shutdown = new AtomicInteger(0);

    @Getter
    private static PeerBanHelperServer server;

    public static void main(String[] args) throws InterruptedException, IOException {
        // Preparing env
        prepareLogger();
        prepareChatsetForWindows();
        workaroundGraalVM();
        prepareBuildMeta();

        // Starting!
        log.info(Lang.MOTD, meta.getVersion());

        // Load Configurations
        log.info(Lang.LOADING_CONFIG);
        try {
            if (!ConfigManager.initConfiguration()) {
                log.warn(Lang.CONFIG_PEERBANHELPER);
                Scanner scanner = new Scanner(System.in);
                scanner.nextLine();
                return;
            }
        } catch (IOException e) {
            log.error(Lang.ERR_SETUP_CONFIGURATION, e);
            return;
        }
        ConfigManager.loadConfig();

        // Start server
        try {
            server = new PeerBanHelperServer();
        } catch (Exception e) {
            log.error(Lang.BOOTSTRAP_FAILED, e);
            throw new RuntimeException(e);
        }

        // Shutdown hooks
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (shutdown) {
                shutdown.set(1); // We're going to shutdown!
                shutdown.notifyAll();
                log.info(Lang.PBH_SHUTTING_DOWN);
                server.shutdown();
                shutdown.set(2); // We're completed shutdown!
                shutdown.notifyAll();
            }
        }));
        while (shutdown.get() != 2) {
            synchronized (shutdown) {
                shutdown.wait(1000 * 5L);
            }
        }

        synchronized (shutdown) {
            shutdown.set(3);
            shutdown.notifyAll(); // App exit
        }
    }

    private static void prepareLogger() throws IOException {
        if (!logsDirectory.exists() && !logsDirectory.mkdirs())
            throw new IOException("Cannot create Logger Directory correctly!");
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
    }

    private static void workaroundGraalVM() {
        // 此方法允许 Native Image Agent 在生成本地二进制文件时正确识别缺少的类
        try {
            Class.forName("java.util.logging.FileHandler");
        } catch (ClassNotFoundException ignored) {
        }
    }

    private static void prepareChatsetForWindows() throws InterruptedException, IOException {
        if (System.getProperties().getProperty("os.name").toUpperCase().contains("WINDOWS")) {
            if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "chcp", "65001").inheritIO();
                Process p = pb.start();
                p.waitFor();
                System.out.println("Chcp switched to UTF-8 (65001) - GraalVM Native Image");
            }
        }
    }

    private static void prepareBuildMeta() {
        meta = new BuildMeta();
        try (InputStream stream = Main.class.getResourceAsStream("/build-info.yml")) {
            if (stream == null) {
                log.error(Lang.ERR_BUILD_NO_INFO_FILE);
            } else {
                String str = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                YamlConfiguration configuration = new YamlConfiguration();
                configuration.loadFromString(str);
                meta.loadBuildMeta(configuration);
            }
        } catch (IOException | InvalidConfigurationException e) {
            log.error(Lang.ERR_CANNOT_LOAD_BUILD_INFO, e);
        }
    }

    private static void handleCommand(String input) {

    }

    public static String getUserAgent() {
        return "PeerBanHelper/" + meta.getVersion();
    }

}
