package com.ghostchu.peerbanhelper;

import com.alessiodp.libby.LibraryManager;
import com.alessiodp.libby.logging.LogLevel;
import com.ghostchu.peerbanhelper.config.MainConfigUpdateScript;
import com.ghostchu.peerbanhelper.config.PBHConfigUpdater;
import com.ghostchu.peerbanhelper.config.ProfileUpdateScript;
import com.ghostchu.peerbanhelper.event.PBHShutdownEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiManager;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.javafx.JavaFxImpl;
import com.ghostchu.peerbanhelper.gui.impl.swing.SwingGuiImpl;
import com.ghostchu.peerbanhelper.util.PBHLibrariesLoader;
import com.ghostchu.peerbanhelper.util.Slf4jLogAppender;
import com.ghostchu.simplereloadlib.ReloadManager;
import com.google.common.eventbus.EventBus;
import com.google.common.io.ByteStreams;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class Main {
    @Getter
    private static final EventBus eventBus = new EventBus();
    @Getter
    private static final ReloadManager reloadManager = new ReloadManager();
    public static String DEF_LOCALE = Locale.getDefault().toLanguageTag();
    @Getter
    private static File dataDirectory;
    @Getter
    private static File logsDirectory;
    @Getter
    private static File configDirectory;
    private static File pluginDirectory;
    private static File libraryDirectory;
    @Getter
    private static File debugDirectory;
    @Getter
    private static PeerBanHelperServer server;
    @Getter
    private static PBHGuiManager guiManager;
    @Getter
    private static File mainConfigFile;
    @Getter
    private static File profileConfigFile;
    @Getter
    private static LibraryManager libraryManager;
    @Getter
    private static PBHLibrariesLoader librariesLoader;
    @Getter
    private static AnnotationConfigApplicationContext applicationContext;
    @Getter
    private static String pbhServerAddress;
    @Getter
    private static YamlConfiguration mainConfig;
    @Getter
    private static YamlConfiguration profileConfig;
    @Getter
    private static BuildMeta meta;
    @Getter
    private static String[] startupArgs;

    public static void main(String[] args) {
        startupArgs = args;
        setupConfDirectory(args);
        setupLog4j2();
        Path librariesPath = dataDirectory.toPath().toAbsolutePath().resolve("libraries");
        libraryManager = new PBHLibraryManager(
                new Slf4jLogAppender(),
                Main.getDataDirectory().toPath(), "libraries"
        );
        libraryManager.setLogLevel(LogLevel.ERROR);
        librariesLoader = new PBHLibrariesLoader(libraryManager, librariesPath);
        meta = buildMeta();
        setupConfiguration();
        mainConfigFile = new File(configDirectory, "config.yml");
        mainConfig = loadConfiguration(mainConfigFile);
        new PBHConfigUpdater(mainConfigFile, mainConfig, Main.class.getResourceAsStream("/config.yml")).update(new MainConfigUpdateScript(mainConfig));
        profileConfigFile = new File(configDirectory, "profile.yml");
        profileConfig = loadConfiguration(profileConfigFile);
        new PBHConfigUpdater(profileConfigFile, profileConfig, Main.class.getResourceAsStream("/profile.yml")).update(new ProfileUpdateScript(profileConfig));
        log.info("Current system language tag: {}", Locale.getDefault().toLanguageTag());
        DEF_LOCALE = mainConfig.getString("language");
        if (DEF_LOCALE == null || DEF_LOCALE.equalsIgnoreCase("default")) {
            DEF_LOCALE = System.getenv("PBH_USER_LOCALE");
            if(DEF_LOCALE == null) {
                DEF_LOCALE = Locale.getDefault().toLanguageTag();
            }
        }
        DEF_LOCALE = DEF_LOCALE.toLowerCase(Locale.ROOT).replace("-", "_");
        initGUI(args);
        guiManager.createMainWindow();
        pbhServerAddress = mainConfig.getString("server.prefix", "http://127.0.0.1:" + mainConfig.getInt("server.http"));
        setupProxySettings();
        try {
            log.info("Loading application context, this may need a while on low-end devices, please wait...");
            applicationContext = new AnnotationConfigApplicationContext();
            applicationContext.register(AppConfig.class);
            applicationContext.refresh();
            registerBean(File.class, mainConfigFile, "mainConfigFile");
            registerBean(File.class, profileConfigFile, "profileConfigFile");
            registerBean(YamlConfiguration.class, mainConfig, "mainConfig");
            registerBean(YamlConfiguration.class, profileConfig, "profileConfig");
            server = applicationContext.getBean(PeerBanHelperServer.class);
            server.start();
        } catch (Exception e) {
            log.error("Failed to startup PeerBanHelper, FATAL ERROR", e);
            throw new RuntimeException(e);
        }
        guiManager.onPBHFullyStarted(server);
        setupShutdownHook();
        guiManager.sync();
    }

    private static void setupProxySettings() {
        var proxySection = mainConfig.getConfigurationSection("proxy");
        if (proxySection == null) return;
        String host = proxySection.getString("host");
        String port = String.valueOf(proxySection.getInt("port"));
        String nonProxyHost = proxySection.getString("non-proxy-hosts", "");
        switch (proxySection.getInt("setting")) {
            case 1 -> System.setProperty("java.net.useSystemProxies", "true");
            case 2 -> {
                System.setProperty("http.proxyHost", host);
                System.setProperty("http.proxyPort", port);
                System.setProperty("https.proxyHost", host);
                System.setProperty("https.proxyPort", port);
                System.setProperty("http.nonProxyHosts", nonProxyHost);
                System.setProperty("https.nonProxyHosts", nonProxyHost);
            }
            case 3 -> {
                System.setProperty("socksProxyHost", host);
                System.setProperty("socksProxyPort", port);
                System.setProperty("socksNonProxyHosts", nonProxyHost);
            }
            default -> System.setProperty("java.net.useSystemProxies", "false");
        }
    }

    private static void setupConfDirectory(String[] args) {
        String osName = System.getProperty("os.name");
        String root = "data";
        if ("true".equalsIgnoreCase(System.getProperty("pbh.usePlatformConfigLocation"))) {
            if (osName.contains("Windows")) {
                root = new File(System.getenv("LOCALAPPDATA"), "PeerBanHelper").getAbsolutePath();
            } else {
                root = new File(new File(System.getProperty("user.home"), ".config"), "PeerBanHelper").getAbsolutePath();
            }
        }
        if (System.getProperty("pbh.datadir") != null) {
            root = System.getProperty("pbh.datadir");
        }

        dataDirectory = new File(root);
        logsDirectory = new File(dataDirectory, "logs");
        configDirectory = new File(dataDirectory, "config");
        pluginDirectory = new File(dataDirectory, "plugins");
        libraryDirectory = new File(dataDirectory, "libraries");
        debugDirectory = new File(dataDirectory, "debug");
    }

    private static void setupLog4j2() {
        PluginManager.addPackage("com.ghostchu.peerbanhelper.log4j2");
    }

    private static YamlConfiguration loadConfiguration(File file) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.getOptions()
                .setParseComments(true)
                .setWidth(1000);
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            log.error("Unable to load configuration: invalid YAML configuration // 无法加载配置文件：无效的 YAML 配置，请检查是否有语法错误", e);
            JOptionPane.showMessageDialog(null, "Invalid/Corrupted YAML configuration | 无效或损坏的 YAML 配置文件", String.format("Failed to read configuration: %s", file), JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        return configuration;
    }

    private static void setupConfiguration() {
        log.info("Loading configuration...");
        try {
            initConfiguration();
            //guiManager.showConfigurationSetupDialog();
            //System.exit(0);
        } catch (IOException e) {
            log.error("Unable to load configuration, something went wrong!", e);
            System.exit(0);
        }
    }

    private static BuildMeta buildMeta() {
        var meta = new BuildMeta();
        try (InputStream stream = Main.class.getResourceAsStream("/build-info.yml")) {
            if (stream == null) {
                log.error("Error: Unable to load build metadata from JAR/build-info.yml: Bundled resources not exists");
            } else {
                String str = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                YamlConfiguration configuration = new YamlConfiguration();
                configuration.loadFromString(str);
                meta.loadBuildMeta(configuration);
            }
        } catch (IOException | InvalidConfigurationException e) {
            log.error("Error: Unable to load build metadata from JAR/build-info.yml", e);
        }
        return meta;
    }

    private static void setupShutdownHook() {
        Thread shutdownThread = new Thread(() -> {
            try {
                log.info("Shutting down...");
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
        String guiType = "javafx";
        if (!Desktop.isDesktopSupported() || System.getProperty("pbh.nogui") != null || Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("nogui"))) {
            guiType = "console";
        } else if (Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("swing"))) {
            guiType = "swing";
        }
        if ("javafx".equals(guiType)) {
            try {
                if (!loadDependencies("/libraries/javafx.maven")) {
                    guiType = "swing";
                }
            } catch (IOException e) {
                log.error("Failed to load JavaFx dependencies", e);
                guiType = "swing";
            }
        }
        switch (guiType) {
            case "javafx" -> guiManager = new PBHGuiManager(new JavaFxImpl(args));
            case "swing" -> guiManager = new PBHGuiManager(new SwingGuiImpl(args));
            case "console" -> guiManager = new PBHGuiManager(new ConsoleGuiImpl(args));
        }
        guiManager.setup();
    }

    public static String getUserAgent() {
        return "PeerBanHelper/" + meta.getVersion() + " BTN-Protocol/0.0.1";
    }

    public static boolean loadDependencies(String mavenManifestPath) throws IOException {
        try (var is = Main.class.getResourceAsStream(mavenManifestPath)) {
            String str = new String(ByteStreams.toByteArray(is), StandardCharsets.UTF_8);
            String[] libraries = str.split("\n");
            String osName = System.getProperty("os.name").toLowerCase();
            String sysArch = "win";
            if (osName.contains("linux")) {
                sysArch = "linux";
            } else if (osName.contains("mac")) {
                sysArch = "mac";
            }
            try {
                librariesLoader.loadLibraries(Arrays.stream(libraries).toList(),
                        Map.of("system.platform", sysArch, "javafx.version",
                                Main.getMeta().getJavafx()));
                return true;
            } catch (Exception e) {
                log.error("Unable to load JavaFx dependencies", e);
                return false;
            }
        }
    }


    private static void handleCommand(String input) {

    }

    private static boolean initConfiguration() throws IOException {
        log.info("PeerBanHelper data directory: {}", dataDirectory.getAbsolutePath());
        if (!dataDirectory.exists()) {
            configDirectory.mkdirs();
        }
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
        }
        if (!configDirectory.isDirectory()) {
            throw new IllegalStateException("The path " + configDirectory.getAbsolutePath() + " should be a directory but found a file.");
        }
        if (!pluginDirectory.exists()) {
            pluginDirectory.mkdirs();
        }
        if (!debugDirectory.exists()) {
            debugDirectory.mkdirs();
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

    public static String decapitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1)) &&
                Character.isUpperCase(name.charAt(0))) {
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    public static <T> void registerBean(Class<T> clazz, @Nullable String beanName) {
        if (beanName == null) {
            beanName = decapitalize(clazz.getSimpleName());
        }
        if (applicationContext.containsBean(beanName)) {
            return;
        } else {
            String bn = decapitalize(clazz.getSimpleName());
            if (applicationContext.containsBean(bn)) {
                return;
            }
        }
        ConfigurableApplicationContext configurableApplicationContext = applicationContext;
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) configurableApplicationContext.getBeanFactory();
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        defaultListableBeanFactory.registerBeanDefinition(beanName, beanDefinitionBuilder.getRawBeanDefinition());
    }

    public static <T> void registerBean(Class<T> clazz, T instance, @Nullable String beanName) {
        if (beanName == null) {
            beanName = decapitalize(clazz.getSimpleName());
        }
        if (applicationContext.containsBean(beanName)) {
            return;
        } else {
            String bn = decapitalize(clazz.getSimpleName());
            if (applicationContext.containsBean(bn)) {
                return;
            }
        }
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) applicationContext.getBeanFactory();
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz, () -> instance);
        defaultListableBeanFactory.registerBeanDefinition(beanName, beanDefinitionBuilder.getRawBeanDefinition());
    }

    public static void unregisterBean(String beanName) {
        ConfigurableApplicationContext configurableApplicationContext = applicationContext;
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) configurableApplicationContext.getBeanFactory();
        defaultListableBeanFactory.removeBeanDefinition(beanName);
    }

}