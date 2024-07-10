package com.ghostchu.peerbanhelper.text;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.postprocessor.PostProcessor;
import com.ghostchu.peerbanhelper.text.postprocessor.impl.FillerProcessor;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.ReloadStatus;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static com.ghostchu.peerbanhelper.Main.DEF_LOCALE;

@Slf4j
public class TextManager implements Reloadable {
    private static TextManager INSTANCE_HOLDER = new TextManager();
    public final Set<PostProcessor> postProcessors = new LinkedHashSet<>();
    // <File <Locale, Section>>
    private final LanguageFilesManager languageFilesManager = new LanguageFilesManager();
    private final Set<String> availableLanguages = new LinkedHashSet<>();
    private final File langDirectory;
    private final File overrideDirectory;

    public TextManager() {
        this.langDirectory = new File(Main.getDataDirectory(), "lang");
        this.overrideDirectory = new File(langDirectory, "overrides");
        if (!this.langDirectory.exists()) {
            this.langDirectory.mkdirs();
        }
        if (!this.overrideDirectory.exists()) {
            this.overrideDirectory.mkdirs();
        }
        load();
    }

    public static String tlUI(String key, Object... params) {
        return tl(DEF_LOCALE, new TranslationComponent(key, INSTANCE_HOLDER.convert(params)));
    }

    public static String tl(String locale, String key, Object... params) {
        return tl(locale, new TranslationComponent(key, INSTANCE_HOLDER.convert(params)));
    }

    public static String tl(String locale, TranslationComponent translationComponent) {
        locale = locale.toLowerCase(Locale.ROOT).replace("-", "_");
        YamlConfiguration yamlConfiguration = INSTANCE_HOLDER.languageFilesManager.getDistribution(locale);
        if (yamlConfiguration == null) {
            return "Unsupported locale: " + locale;
        }
        String str = yamlConfiguration.getString(translationComponent.key());
        if (str == null) {
            return translationComponent.key() + "@" + locale;
        }
        for (PostProcessor postProcessor : INSTANCE_HOLDER.postProcessors) {
            try {
                str = postProcessor.process(str, locale, translationComponent.params());
            } catch (Exception e) {
                log.warn("Unable to process post processor: key={}, locale={}, params={}", translationComponent.key(), locale, translationComponent.params());
            }
        }
        return str;
    }

    /**
     * Loading Crowdin OTA module and i18n system
     */
    public void load() {
        log.info("Loading up translations, this may need a while...");
        //TODO: This will break the message processing system in-game until loading finished, need to fix it.
        this.reset();
        // first, we need load built-in fallback translation.
        languageFilesManager.deploy("en_us", loadBuiltInFallback());
        // second, load the bundled language files
        loadBundled().forEach(languageFilesManager::deploy);
        // then, load the translations from Crowdin
        // and don't forget fix missing
        languageFilesManager.fillMissing(loadBuiltInFallback());
        // finally, load override translations
        Collection<String> pending = getOverrideLocales(languageFilesManager.getDistributions().keySet());
        log.debug("Pending: {}", Arrays.toString(pending.toArray()));
        pending.forEach(locale -> {
            File file = getOverrideLocaleFile(locale);
            if (file.exists()) {
                YamlConfiguration configuration = new YamlConfiguration();
                try {
                    configuration.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
                    languageFilesManager.deploy(locale, configuration);
                } catch (InvalidConfigurationException | IOException e) {
                    log.warn("Failed to override translation for {}.", locale, e);
                }

            } else {
                log.debug("Override not applied: File {} not exists.", file.getAbsolutePath());
            }
        });

        // Remove disabled locales
        //List<String> enabledLanguagesRegex = .getStringList("enabled-languages");
        //enabledLanguagesRegex.replaceAll(s -> s.toLowerCase(Locale.ROOT).replace("-", "_"));
//        Iterator<String> it = pending.iterator();
//        while (it.hasNext()) {
//            String locale = it.next();
//            if (!localeEnabled(locale, enabledLanguagesRegex)) {
//                this.languageFilesManager.destroy(locale);
//                it.remove();
//            }
//        }
        if (pending.isEmpty()) {
            log.warn("Warning! You must enable at least one language! Forcing enable build-in en_us...");
            pending.add("en_us");
            this.languageFilesManager.deploy("en_us", loadBuiltInFallback());
        }
        // Remember all available languages
        availableLanguages.addAll(pending);

        // Register post processor
        postProcessors.add(new FillerProcessor());
    }

    /**
     * Reset everything
     */
    private void reset() {
        languageFilesManager.reset();
        postProcessors.clear();
        availableLanguages.clear();
    }

