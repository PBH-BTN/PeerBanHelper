package com.ghostchu.peerbanhelper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import com.ghostchu.peerbanhelper.config.MainConfigUpdateScript;
import com.ghostchu.peerbanhelper.config.PBHConfigUpdater;
import com.ghostchu.peerbanhelper.config.ProfileUpdateScript;
import com.ghostchu.peerbanhelper.event.PBHShutdownEvent;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.gui.PBHGuiManager;
import com.ghostchu.peerbanhelper.gui.TaskbarState;
import com.ghostchu.peerbanhelper.gui.impl.console.ConsoleGuiImpl;
import com.ghostchu.peerbanhelper.gui.impl.swing.SwingGuiImpl;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TextManager;
import com.ghostchu.peerbanhelper.util.*;
import com.ghostchu.peerbanhelper.util.encrypt.RSAUtils;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.simplereloadlib.ReloadManager;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.google.common.eventbus.EventBus;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.EvalMode;
import com.googlecode.aviator.Options;
import com.googlecode.aviator.runtime.JavaMethodReflectionFunctionMissing;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import oshi.SystemInfo;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.math.MathContext;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public class Main {
    @Getter
    private static final EventBus eventBus = new EventBus();
    @Getter
    private static final ReloadManager reloadManager = new ReloadManager();
    public static String DEF_LOCALE = Locale.getDefault().toLanguageTag().toLowerCase(Locale.ROOT).replace("-", "_");
    @Getter
    private static File dataDirectory;
    @Getter
    private static File logsDirectory;
    @Getter
    private static File configDirectory;
    @Getter
    private static File pluginDirectory;
    @Getter
    private static File debugDirectory;
    @Getter
    private static PeerBanHelper server;
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
    private static String userAgent;
    public static final int PBH_BTN_PROTOCOL_IMPL_VERSION = 12;
    public static final String PBH_BTN_PROTOCOL_READABLE_VERSION = "2.0.0";
    private static PluginManager pluginManager;
    private static long bootSince;

    public static void main(String[] args) {
        bootSince = System.currentTimeMillis();
        startupArgs = args;
        setupReloading();
        setupConfDirectory(args);
        loadFlagsProperties();
        setupConfiguration();
        meta = buildMeta();
        setupLogback();
        String defLocaleTag = Locale.getDefault().getLanguage() + "-" + Locale.getDefault().getCountry();
        log.info("Current system language tag: {}", defLocaleTag);
        DEF_LOCALE = mainConfig.getString("language");
        if (DEF_LOCALE == null || DEF_LOCALE.equalsIgnoreCase("default")) {
            DEF_LOCALE = ExternalSwitch.parse("pbh.userLocale");
            if (DEF_LOCALE == null) {
                DEF_LOCALE = defLocaleTag;
            }
        }
        initGUI(args);
        Thread.ofPlatform().name("Bootstrap").start(() -> {
            guiManager.taskbarControl().updateProgress(null, TaskbarState.INDETERMINATE, 0.0f);
            pbhServerAddress = mainConfig.getString("server.prefix", "http://127.0.0.1:" + mainConfig.getInt("server.http"));
            setupScriptEngine();
            try {
                log.info(tlUI(Lang.SPRING_CONTEXT_LOADING));
                applicationContext = new AnnotationConfigApplicationContext();
                applicationContext.register(AppConfig.class);
                applicationContext.refresh();
                server = applicationContext.getBean(PeerBanHelper.class);
                server.start();
                log.info(tlUI(Lang.BOOT_TIME, System.currentTimeMillis() - bootSince));
            } catch (Exception e) {
                log.error(tlUI(Lang.PBH_STARTUP_FATAL_ERROR), e);
                throw new RuntimeException(e);
            }
            setupShutdownHook();
        });
        guiManager.sync();

    }

    private static void loadFlagsProperties() {
        try {
            var flags = new File(dataDirectory, "flags.properties");
            if (flags.exists()) {
                try (var is = Files.newInputStream(flags.toPath())) {
                    Properties properties = new Properties();
                    properties.load(is);
                    System.getProperties().putAll(properties);
                    log.info("Loaded {} property from data/flags.properties.", properties.size());
                }
            }
        } catch (IOException e) {
            log.error("Unable to load flags.properties", e);
        }
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
            var targetLevel = ExternalSwitch.parse("pbh.log.level");
            if (targetLevel != null) {
                ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
                rootLogger.setLevel(Level.toLevel(targetLevel));
            }
        } catch (Throwable e) {
            log.warn("Failed to set log level", e);
        }

    }

    public static ReloadResult reloadModule() {
        setupConfiguration();
        loadFlagsProperties();
        return ReloadResult.builder().status(ReloadStatus.SUCCESS).reason("OK!").build();
    }

    private static void setupConfDirectory(String[] args) {
        String osName = System.getProperty("os.name");
        String root = "data";
        if (ExternalSwitch.parseBoolean("pbh.usePlatformConfigLocation")) {
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
        if (ExternalSwitch.parse("pbh.datadir") != null) {
            root = ExternalSwitch.parse("pbh.datadir");
        }

        dataDirectory = new File(root);
        logsDirectory = new File(dataDirectory, "logs");
        configDirectory = new File(dataDirectory, "config");
        pluginDirectory = new File(dataDirectory, "plugins");
        debugDirectory = new File(dataDirectory, "debug");
        if (ExternalSwitch.parse("pbh.configdir") != null) {
            configDirectory = new File(ExternalSwitch.parse("pbh.configdir"));
        }
        if (ExternalSwitch.parse("pbh.logsdir") != null) {
            logsDirectory = new File(ExternalSwitch.parse("pbh.logsdir"));
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
            if (!Desktop.isDesktopSupported() || ExternalSwitch.parse("pbh.nogui") != null || Arrays.stream(startupArgs).anyMatch(arg -> arg.equalsIgnoreCase("nogui"))) {
                try {
                    log.error("Bad configuration:  {}", Files.readString(file.toPath()));
                } catch (IOException ex) {
                    log.error("Unable to output the bad configuration content", ex);
                }
                log.error("Unable to load configuration: invalid YAML configuration // 无法加载配置文件：无效的 YAML 配置，请检查是否有语法错误", e);
            } else {
                JOptionPane.showMessageDialog(null, "Invalid/Corrupted YAML configuration | 无效或损坏的 YAML 配置文件", String.format("Failed to read configuration: %s", file), JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
        }
        return configuration;
    }

    public static void setupConfiguration() {
        log.info("Loading configuration...");
        try {
            initConfiguration();
            mainConfigFile = new File(configDirectory, "config.yml");
            mainConfig = loadConfiguration(mainConfigFile);
            new PBHConfigUpdater(mainConfigFile, mainConfig, Main.class.getResourceAsStream("/config.yml")).update(new MainConfigUpdateScript(mainConfig));
            profileConfigFile = new File(configDirectory, "profile.yml");
            profileConfig = loadConfiguration(profileConfigFile);
            new PBHConfigUpdater(profileConfigFile, profileConfig, Main.class.getResourceAsStream("/profile.yml")).update(new ProfileUpdateScript(profileConfig));
            //guiManager.showConfigurationSetupDialog();
            //System.exit(0);
        } catch (IOException e) {
            log.error("Unable to load configuration, something went wrong!", e);
            System.exit(0);
        }
    }

    private static BuildMeta buildMeta() {
        var meta = new BuildMeta();
        try (InputStream stream = Main.class.getResourceAsStream("/git.properties")) {
            if (stream == null) {
                log.error("Error: Unable to load build metadata from JAR/git.properties: Bundled resources not exists");
            } else {
                Properties properties = new Properties();
                properties.load(stream);
                meta.loadBuildMeta(properties);
            }
        } catch (IOException e) {
            log.error("Error: Unable to load build metadata from <JAR>/git.properties", e);
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
        String guiType = mainConfig.getString("gui", "auto");
        if ("auto".equals(guiType)) guiType = "swing";

        if (Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("swing"))) {
            guiType = "swing";
        } else if (Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("swt"))) {
            guiType = "swt";
        } else if (Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("qt"))) {
            guiType = "qt";
        }
        if (!Desktop.isDesktopSupported() || ExternalSwitch.parse("pbh.nogui") != null || Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("nogui"))) {
            guiType = "console";
        }

        switch (guiType) {
            //case "qt" -> guiManager = new PBHGuiManager(new com.ghostchu.peerbanhelper.gui.impl.qt.QtGuiImpl(args));
            case "console" -> guiManager = new PBHGuiManager(new ConsoleGuiImpl(args));
            default -> guiManager = new PBHGuiManager(new SwingGuiImpl(args));
        }

        guiManager.setup();
    }

    public static String getUserAgent() {
        if (userAgent != null) return userAgent;
        String userAgentTemplate = "PeerBanHelper/%s (%s; %s,%s,%s) BTN-Protocol/%s BTN-Protocol-Version/%s";
        var osMXBean = ManagementFactory.getOperatingSystemMXBean();
        String release = ExternalSwitch.parse("pbh.release");
        if (release == null) {
            release = "unknown";
        }
        String os = osMXBean.getName();
        String osVersion = osMXBean.getVersion();
        String buildNumber = "unknown";
        String codeName = "";
        try {
            SystemInfo info = new SystemInfo();
            var verInfo = info.getOperatingSystem().getVersionInfo();
            buildNumber = verInfo.getBuildNumber();
            codeName = verInfo.getCodeName();
        } catch (Throwable ignored) {
        }
        userAgent = String.format(userAgentTemplate, meta.getVersion(), release, os, osVersion, codeName + buildNumber, PBH_BTN_PROTOCOL_READABLE_VERSION, PBH_BTN_PROTOCOL_IMPL_VERSION);
        return userAgent;
    }

    private static void handleCommand(String input) {

    }

    private static boolean initConfiguration() throws IOException {
        log.info("PeerBanHelper data directory: {}", dataDirectory.getAbsolutePath());
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }
        if (!configDirectory.exists()) {
            configDirectory.mkdirs();
        }
        if (!configDirectory.isDirectory()) {
            configDirectory.delete();
            configDirectory.mkdirs();
            if (!configDirectory.isDirectory()) {
                throw new IllegalStateException("The path " + configDirectory.getAbsolutePath() + " should be a directory but found a file, auto fix failed.");
            }
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
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

//    public static <T> void registerBean(Class<T> clazz, @Nullable String beanName) {
//        if (beanName == null) {
//            beanName = decapitalize(clazz.getSimpleName());
//        }
//        if (applicationContext.containsBean(beanName)) {
//            return;
//        } else {
//            String bn = decapitalize(clazz.getSimpleName());
//            if (applicationContext.containsBean(bn)) {
//                return;
//            }
//        }
//        ConfigurableApplicationContext configurableApplicationContext = applicationContext;
//        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) configurableApplicationContext.getBeanFactory();
//        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
//        defaultListableBeanFactory.registerBeanDefinition(beanName, beanDefinitionBuilder.getRawBeanDefinition());
//    }
//
//    public static <T> void registerBean(Class<T> clazz, T instance, @Nullable String beanName) {
//        if (beanName == null) {
//            beanName = decapitalize(clazz.getSimpleName());
//        }
//        if (applicationContext.containsBean(beanName)) {
//            return;
//        } else {
//            String bn = decapitalize(clazz.getSimpleName());
//            if (applicationContext.containsBean(bn)) {
//                return;
//            }
//        }
//        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) applicationContext.getBeanFactory();
//        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz, () -> instance);
//        defaultListableBeanFactory.registerBeanDefinition(beanName, beanDefinitionBuilder.getRawBeanDefinition());
//    }
//
//    public static void unregisterBean(String beanName) {
//        ConfigurableApplicationContext configurableApplicationContext = applicationContext;
//        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) configurableApplicationContext.getBeanFactory();
//        defaultListableBeanFactory.removeBeanDefinition(beanName);
//    }

    private static void setupScriptEngine() {
        AviatorEvaluator.getInstance().setCachedExpressionByDefault(true);
        // ASM 性能优先
        AviatorEvaluator.getInstance().setOption(Options.EVAL_MODE, EvalMode.ASM);
        // EVAL 性能优先
        AviatorEvaluator.getInstance().setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL);
        // 降低浮点计算精度
        AviatorEvaluator.getInstance().setOption(Options.MATH_CONTEXT, MathContext.DECIMAL32);
        // 启用变量语法糖
        AviatorEvaluator.getInstance().setOption(Options.ENABLE_PROPERTY_SYNTAX_SUGAR, true);
//        // 表达式允许序列化和反序列化
//        AviatorEvaluator.getInstance().setOption(Options.SERIALIZABLE, true);
        // 启用反射方法查找
        AviatorEvaluator.getInstance().setFunctionMissing(JavaMethodReflectionFunctionMissing.getInstance());
        // 注册反射调用
        registerFunctions(IPAddressUtil.class);
        registerFunctions(HTTPUtil.class);
        registerFunctions(JsonUtil.class);
        registerFunctions(Lang.class);
        registerFunctions(StrUtil.class);
        registerFunctions(PeerBanHelper.class);
        registerFunctions(InfoHashUtil.class);
        registerFunctions(CommonUtil.class);
        registerFunctions(ByteUtil.class);
        registerFunctions(MiscUtil.class);
        registerFunctions(MsgUtil.class);
        registerFunctions(SharedObject.class);
        registerFunctions(UrlEncoderDecoder.class);
        registerFunctions(URLUtil.class);
        registerFunctions(WebUtil.class);
        registerFunctions(RSAUtils.class);
        registerFunctions(Pageable.class);
        registerFunctions(TextManager.class);
        registerFunctions(ExchangeMap.class);
        registerFunctions(Main.class);
    }

    private static void registerFunctions(Class<?> clazz) {
        try {
            AviatorEvaluator.addInstanceFunctions(StrUtil.uncapitalize(clazz.getSimpleName()), clazz);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            log.error("Internal error: failed on register instance functions: {}", clazz.getName(), e);
        }
        try {
            AviatorEvaluator.addStaticFunctions(StrUtil.capitalize(clazz.getSimpleName()), clazz);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            log.error("Internal error: failed on register static functions: {}", clazz.getName(), e);
        }
    }


}