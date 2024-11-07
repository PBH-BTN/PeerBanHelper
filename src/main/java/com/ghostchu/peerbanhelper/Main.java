package com.ghostchu.peerbanhelper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import com.ghostchu.peerbanhelper.config.MainConfigUpdateScript;
import com.ghostchu.peerbanhelper.config.PBHConfigUpdater;
import com.ghostchu.peerbanhelper.config.ProfileUpdateScript;
import com.ghostchu.peerbanhelper.event.PBHShutdownEvent;
import com.ghostchu.peerbanhelper.gui.PBHGuiManager;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.swing.SwingGuiImpl;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TextManager;
import com.ghostchu.simplereloadlib.ReloadManager;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.google.common.eventbus.EventBus;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Arrays;
import java.util.Locale;

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
    //    @Getter
//    private static LibraryManager libraryManager;
//    @Getter
//    private static PBHLibrariesLoader librariesLoader;
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
    @Getter
    private static long startupAt = System.currentTimeMillis();

    public static void main(String[] args) {
        startupArgs = args;
        setupConfDirectory(args);
        setupLogback();
        meta = buildMeta();
        setupConfiguration();
        mainConfigFile = new File(configDirectory, "config.yml");
        mainConfig = loadConfiguration(mainConfigFile);
        new PBHConfigUpdater(mainConfigFile, mainConfig, Main.class.getResourceAsStream("/config.yml")).update(new MainConfigUpdateScript(mainConfig));
        profileConfigFile = new File(configDirectory, "profile.yml");
        profileConfig = loadConfiguration(profileConfigFile);
        new PBHConfigUpdater(profileConfigFile, profileConfig, Main.class.getResourceAsStream("/profile.yml")).update(new ProfileUpdateScript(profileConfig));
        String defLocaleTag = Locale.getDefault().getLanguage() + "-" + Locale.getDefault().getCountry();
        log.info("Current system language tag: {}", defLocaleTag);
        DEF_LOCALE = mainConfig.getString("language");
        if (DEF_LOCALE == null || DEF_LOCALE.equalsIgnoreCase("default")) {
            DEF_LOCALE = System.getenv("PBH_USER_LOCALE");
            if (DEF_LOCALE == null) {
                DEF_LOCALE = defLocaleTag;
            }
        }
        DEF_LOCALE = DEF_LOCALE.toLowerCase(Locale.ROOT).replace("-", "_");
        initGUI(args);
        guiManager.createMainWindow();
        pbhServerAddress = mainConfig.getString("server.prefix", "http://127.0.0.1:" + mainConfig.getInt("server.http"));
        setupProxySettings();
        try {
            log.info(TextManager.tlUI(Lang.SPRING_CONTEXT_LOADING));
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
            log.error(TextManager.tlUI(Lang.PBH_STARTUP_FATAL_ERROR), e);
            throw new RuntimeException(e);
        }
        guiManager.onPBHFullyStarted(server);
        setupShutdownHook();
        setupReloading();
        guiManager.sync();
    }

    @SneakyThrows
    private static void setupReloading() {
        reloadManager.register(Main.class.getDeclaredMethod("reloadModule"));
    }

    public static void setupLogback() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        RollingFileAppender<?> appender = (RollingFileAppender<?>) loggerContext.getLogger("ROOT").getAppender("FILE");

        if (appender != null) {
            appender.stop(); // 停止当前 appender
            appender.setFile(new File(logsDirectory, "latest.log").getAbsolutePath()); // 设置新的文件路径
            // 更新滚动策略
            SizeAndTimeBasedRollingPolicy<?> policy = (SizeAndTimeBasedRollingPolicy<?>) appender.getRollingPolicy();
            policy.setFileNamePattern(logsDirectory.getAbsolutePath() + "/%d{yyyy-MM-dd}-%i.log.gz"); // 更新文件名模式
            policy.start(); // 启动滚动策略

            appender.start(); // 启动 appender
        }

        try {
            var targetLevel = System.getProperty("pbh.log.level");
            if (targetLevel != null) {
                var rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
                ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) rootLogger;
                logbackLogger.setLevel(Level.toLevel(targetLevel));
            }
        } catch (Throwable ignored) {
        }
    }

    public static ReloadResult reloadModule() {
        setupProxySettings();
        ;
        return ReloadResult.builder().status(ReloadStatus.SUCCESS).reason("OK!").build();
    }

    private static void setupProxySettings() {
        var proxySection = mainConfig.getConfigurationSection("proxy");
        if (proxySection == null) return;
        String host = proxySection.getString("host");
        String port = String.valueOf(proxySection.getInt("port"));
        String nonProxyHost = proxySection.getString("non-proxy-hosts", "");

        // 在设置新的代理属性之前，移除所有现有的设定
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("http.nonProxyHosts");
        System.clearProperty("https.nonProxyHosts");
        System.clearProperty("java.net.useSystemProxies");

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
                var dataDirectory = new File(System.getProperty("user.home")).toPath();
                if (osName.contains("mac")) {
                    dataDirectory = dataDirectory.resolve("/Library/Application Support");
                } else {
                    dataDirectory = dataDirectory.resolve(".config");
                }
                root = dataDirectory.resolve("PeerBanHelper").toAbsolutePath().toString();
            }
        }
        if (System.getProperty("pbh.datadir") != null) {
            root = System.getProperty("pbh.datadir");
        }

        dataDirectory = new File(root);
        logsDirectory = new File(dataDirectory, "logs");
        configDirectory = new File(dataDirectory, "config");
        pluginDirectory = new File(dataDirectory, "plugins");
        debugDirectory = new File(dataDirectory, "debug");
        if (System.getProperty("pbh.configdir") != null) {
            configDirectory = new File(System.getProperty("pbh.configdir"));
        }
        if (System.getProperty("pbh.logsdir") != null) {
            logsDirectory = new File(System.getProperty("pbh.logsdir"));
        }
        // other directories aren't allowed to change by user to keep necessary structure
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
        String guiType = "swing";
        if (!Desktop.isDesktopSupported() || System.getProperty("pbh.nogui") != null || Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("nogui"))) {
            guiType = "console";
        } else if (Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("swing"))) {
            guiType = "swing";
        }
        switch (guiType) {
            case "swing" -> guiManager = new PBHGuiManager(new SwingGuiImpl(args));
            case "console" -> guiManager = new PBHGuiManager(new ConsoleGuiImpl(args));
        }
        guiManager.setup();
    }

    public static String getUserAgent() {
        return "PeerBanHelper/" + meta.getVersion() + " BTN-Protocol/0.0.2";
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