    @NotNull
    private YamlConfiguration loadBuiltInFallback() {
        YamlConfiguration configuration = new YamlConfiguration();
        try (InputStream inputStream = Main.class.getResourceAsStream("lang/messages.yml")) {
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

    /**
     * Loading translations from bundled resources
     *
     * @return The bundled translations, empty hash map if nothing can be load.
     */
    @NotNull
    @SneakyThrows
    private Map<String, YamlConfiguration> loadBundled() {
        Map<String, YamlConfiguration> availableLang = new HashMap<>();
        URL url = Main.class.getClassLoader().getResource("");
        if (url == null) {
            return availableLang;
        }
        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        var res = resourcePatternResolver.getResources("lang/*/*.yml");
        for (Resource re : res) {
            if (re.isFile()) {
                if (re.getFilename() == null)
                    continue;
                if (!re.getFilename().endsWith(".yml")) {
                    continue;
                }
                File f = re.getFile();
                var parent = f.getParentFile();
                String langName = parent.getName();
                try {
                    YamlConfiguration configuration = new YamlConfiguration();
                    configuration.loadFromString(Files.readString(f.toPath()));
                    availableLang.put(langName.toLowerCase(Locale.ROOT).replace("-", "_"), configuration);
                } catch (IOException | InvalidConfigurationException e) {
                    log.warn("Failed to load bundled translation.", e);
                }
            }
        }
        return availableLang;
    }

    /**
     * Generate the override files storage path
     *
     * @param pool The language codes you already own.
     * @return The pool copy with new added language codes.
     */
    @SneakyThrows(IOException.class)
    @NotNull
    protected Collection<String> getOverrideLocales(@NotNull Collection<String> pool) {
        // create the pool overrides placeholder directories
        pool.forEach(single -> {
            File f = new File(overrideDirectory, single);
            if (!f.exists()) {
                f.mkdirs();
            }
        });
        //
        File[] files = overrideDirectory.listFiles();
        if (files == null) {
            return pool;
        }
        List<String> newPool = new ArrayList<>(pool);
        for (File file : files) {
            if (file.isDirectory()) {
                // custom language
                newPool.add(file.getName());
                // create the paired file
                File localeFile = new File(file, "messages.yml");
                if (!localeFile.exists()) {
                    localeFile.getParentFile().mkdirs();
                    localeFile.createNewFile();
                } else {
                    if (localeFile.isDirectory()) {
                        localeFile.delete();
                    }
                }
            }
        }
        return newPool;
    }

    @NotNull
    private File getOverrideLocaleFile(@NotNull String locale) {
        File file;
        // bug fixes workaround
        file = new File(overrideDirectory, locale + ".yml");
        if (file.isDirectory()) { // Fix bad directory name.
            file.delete();
        }
        file = new File(overrideDirectory, locale);
        file = new File(file, "messages.yml");
        return file;
    }

    @Override
    public ReloadResult reloadModule() {
        load();
        return ReloadResult.builder().status(ReloadStatus.SUCCESS).build();
    }

    /**
     * Gets specific locale status
     *
     * @param locale The locale
     * @param regex  The regexes
     * @return The locale enabled status
     */
    public boolean localeEnabled(@NotNull String locale, @NotNull List<String> regex) {
        return true;
//        for (String languagesRegex : regex) {
//            try {
//                if (Pattern.matches(CommonUtil.createRegexFromGlob(languagesRegex), locale)) {
//                    return true;
//                }
//            } catch (PatternSyntaxException exception) {
//                Log.debug("Pattern " + languagesRegex + " invalid, skipping...");
//            }
//        }
//        return false;
    }

    /**
     * Register the language phrase to QuickShop text manager in runtime.
     *
     * @param locale Target locale
     * @param path   The language key path
     * @param text   The language text
     */
    @SneakyThrows(InvalidConfigurationException.class)
    public void register(@NotNull String locale, @NotNull String path, @NotNull String text) {
        YamlConfiguration configuration = languageFilesManager.getDistribution(locale);
        if (configuration == null) {
            configuration = new YamlConfiguration();
            configuration.loadFromString(languageFilesManager.getDistribution("en_us").saveToString());
        }
        configuration.set(path, text);
        languageFilesManager.deploy(locale, configuration);
    }

    @NotNull
    public String[] convert(@Nullable Object... args) {
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
            //     Class<?> clazz = obj.getClass();
            // Check
            try {
                components[i] = obj.toString();
            } catch (Exception exception) {
                log.debug("Failed to process the object: {}", obj);
                components[i] = String.valueOf(obj); // null safe
            }
            // undefined

        }
        return components;
    }

    /**
     * Return the set of available Languages
     *
     * @return the set of available Languages
     */
    public List<String> getAvailableLanguages() {
        return new ArrayList<>(availableLanguages);
    }


}
