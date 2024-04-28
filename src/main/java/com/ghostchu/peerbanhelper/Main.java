package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.config.MainConfigUpdateScript;
import com.ghostchu.peerbanhelper.config.PBHConfigUpdater;
import com.ghostchu.peerbanhelper.config.ProfileUpdateScript;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.downloader.impl.qbittorrent.QBittorrent;
import com.ghostchu.peerbanhelper.downloader.impl.transmission.Transmission;
import com.ghostchu.peerbanhelper.event.PBHShutdownEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiManager;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.swing.SwingGuiImpl;
import com.ghostchu.peerbanhelper.text.Lang;
import com.google.common.eventbus.EventBus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
    private static PBHGuiManager guiManager;
    @Getter
    private static final EventBus eventBus = new EventBus();

    public static void main(String[] args) {
        initBuildMeta();
        initGUI(args);
        setupConfiguration();
        List<Downloader> downloaderList = new ArrayList<>();
        guiManager.createMainWindow();
        File mainConfigFile = new File(configDirectory, "config.yml");
        YamlConfiguration mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile);
        new PBHConfigUpdater(mainConfigFile, mainConfig).update(new MainConfigUpdateScript(mainConfig));
        File profileConfigFile = new File(configDirectory, "profile.yml");
        YamlConfiguration profileConfig = YamlConfiguration.loadConfiguration(profileConfigFile);
        new PBHConfigUpdater(profileConfigFile, profileConfig).update(new ProfileUpdateScript(profileConfig));
        String pbhServerAddress = mainConfig.getString("server.prefix", "http://127.0.0.1:" + mainConfig.getInt("server.http"));
        ConfigurationSection clientSection = mainConfig.getConfigurationSection("client");
        for (String client : clientSection.getKeys(false)) {
            ConfigurationSection downloaderSection = clientSection.getConfigurationSection(client);
            String endpoint = downloaderSection.getString("endpoint");
            String username = downloaderSection.getString("username");
            String password = downloaderSection.getString("password");
            String baUser = downloaderSection.getString("basic-auth.user");
            String baPass = downloaderSection.getString("basic-auth.pass");
            String httpVersion = downloaderSection.getString("http-version", "HTTP_1_1");
            boolean incrementBan = downloaderSection.getBoolean("increment-ban");
            String rpcUrl = downloaderSection.getString("rpc-url");
            HttpClient.Version httpVersionEnum;
            try {
                httpVersionEnum = HttpClient.Version.valueOf(httpVersion);
            } catch (IllegalArgumentException e) {
                httpVersionEnum = HttpClient.Version.HTTP_1_1;
            }
            boolean verifySSL = downloaderSection.getBoolean("verify-ssl", true);
            switch (downloaderSection.getString("type").toLowerCase(Locale.ROOT)) {
                case "qbittorrent" -> {
                    downloaderList.add(new QBittorrent(client, endpoint, username, password, baUser, baPass, verifySSL, httpVersionEnum, incrementBan));
                    log.info(Lang.DISCOVER_NEW_CLIENT, "qBittorrent", client, endpoint);
                }
                case "transmission" -> {
                    downloaderList.add(new Transmission(client, endpoint, username, password, pbhServerAddress + "/blocklist/transmission", verifySSL, httpVersionEnum, rpcUrl));
                    log.info(Lang.DISCOVER_NEW_CLIENT, "Transmission", client, endpoint);
                }
            }
        }
        try {
            server = new PeerBanHelperServer(downloaderList,
                    YamlConfiguration.loadConfiguration(new File(configDirectory, "profile.yml")), mainConfig);
        } catch (Exception e) {
            log.error(Lang.BOOTSTRAP_FAILED, e);
            throw new RuntimeException(e);
        }
        guiManager.onPBHFullyStarted(server);
        setupShutdownHook();
        guiManager.sync();
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
        if (Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("nogui"))
                || !Desktop.isDesktopSupported()) {
            guiManager = new PBHGuiManager(new ConsoleGuiImpl());
        } else {
            guiManager = new PBHGuiManager(new SwingGuiImpl());
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