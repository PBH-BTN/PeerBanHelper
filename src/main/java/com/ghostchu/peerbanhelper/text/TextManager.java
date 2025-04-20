package com.ghostchu.peerbanhelper.text;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.postprocessor.PostProcessor;
import com.ghostchu.peerbanhelper.text.postprocessor.impl.FillerProcessor;
import com.ghostchu.peerbanhelper.util.URLUtil;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.ghostchu.peerbanhelper.Main.DEF_LOCALE;

@Slf4j
public final class TextManager implements Reloadable {
    public static TextManager INSTANCE_HOLDER = new TextManager();
    public final Set<PostProcessor> postProcessors = new LinkedHashSet<>();
    private final LanguageFilesManager languageFilesManager = new LanguageFilesManager();
    private final Set<String> availableLanguages = new LinkedHashSet<>();
    private final Set<String> loadedLanguages = ConcurrentHashMap.newKeySet();
    private final File langDirectory;
    private final File overrideDirectory;
    private final YamlConfiguration fallbackConfig;

    public TextManager() {
        this.langDirectory = new File(Main.getDataDirectory(), "lang");
        this.overrideDirectory = new File(langDirectory, "overrides");
        if (!this.langDirectory.exists()) {
            this.langDirectory.mkdirs();
        }
        if (!this.overrideDirectory.exists()) {
            this.overrideDirectory.mkdirs();
        }
        this.fallbackConfig = loadBuiltInFallback();
        initializeBasic();
        Main.getReloadManager().register(this);
    }

    public static String tlUI(Lang key, Object... params) {
        return tl(DEF_LOCALE, new TranslationComponent(key.getKey(), (Object[]) TextManager.convert(DEF_LOCALE, params)));
    }

    public static String tlUI(TranslationComponent translationComponent) {
        return tl(DEF_LOCALE, translationComponent);
    }

    public static String tl(String locale, Lang key, Object... params) {
        locale = locale.toLowerCase(Locale.ROOT).replace("-", "_");
        return tl(locale, new TranslationComponent(key.getKey(), (Object[]) convert(locale, params)));
    }

    public static String tl(String locale, TranslationComponent translationComponent) {
        locale = locale.toLowerCase(Locale.ROOT).replace("-", "_");

        // 按需加载语言文件
        if (!INSTANCE_HOLDER.loadedLanguages.contains(locale)) {
            INSTANCE_HOLDER.loadLanguage(locale);
        }

        YamlConfiguration yamlConfiguration = INSTANCE_HOLDER.languageFilesManager.getDistribution(locale);
        if (yamlConfiguration == null) {
            yamlConfiguration = INSTANCE_HOLDER.languageFilesManager.getDistribution("en_us");
            if (yamlConfiguration == null) {
                log.warn("The locale {} are not supported and fallback locale en_us load failed.", locale);
                return "Unsupported locale " + locale;
            }
        }
        if (translationComponent == null) {
            return "null";
        }
        if (translationComponent.getKey().isBlank()) {
            return "";
        }
        String str = yamlConfiguration.getString(translationComponent.getKey());
        if (str == null) {
            str = translationComponent.getKey();
        }
        String[] params = convert(locale, translationComponent.getParams());
        for (PostProcessor postProcessor : INSTANCE_HOLDER.postProcessors) {
            try {
                str = postProcessor.process(str, locale, params);
            } catch (Exception e) {
                log.warn("Unable to process post processor: key={}, locale={}, params={}", translationComponent.getKey(), locale, translationComponent.getParams());
            }
        }
        return str;
    }

