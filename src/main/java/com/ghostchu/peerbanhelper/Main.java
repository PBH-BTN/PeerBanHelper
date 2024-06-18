package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.config.MainConfigUpdateScript;
import com.ghostchu.peerbanhelper.config.PBHConfigUpdater;
import com.ghostchu.peerbanhelper.config.ProfileUpdateScript;
import com.ghostchu.peerbanhelper.event.PBHShutdownEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiManager;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.javafx.JavaFxImpl;
import com.ghostchu.peerbanhelper.gui.impl.swing.SwingGuiImpl;
import com.ghostchu.peerbanhelper.text.Lang;
import com.google.common.eventbus.EventBus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.logging.Level;

@Slf4j
public class Main {
    @Getter
    private static final File dataDirectory = new File("data");
    @Getter
    private static final File logsDirectory = new File(dataDirectory, "logs");
    @Getter
    private static final File configDirectory = new File(dataDirectory, "config");
    private static final File pluginDirectory = new File(dataDirectory, "plugins");
    @Getter
    private static BuildMeta meta = new BuildMeta();
    @Getter
    private static PeerBanHelperServer server;
    @Getter
    private static PBHGuiManager guiManager;
    @Getter
    private static final EventBus eventBus = new EventBus();
    @Getter
    private static File mainConfigFile;
    @Getter
    private static File profileConfigFile;

    public static void main(String[] args) {
        setupLog4j2();
        initBuildMeta();
        initGUI(args);
        setupConfiguration();
        guiManager.createMainWindow();
        mainConfigFile = new File(configDirectory, "config.yml");
        YamlConfiguration mainConfig = loadConfiguration(mainConfigFile);
        new PBHConfigUpdater(mainConfigFile, mainConfig).update(new MainConfigUpdateScript(mainConfig));
        profileConfigFile = new File(configDirectory, "profile.yml");
        YamlConfiguration profileConfig = loadConfiguration(profileConfigFile);
        new PBHConfigUpdater(profileConfigFile, profileConfig).update(new ProfileUpdateScript(profileConfig));
        String pbhServerAddress = mainConfig.getString("server.prefix", "http://127.0.0.1:" + mainConfig.getInt("server.http"));
        try {
            server = new PeerBanHelperServer(pbhServerAddress,
                    YamlConfiguration.loadConfiguration(new File(configDirectory, "profile.yml")), mainConfig);
        } catch (Exception e) {
            log.error(Lang.BOOTSTRAP_FAILED, e);
            throw new RuntimeException(e);
        }
        guiManager.onPBHFullyStarted(server);
        setupShutdownHook();
        guiManager.sync();
    }

    private static void setupLog4j2() {
        PluginManager.addPackage("com.ghostchu.peerbanhelper.log4j2");
    }

    private static YamlConfiguration loadConfiguration(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.getKeys(false).isEmpty()) {
            log.error(Lang.CONFIGURATION_INVALID, file);
            guiManager.createDialog(Level.SEVERE, Lang.CONFIGURATION_INVALID_TITLE, String.format(Lang.CONFIGURATION_INVALID_DESCRIPTION, file));
            System.exit(1);
        }
        return config;
    }

    private static void setupConfiguration() {
        log.info(Lang.LOADING_CONFIG);
        try {
            if (!initConfiguration()) {
                guiManager.showConfigurationSetupDialog();
                System.exit(0);
            }
        } catch (IOException e) {
            log.error(Lang.ERR_SETUP_CONFIGURATION, e);
            System.exit(0);
        }
    }

    private static void setupShutdownHook() {
        Thread shutdownThread = new Thread(() -> {
            try {
                log.info(Lang.PBH_SHUTTING_DOWN);
                eventBus.post(new PBHShutdownEvent());
                server.shutdown();
                guiManager.close();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        });
        shutdownThread.setDaemon(false);
        shutdownThread.setName("ShutdownThread");
        Runtime.getRuntime().addShutdownHook(shutdownThread);
    }

    private static void initGUI(String[] args) {
        boolean useJavaFx = Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("javafx"));
        if (Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("nogui"))
                || !Desktop.isDesktopSupported() || System.getProperty("pbh.nogui") != null) {
            guiManager = new PBHGuiManager(new ConsoleGuiImpl(args));
        } else {
            if (useJavaFx) {
                guiManager = new PBHGuiManager(new JavaFxImpl(args));
            } else {
                guiManager = new PBHGuiManager(new SwingGuiImpl(args));
            }
        }
        guiManager.setup();
    }

    private static void initBuildMeta() {
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
        log.info(Lang.MOTD, meta.getVersion());
    }

    private static void handleCommand(String input) {

    }

    private static boolean initConfiguration() throws IOException {
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
        }
        if (!configDirectory.isDirectory()) {
            throw new IllegalStateException(Lang.ERR_CONFIG_DIRECTORY_INCORRECT);
        }
        if (!pluginDirectory.exists()) {
            pluginDirectory.mkdirs();
        }
        boolean exists = true;
        File config = new File(configDirectory, "config.yml");
        File profile = new File(configDirectory, "profile.yml");
        if (!config.exists()) {
            exists = false;
            Files.copy(Main.class.getResourceAsStream("/config.yml"), config.toPath());
        }
        if (!profile.exists()) {
            exists = false;
            Files.copy(Main.class.getResourceAsStream("/profile.yml"), profile.toPath());
        }
        return exists;
    }

    public static String getUserAgent() {
        return "PeerBanHelper/" + meta.getVersion() + " BTN-Protocol/0.0.0-dev";
    }

}