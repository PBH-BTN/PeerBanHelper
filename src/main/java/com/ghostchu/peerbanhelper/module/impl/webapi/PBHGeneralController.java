package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadStatus;
import com.ghostchu.simplereloadlib.Reloadable;
import com.google.gson.Gson;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import com.sun.management.HotSpotDiagnosticMXBean;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

import javax.management.MBeanServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Slf4j
@Component
@IgnoreScan
public class PBHGeneralController extends AbstractFeatureModule {
    private static final Gson GSON = JsonUtil.getGson().newBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private ModuleMatchCache moduleMatchCache;
    @Autowired
    private ModuleManager moduleManager;
    @Autowired
    private PeerBanHelperServer peerBanHelperServer;

    /**
     * Indicates whether this module is configurable.
     *
     * @return Always returns {@code false}, signifying that this module does not support direct configuration.
     */
    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - General";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-general";
    }

    /**
     * Initializes and registers web API endpoints for general application functionality.
     *
     * This method sets up multiple HTTP endpoints using Javalin, each mapped to a specific handler method
     * and associated with a required user role. The endpoints cover various operations such as:
     * - Retrieving system status
     * - Checking module availability
     * - Generating heap dumps
     * - Reloading application configuration
     * - Reading and updating global configuration
     * - Retrieving and updating specific configuration sections
     *
     * Endpoints are configured with appropriate HTTP methods (GET, POST, PATCH, PUT) and access roles
     * to ensure proper security and access control.
     *
     * @see Role Defines the user roles required to access different endpoints
     */
    @Override
    public void onEnable() {
        webContainer.javalin()
                .get("/api/general/status", this::handleStatusGet, Role.USER_READ)
                .get("/api/general/checkModuleAvailable", this::handleModuleAvailable, Role.USER_READ)
                .get("/api/general/heapdump", this::handleHeapDump, Role.USER_WRITE)
                .post("/api/general/reload", this::handleReloading, Role.USER_WRITE)
                .get("/api/general/global", this::handleGlobalConfigRead, Role.USER_READ)
                .patch("/api/general/global", this::handleGlobalConfig, Role.USER_WRITE)
                .get("/api/general/{configName}", this::handleConfigGet, Role.USER_WRITE)
                .put("/api/general/{configName}", this::handleConfigPut, Role.USER_WRITE);
    }

    /**
     * Handles GET requests to retrieve the global paused state of the application.
     *
     * This method creates a response containing the current global paused status
     * from the PeerBanHelperServer. It returns a standardized JSON response with
     * the global paused state.
     *
     * @param context The Javalin request context used to send the JSON response
     */
    private void handleGlobalConfigRead(Context context) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("globalPaused", peerBanHelperServer.isGlobalPaused());
        context.json(new StdResp(true, null, data));
    }

    /**
     * Handles a PATCH request to update the global paused state of the application.
     *
     * @param context The Javalin request context containing the HTTP request details
     * @throws IllegalArgumentException if the request body is null
     *
     * @apiNote This method allows updating the global paused state through a PATCH request.
     *          If a valid globalPaused value is provided, it updates the server's global paused status.
     *          Returns a standard JSON response indicating successful update.
     */
    private void handleGlobalConfig(Context context) {
        var body = context.bodyAsClass(GlobalOptionPatch.class);
        if (body == null) {
            throw new IllegalArgumentException("Request body cannot be null");
        }
        if (body.globalPaused() != null) {
            peerBanHelperServer.setGlobalPaused(body.globalPaused());
        }
        context.json(new StdResp(true, "OK!", null));
    }

    /**
     * Checks the availability of a specified module in the application.
     *
     * This method handles a GET request to determine if a module is available and enabled.
     * The module can be identified by its full name, configuration name, fully qualified class name,
     * or simple class name (case-insensitive).
     *
     * @param context The Javalin request context containing the query parameters
     * @throws IllegalArgumentException if the module query parameter is not provided
     *
     * @apiNote Responds with a JSON object containing a boolean indicating module availability:
     * - Returns true if the module exists and is enabled
     * - Returns false if the module is not found or is disabled
     *
     * @see StdResp
     * @see FeatureModule
     */
    private void handleModuleAvailable(Context context) {
        var moduleName = context.queryParam("module");
        if (moduleName == null) {
            throw new IllegalArgumentException("module argument cannot be null");
        }
        for (FeatureModule module : moduleManager.getModules()) {
            if (module.getName().equalsIgnoreCase(moduleName)
                    || module.getConfigName().equalsIgnoreCase(moduleName)
                    || module.getClass().getName().equalsIgnoreCase(moduleName)
                    || module.getClass().getSimpleName().equalsIgnoreCase(moduleName)) {
                if (module.isModuleEnabled()) {
                    context.json(new StdResp(true, null, true));
                    return;
                }
            }
        }
        context.json(new StdResp(true, null, false));
    }

    private void handleHeapDump(Context context) throws IOException {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
                server, "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
        File hprof = Files.createTempFile("heapdump", ".hprof").toFile();
        hprof.delete();
        File finalHprof = new File(Main.getDebugDirectory(), System.currentTimeMillis() + ".hprof.gz");
        System.gc();
        mxBean.dumpHeap(hprof.getAbsolutePath(), true);
        if (!finalHprof.exists())
            finalHprof.createNewFile();
        try (var filein = new FileInputStream(hprof);
             var fileout = new FileOutputStream(finalHprof)) {
            MiscUtil.gzip(filein, fileout);
            context.header("Content-Disposition", "attachment; filename=\"" + finalHprof.getName() + "\"");
            context.header("Content-Length", String.valueOf(finalHprof.length()));
            context.header("Content-Type", "application/octet-stream");
            var stream = new FileInputStream(finalHprof); // 这个 stream 将由 Jetty 关闭，不要手动关闭流，否则报错 stream closed
            context.result(stream);
        }
    }

    private void handleStatusGet(Context context) {
        // 有点大而全了，需要和前端看看哪些不需要可以删了
        SystemInfo systemInfo = new SystemInfo();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jvm", generateJvmData());
        data.put("system", generateSystemData(context, systemInfo));
        data.put("peerbanhelper", generatePbhData());
        context.json(new StdResp(true, null, data));
    }

    private Map<String, Object> generatePbhData() {
        long compile_time = 0;
        String release = System.getProperty("pbh.release");
        if (release == null) {
            release = "unknown";
        }
        try {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
            compile_time = formatter.parse(Main.getMeta().getCompileTime()).getTime() / 1000;
        } catch (Exception ignore) {
        }

        Map<String, Object> pbh = new LinkedHashMap<>();
        pbh.put("version", Main.getMeta().getVersion());
        pbh.put("commit_id", Main.getMeta().getCommit());
        pbh.put("compile_time", compile_time);
        pbh.put("release", release);
        pbh.put("uptime", (System.currentTimeMillis() - Main.getStartupAt()) / 1000);
        pbh.put("data_dir", Main.getDataDirectory().getAbsolutePath());
        //pbh.put("gui_available", Main.getGuiManager().isGuiAvailable());
        //pbh.put("default_locale", Main.DEF_LOCALE);
        return pbh;
    }

    private Map<String, Object> generateSystemData(Context context, SystemInfo systemInfo) {

        Map<String, Object> os = new LinkedHashMap<>();
        var osMXBean = ManagementFactory.getOperatingSystemMXBean();
        var operatingSystem = systemInfo.getOperatingSystem();
        if (osMXBean.getName().contains("Windows")) {
            os.put("os", "Windows");
            os.put("version", String.valueOf(operatingSystem));
        } else {
            os.put("os", osMXBean.getName());
            os.put("version", osMXBean.getVersion());
        }
        os.put("architecture", osMXBean.getArch());
        os.put("cores", osMXBean.getAvailableProcessors());
        var mem = generateSystemMemoryData(systemInfo.getHardware());
        os.put("memory", mem);
        os.put("load", osMXBean.getSystemLoadAverage());
        var network = generateNetworkStats(context);
        os.put("network", network);
        return os;
    }

    private Map<String, Object> generateNetworkStats(Context context) {
        var userIp = IPAddressUtil.getIPAddress(userIp(context)).toCompressedString();
        Map<String, Object> network = new LinkedHashMap<>();
        var proxy = Main.getMainConfig().getInt("proxy.setting");
        network.put("internet_access", true); // ?
        network.put("use_proxy", proxy == 1 || proxy == 2 || proxy == 3);
        network.put("reverse_proxy", MiscUtil.isUsingReserveProxy(context));
        network.put("client_ip", userIp);
        return network;
    }

    private Map<String, Object> generateJvmData() {
        var runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("version", runtimeMXBean.getVmVersion());
        jvm.put("vendor", runtimeMXBean.getVmVendor());
        jvm.put("runtime", runtimeMXBean.getVmName());
        jvm.put("bitness", Short.parseShort(System.getProperty("sun.arch.data.model")));
        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("heap", generateMemoryData(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage()));
        mem.put("non_heap", generateMemoryData(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage()));
        jvm.put("memory", mem);
        return jvm;
    }

    private Map<String, Object> generateSystemMemoryData(HardwareAbstractionLayer hardware) {
        Map<String, Object> data = new LinkedHashMap<>();
        var mem = hardware.getMemory();
        data.put("total", mem.getTotal());
        data.put("free", mem.getAvailable());
        data.put("page_size", mem.getPageSize());
        return data;
    }

    private Map<String, Object> generateMemoryData(MemoryUsage heapMemoryMXBean) {
        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("init", heapMemoryMXBean.getInit());
        mem.put("max", heapMemoryMXBean.getMax());
        mem.put("used", heapMemoryMXBean.getUsed());
        mem.put("free", heapMemoryMXBean.getMax() - heapMemoryMXBean.getUsed());
        mem.put("committed", heapMemoryMXBean.getCommitted());
        return mem;
    }

    private void handleReloading(Context context) {
        Main.setupConfiguration();
        var result = Main.getReloadManager().reload();
        List<ReloadEntry> entryList = new ArrayList<>();
        result.forEach((container, r) -> {
            String entryName;
            if (container.getReloadable() == null) {
                entryName = container.getReloadableMethod().getDeclaringClass().getName() + "#" + container.getReloadableMethod().getName();
            } else {
                Reloadable reloadable = container.getReloadable().get();
                if (reloadable == null) {
                    entryName = "<invalid>";
                } else {
                    entryName = reloadable.getClass().getName();
                }
            }
            entryList.add(new ReloadEntry(entryName, r.getStatus().name()));
        });
        moduleMatchCache.invalidateAll();

        boolean success = true;
        TranslationComponent message = new TranslationComponent(Lang.RELOAD_RESULT_SUCCESS);
        if (result.values().stream().anyMatch(r -> r.getStatus() == ReloadStatus.SCHEDULED)) {
            message = new TranslationComponent(Lang.RELOAD_RESULT_SCHEDULED);
        }
        if (result.values().stream().anyMatch(r -> r.getStatus() == ReloadStatus.REQUIRE_RESTART)) {
            message = new TranslationComponent(Lang.RELOAD_RESULT_REQUIRE_RESTART);
        }
        if (result.values().stream().anyMatch(r -> r.getStatus() == ReloadStatus.EXCEPTION)) {
            success = false;
            message = new TranslationComponent(Lang.RELOAD_RESULT_FAILED);
        }

        context.json(new StdResp(success, tl(locale(context), message), entryList));
    }

    /**
     * Retrieves and processes configuration data based on the specified configuration name.
     *
     * @param context The Javalin request context containing the configuration name path parameter
     * @throws IOException If there is an error reading the configuration file
     * @throws InvalidConfigurationException If the configuration file is invalid or cannot be parsed
     *
     * @apiNote Supports retrieving two types of configurations:
     * - "config": Main configuration file with push notification settings
     * - "profile": Profile configuration file with various blacklist and rule settings
     *
     * @return Sends a JSON response with the processed configuration data or a 404 status if the config name is invalid
     */
    private void handleConfigGet(Context context) throws IOException, InvalidConfigurationException {
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        yamlConfiguration.getOptions()
                .setParseComments(true)
                .setWidth(1000);
        switch (context.pathParam("configName")) {
            case "config" -> {
                yamlConfiguration.load(Main.getMainConfigFile());
                sectionToList(yamlConfiguration, "push-notification", "push-notification-name");
            }
            case "profile" -> {
                yamlConfiguration.load(Main.getProfileConfigFile());
                stringListToMapList(yamlConfiguration, "module.peer-id-blacklist", "banned-peer-id");
                stringListToMapList(yamlConfiguration, "module.client-name-blacklist", "banned-client-name");
                stringListToMapList(yamlConfiguration, "module.ptr-blacklist", "ptr-rules");
            }
            default -> {
                context.status(HttpStatus.NOT_FOUND);
                return;
            }
        }
        context.json(new StdResp(true, null, replaceKeys(yamlConfiguration, "-", "_")));
    }

    private void handleConfigPut(Context context) throws IOException, InvalidConfigurationException {
        File configFile;
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        yamlConfiguration.getOptions()
                .setParseComments(true)
                .setWidth(1000);
        switch (context.pathParam("configName")) {
            case "config" -> configFile = Main.getMainConfigFile();
            case "profile" -> configFile = Main.getProfileConfigFile();
            default -> {
                context.status(HttpStatus.NOT_FOUND);
                return;
            }
        }
        yamlConfiguration.load(configFile);
        Map<String, Object> newData = GSON.fromJson(context.body(), Map.class);
        mergeYaml(yamlConfiguration, newData, "_", "-");
        yamlConfiguration.save(configFile);
        //moduleMatchCache.invalidateAll();
        context.status(HttpStatus.CREATED);
        //context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        handleReloading(context);
    }

    private static void stringListToMapList(ConfigurationSection cfg, String path, String field) {
        ConfigurationSection section = cfg.getConfigurationSection(path);
        if (section != null) {
            List<Map<String, String>> list = section.getStringList(field).stream()
                    .map(item -> (Map<String, String>) GSON.fromJson(item, new TypeToken<Map<String, String>>() {
                    }.getType()))
                    .toList();
            section.set(field, list);
            cfg.set(path, section);
        }
    }

    private static void sectionToList(ConfigurationSection cfg, String path, String key) {
        ConfigurationSection section = cfg.getConfigurationSection(path);
        if (section != null) {
            List<Object> list = section.getKeys(false).stream().map(k -> {
                ConfigurationSection s2 = section.getConfigurationSection(k);
                if (s2 != null) {
                    s2.set(key, k);
                    return s2;
                }
                return section.get(k);
            }).toList();
            cfg.set(path, list);
        }
    }

    private static Map<String, Object> replaceKeys(ConfigurationSection cfg, String target, String replacement) {
        return cfg.getValues(false).entrySet().stream()
                .collect(LinkedHashMap::new, (map, entry) -> {
                    String updatedKey = entry.getKey().replace(target, replacement);
                    Object value = entry.getValue();
                    if (value instanceof ConfigurationSection c) {
                        map.put(updatedKey, replaceKeys(c, target, replacement));
                    } else if (value instanceof List<?> list) {
                        List<Object> updatedList = list.stream()
                                .map(item -> item instanceof ConfigurationSection c ? replaceKeys(c, target, replacement) : item)
                                .toList();
                        map.put(updatedKey, updatedList);
                    } else {
                        map.put(updatedKey, value);
                    }
                }, LinkedHashMap::putAll);
    }

    private static Map<String, Object> replaceKeys(Map<String, Object> originalMap, String target, String replacement) {
        return originalMap.entrySet().stream()
                .collect(LinkedHashMap::new, (map, entry) -> {
                    String updatedKey = entry.getKey().replace(target, replacement);
                    Object value = entry.getValue();
                    // 如果值是 Map，递归替换子 Map 中的键
                    if (value instanceof Map<?, ?> m) {
                        map.put(updatedKey, replaceKeys((Map<String, Object>) m, target, replacement));
                    }
                    // 如果值是 List，检查其中的元素是否是 Map，如果是也进行递归处理
                    else if (value instanceof List<?> list) {
                        List<Object> updatedList = list.stream()
                                .map(item -> item instanceof Map<?, ?> ? replaceKeys((Map<String, Object>) item, target, replacement) : item)
                                .toList();
                        map.put(updatedKey, updatedList);
                    }
                    // 直接添加非 Map 和非 List 的值
                    else {
                        map.put(updatedKey, value);
                    }
                }, LinkedHashMap::putAll);
    }

    /**
     * Merges a new YAML configuration map into an existing configuration section with key replacement and transformation.
     *
     * This method recursively processes a configuration map, handling special cases for different configuration types
     * and performing key replacements and data transformations. It supports:
     * - Converting push notification configurations
     * - Converting object lists to string lists for specific blacklist configurations
     * - Recursive key replacement in nested maps and lists
     *
     * @param cfg The target configuration section to merge into
     * @param newMap The new configuration map to merge
     * @param target The original key pattern to be replaced
     * @param replacement The replacement key pattern
     */
    private static void mergeYaml(ConfigurationSection cfg, Map<String, Object> newMap, String target, String replacement) {
        String path = cfg.getCurrentPath();
        newMap.forEach((key, value) -> {
            final String originalKey = cfg.contains(key) ? key : key.replace(target, replacement);
            switch (originalKey) {
                // 推送服务转回字典
                case "push-notification" -> {
                    if ("".equals(path)) {
                        Map<String, Object> pushMap = ((List<?>) value).stream()
                                .filter(Map.class::isInstance)
                                .map(Map.class::cast)
                                .collect(LinkedHashMap::new, (map, entry) -> {
                                    String name = (String) entry.remove("push_notification_name");
                                    map.put(name, entry);
                                }, LinkedHashMap::putAll);
                        value = pushMap;
                    }
                }
                // 对象列表转为字符串列表
                case "banned-peer-id" -> {
                    if ("module.peer-id-blacklist".equals(path)) {
                        List<String> bannedList = ((List<?>) value).stream()
                                .filter(Map.class::isInstance)
                                .map(GSON::toJson)
                                .toList();
                        value = bannedList;
                    }
                }
                case "banned-client-name" -> {
                    if ("module.client-name-blacklist".equals(path)) {
                        List<String> bannedList = ((List<?>) value).stream()
                                .filter(Map.class::isInstance)
                                .map(GSON::toJson)
                                .toList();
                        value = bannedList;
                    }
                }
                case "ptr-rules" -> {
                    if ("module.ptr-blacklist".equals(path)) {
                        List<String> bannedList = ((List<?>) value).stream()
                                .filter(Map.class::isInstance)
                                .map(GSON::toJson)
                                .toList();
                        value = bannedList;
                    }
                }
            }
            // 如果值是 Map，递归替换子 cfg 中的键
            if (value instanceof Map<?, ?> map) {
                ConfigurationSection section = Optional.ofNullable(cfg.getConfigurationSection(originalKey))
                        .orElseGet(() -> cfg.createSection(originalKey));
                mergeYaml(section, (Map<String, Object>) map, target, replacement);
                cfg.set(originalKey, section);
            }
            // 如果值是 List，检查其中的元素是否是 Map，如果是也进行递归处理
            else if (value instanceof List<?> list) {
                List<Object> updatedList = list.stream()
                        .map(item -> item instanceof Map<?, ?> ? replaceKeys((Map<String, Object>) item, target, replacement) : item)
                        .toList();
                cfg.set(originalKey, updatedList);
            }
            // 直接添加非 Map 和非 List 的值
            else {
                cfg.set(originalKey, value); // 直接添加
            }
        });
    }

    /**
     * Handles the disabling of the PBHGeneralController module.
     * 
     * This method is called when the module is being disabled or shut down. 
     * Currently, no specific cleanup or resource release operations are implemented.
     * 
     * @see AbstractFeatureModule#onDisable()
     */
    @Override
    public void onDisable() {

    }

    public record GlobalOptionPatch(
            Boolean globalPaused
    ) {

    }

    public record ReloadEntry(
            String reloadable,
            String reloadResult
    ) {
    }

}