    @NotNull
    public static String[] convert(String locale, @Nullable Object... args) {
        if (args == null || args.length == 0) {
            return new String[0];
        }
        String[] components = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            Object obj = args[i];
            if (obj == null) {
                components[i] = "null";
                continue;
            }

            try {
                if (obj instanceof TranslationComponent translationComponent) {
                    components[i] = tl(locale, translationComponent);
                    continue;
                }
                components[i] = obj.toString();

            } catch (Exception exception) {
                log.debug("Failed to process the object: {}", obj);
                components[i] = String.valueOf(obj); // null safe
            }
        }
        return components;
    }

    /**
     * 初始化基本的语言系统
     */
    private void initializeBasic() {
        log.info("Initializing translation system...");
        this.reset();

        // 只加载默认语言(en_us)作为回退
        languageFilesManager.deploy("zh_cn", fallbackConfig);
        loadedLanguages.add("zh_cn");

        // 扫描并记录可用的语言，但不加载它们
        scanAvailableLanguages();

        // 注册后处理器
        postProcessors.add(new FillerProcessor());

        log.info("Translation system initialized with {} available languages", availableLanguages.size());
    }

    /**
     * 扫描可用的语言列表，但不加载它们
     */
    private void scanAvailableLanguages() {
        // 扫描内置语言
        try {
            PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver(Main.class.getClassLoader());
            Resource[] resources = resourcePatternResolver.getResources("classpath:lang/**/*.yml");
            for (Resource res : resources) {
                String langName = URLUtil.getParentName(res.getURI());
                availableLanguages.add(langName.toLowerCase(Locale.ROOT));
            }
        } catch (IOException e) {
            log.warn("Failed to scan bundled translations", e);
        }

        // 扫描覆盖语言
        File[] files = overrideDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    availableLanguages.add(file.getName().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    /**
     * 按需加载特定语言
     *
     * @param locale 要加载的语言代码
     * @return 是否成功加载
     */
    public synchronized boolean loadLanguage(String locale) {
        locale = locale.toLowerCase(Locale.ROOT).replace("-", "_");

        // 如果已加载，直接返回
        if (loadedLanguages.contains(locale)) {
            return true;
        }

        log.debug("Loading language on demand: {}", locale);
        boolean loaded = false;

        // 尝试加载内置语言
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(Main.class.getClassLoader());
            Resource[] resources = resolver.getResources("classpath:lang/" + locale + "/*.yml");
            for (Resource res : resources) {
                try {
                    YamlConfiguration config = new YamlConfiguration();
                    config.loadFromString(new String(res.getContentAsByteArray(), StandardCharsets.UTF_8));
                    languageFilesManager.deploy(locale, config);
                    loaded = true;
                } catch (IOException | InvalidConfigurationException e) {
                    log.warn("Failed to load bundled translation for {}", locale, e);
                }
            }
        } catch (IOException e) {
            log.debug("No bundled translations found for {}", locale);
        }

        // 尝试加载覆盖语言
        File overrideFile = getOverrideLocaleFile(locale);
        if (overrideFile.exists()) {
            try {
                YamlConfiguration config = new YamlConfiguration();
                config.loadFromString(Files.readString(overrideFile.toPath(), StandardCharsets.UTF_8));
                languageFilesManager.deploy(locale, config);
                loaded = true;
            } catch (InvalidConfigurationException | IOException e) {
                log.warn("Failed to load override translation for {}", locale, e);
            }
        }

        // 如果加载成功，应用回退并添加到已加载集合
        if (loaded) {
            YamlConfiguration langConfig = languageFilesManager.getDistribution(locale);
            if (langConfig != null) {
                // 确保所有缺失的键都从回退中填充
                for (String key : fallbackConfig.getKeys(true)) {
                    if (!fallbackConfig.isConfigurationSection(key) && !langConfig.isSet(key)) {
                        langConfig.set(key, fallbackConfig.get(key));
                    }
                }
                loadedLanguages.add(locale);
            }
        } else if (!"en_us".equals(locale)) {
            // 如果无法加载请求的语言，使用回退语言
            log.warn("Failed to load language {}, using fallback", locale);
            return false;
        }

        return loaded;
    }

    /**
     * Reset everything
     */
    private void reset() {
        languageFilesManager.reset();
        postProcessors.clear();
        availableLanguages.clear();
        loadedLanguages.clear();
    }

    @NotNull
    private YamlConfiguration loadBuiltInFallback() {
        YamlConfiguration configuration = new YamlConfiguration();
        try (InputStream inputStream = Main.class.getResourceAsStream("/lang/messages_fallback.yml")) {
            if (inputStream == null) {
                log.warn("Failed to load built-in fallback translation, fallback file not exists in jar.");
                return configuration;
            }
            byte[] bytes = inputStream.readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            configuration.loadFromString(content);
            return configuration;
        } catch (IOException | InvalidConfigurationException e) {
            log.warn("Failed to load built-in fallback translation.", e);
            return configuration;
        }
    }

    @NotNull
    private File getOverrideLocaleFile(@NotNull String locale) {
        locale = locale.toLowerCase(Locale.ROOT).replace("-", "_");
        File file;
        // bug fixes workaround
        file = new File(overrideDirectory, locale + ".yml");
        if (file.isDirectory()) { // Fix bad directory name.
            file.delete();
        }
        file = new File(overrideDirectory, locale);
        file = new File(file, "messages_fallback.yml");
        return file;
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reset();
        initializeBasic();
        return Reloadable.super.reloadModule();
    }

    /**
     * 注册语言短语
     */
    @SneakyThrows(InvalidConfigurationException.class)
    public void register(@NotNull String locale, @NotNull String path, @NotNull String text) {
        locale = locale.toLowerCase(Locale.ROOT).replace("-", "_");

        // 确保语言已加载
        if (!loadedLanguages.contains(locale)) {
            loadLanguage(locale);
        }

        YamlConfiguration configuration = languageFilesManager.getDistribution(locale);
        if (configuration == null) {
            configuration = new YamlConfiguration();
            configuration.loadFromString(languageFilesManager.getDistribution("en_us").saveToString());
        }
        configuration.set(path, text);
        languageFilesManager.deploy(locale, configuration);
    }

    /**
     * 返回可用语言列表
     */
    public List<String> getAvailableLanguages() {
        return new ArrayList<>(availableLanguages);
    }

    /**
     * 获取已加载语言列表
     */
    public List<String> getLoadedLanguages() {
        return new ArrayList<>(loadedLanguages);
    }

    /**
     * 强制加载所有可用语言
     */
    public void loadAllLanguages() {
        for (String language : availableLanguages) {
            loadLanguage(language);
        }
    }
}