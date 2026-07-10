package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.DownloaderServer;
import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.configuration.DatabaseDriverConfig;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManagerImpl;
import com.ghostchu.peerbanhelper.module.ModuleStatusType;
import com.ghostchu.peerbanhelper.module.impl.webapi.body.GlobalOptionPatchBody;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.ReloadEntryDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.*;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.util.traversal.btstun.StunManager;
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
import io.javalin.openapi.*;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.configuration.InvalidConfigurationException;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import oshi.hardware.HardwareAbstractionLayer;

import javax.management.MBeanServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Proxy;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

@Slf4j
@Component
public final class PBHGeneralController extends AbstractFeatureModule {
    private static final Gson GSON = JsonUtil.getGson().newBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private ModuleMatchCache moduleMatchCache;
    @Autowired
    private ModuleManagerImpl moduleManager;
    @Autowired
    private DownloaderServer downloaderServer;
    @Autowired
    private StunManager bTStunManager;
    @Autowired
    private HTTPUtil hTTPUtil;

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

    @Override
    public void onEnable() {
        webContainer.routes()
                .get("/api/general/status", this::handleStatusGet, Role.USER_READ)
                .post("/api/general/refreshNatStatus", this::handleRefreshNatStatus, Role.USER_WRITE)
                .get("/api/general/checkModuleAvailable", this::handleModuleAvailable, Role.USER_READ)
                .get("/api/general/stacktrace", this::handleDumpStackTrace, Role.USER_READ)
                .get("/api/general/heapdump", this::handleHeapDump, Role.USER_WRITE)
                .post("/api/general/reload", this::handleReloading, Role.USER_WRITE)
                .get("/api/general/global", this::handleGlobalConfigRead, Role.USER_READ)
                .patch("/api/general/global", this::handleGlobalConfig, Role.USER_WRITE)
                .get("/api/general/{configName}", this::handleConfigGet, Role.USER_READ)
                .put("/api/general/{configName}", this::handleConfigPut, Role.USER_WRITE);
    }


    @OpenApi(
            path = "/api/general/refreshNatStatus",
            methods = HttpMethod.POST,
            summary = "刷新 NAT 状态",
            description = "触发 NAT 类型状态刷新",
            tags = {"常规"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleRefreshNatStatus"
    )
    private void handleRefreshNatStatus(@NotNull Context context) {
        Thread.ofVirtual().name("Refresh NAT Status").start(() -> bTStunManager.refreshNatType());
        context.json(new StdResp(true, "Refreshing NAT Status", null));
    }


    @OpenApi(
            path = "/api/general/stacktrace",
            methods = HttpMethod.GET,
            summary = "获取 JVM 堆栈信息",
            description = "返回当前 JVM 线程堆栈信息",
            tags = {"常规"},
            responses = {
                    @OpenApiResponse(status = "200"),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleDumpStackTrace"
    )
    private void handleDumpStackTrace(Context context) {
        StringBuilder threadDump = new StringBuilder();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(true, true)) {
            threadDump.append(MsgUtil.threadInfoToString(threadInfo));
        }
        if ("application/json".equals(context.contentType())) {
            context.json(new StdResp(true, null, threadDump.toString()));
        } else {
            context.result(threadDump.toString());
        }
    }

    @OpenApi(
            path = "/api/general/global",
            methods = HttpMethod.GET,
            summary = "获取全局配置",
            description = "读取全局暂停和分析配置",
            tags = {"常规"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleGlobalConfigRead"
    )
    private void handleGlobalConfigRead(Context context) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("globalPaused", downloaderServer.isGlobalPaused());
        data.put("analytics", Main.getMainConfig().getBoolean("privacy.analytics"));
        context.json(new StdResp(true, null, data));
    }

    @OpenApi(
            path = "/api/general/global",
            methods = HttpMethod.PATCH,
            summary = "更新全局配置",
            description = "更新全局暂停等全局配置项",
            tags = {"常规"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = GlobalOptionPatchBody.class)),
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleGlobalConfig"
    )
    private void handleGlobalConfig(Context context) {
        var body = context.bodyAsClass(GlobalOptionPatchBody.class);
        if (body == null) {
            throw new IllegalArgumentException("Request body cannot be null");
        }
        if (body.globalPaused() != null) {
            downloaderServer.setGlobalPaused(body.globalPaused());
        }
        context.json(new StdResp(true, "OK!", null));
    }

    @OpenApi(
            path = "/api/general/checkModuleAvailable",
            methods = HttpMethod.GET,
            summary = "检查模块是否可用",
            description = "检查指定模块是否已启用",
            tags = {"常规"},
            queryParams = {
                    @OpenApiParam(name = "module", description = "模块名称或标识", required = true)
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleModuleAvailable"
    )
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
                if (module.getModuleStatus().getType() == ModuleStatusType.ENABLED) {
                    context.json(new StdResp(true, null, true));
                    return;
                }
            }
        }
        context.json(new StdResp(true, null, false));
    }

    @OpenApi(
            path = "/api/general/heapdump",
            methods = HttpMethod.GET,
            summary = "生成 JVM 堆转储文件",
            description = "生成并下载 JVM 堆转储文件",
            tags = {"常规"},
            responses = {
                    @OpenApiResponse(status = "200"),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleHeapDump"
    )
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
            context.disableCompression();
            var stream = new FileInputStream(finalHprof); // 这个 stream 将由 Jetty 关闭，不要手动关闭流，否则报错 stream closed
            context.result(stream);
        }
    }

    @OpenApi(
            path = "/api/general/status",
            methods = HttpMethod.GET,
            summary = "获取服务器状态",
            description = "返回系统、JVM 和应用状态信息",
            tags = {"常规"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleStatusGet"
    )
    private void handleStatusGet(Context context) {
        // 有点大而全了，需要和前端看看哪些不需要可以删了
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jvm", generateJvmData());
        data.put("system", generateSystemData(context));
        data.put("peerbanhelper", generatePbhData());
        context.json(new StdResp(true, null, data));
    }

    private Map<String, Object> generatePbhData() {
        long compile_time = 0;
        String release = ExternalSwitch.parse("pbh.release");
        if (release == null) {
            release = "unknown";
        }
        try {
            var meta = Main.getMeta().getCompileTime();
            OffsetDateTime offsetDateTime;
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
                offsetDateTime = OffsetDateTime.parse(meta, formatter);
            } catch (Exception e) {
                offsetDateTime = OffsetDateTime.parse(meta, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }
            compile_time = offsetDateTime.toInstant().toEpochMilli();
        } catch (Exception ignore) {
        }
        Map<String, Object> pbh = new LinkedHashMap<>();
        pbh.put("version", Main.getMeta().getVersion());
        pbh.put("commit_id", Main.getMeta().getCommit());
        pbh.put("compile_time", compile_time / 1000);
        pbh.put("release", release);
        pbh.put("uptime", (System.currentTimeMillis() - Main.getStartupAt()) / 1000);
        pbh.put("data_dir", Main.getDataDirectory().getAbsolutePath());
        pbh.put("database_type", DatabaseDriverConfig.databaseDriver.getType().name());
        //pbh.put("gui_available", Main.getGuiManager().isGuiAvailable());
        //pbh.put("default_locale", Main.DEF_LOCALE);
        return pbh;
    }

    private Map<String, Object> generateSystemData(Context context) {
        Map<String, Object> os = new LinkedHashMap<>();
        var osMXBean = ManagementFactory.getOperatingSystemMXBean();
        os.put("architecture", osMXBean.getArch());
        os.put("cores", osMXBean.getAvailableProcessors());
        os.put("load", osMXBean.getSystemLoadAverage());
        if (osMXBean.getName().contains("Windows")) {
            os.put("os", "Windows");
            os.put("version", osMXBean.getVersion());
        } else {
            os.put("os", osMXBean.getName());
            os.put("version", osMXBean.getVersion());
        }
        var network = generateNetworkStats(context);
        os.put("network", network);
        try {
            SystemInfoProviderWrapper.find().ifPresent(provider -> {
                var operatingSystem = provider.getOperatingSystem();
                os.put("version", String.valueOf(operatingSystem));
                var mem = generateSystemMemoryData(provider.getHardware());
                os.put("memory", mem);
            });
        }catch (Throwable _){
            os.put("version", "N/A");
        }
        return os;
    }

    private Map<String, Object> generateNetworkStats(Context context) {
        var userIp = IPAddressUtil.getIPAddress(userIp(context)).toCompressedString();
        Map<String, Object> network = new LinkedHashMap<>();
        var proxy = Main.getMainConfig().getInt("proxy.setting");
        network.put("internet_access", hTTPUtil.getNetworkReachability());
        network.put("use_proxy", hTTPUtil.getProxyType() != Proxy.Type.DIRECT);
        network.put("reverse_proxy", WebUtil.isUsingReserveProxy(context));
        network.put("client_ip", userIp);
        network.put("nat_type", bTStunManager.getCachedNatType().name());
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
        jvm.put("startup_arguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
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

    @OpenApi(
            path = "/api/general/reload",
            methods = HttpMethod.POST,
            summary = "重载所有模块",
            description = "执行系统配置和模块重载",
            tags = {"常规"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleReloading"
    )
    private void handleReloading(Context context) {
        Main.setupConfiguration();
        var result = Main.getReloadManager().reload();
        List<ReloadEntryDTO> entryList = new ArrayList<>();
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
            entryList.add(new ReloadEntryDTO(entryName, r.getStatus().name()));
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

    @OpenApi(
            path = "/api/general/{configName}",
            methods = HttpMethod.GET,
            summary = "获取模块配置",
            description = "读取指定模块配置内容",
            tags = {"常规"},
            pathParams = {
                    @OpenApiParam(name = "configName", description = "配置名称", required = true)
            },
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "generalConfigGet"
    )
    private void handleConfigGet(Context context) throws IOException, InvalidConfigurationException {
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        yamlConfiguration.getOptions()
                .setParseComments(true)
                .setWidth(1000);
        switch (context.pathParam("configName")) {
            case "config" -> {
                yamlConfiguration.load(Main.getMainConfigFile());
                if (ExternalSwitch.parseBoolean("pbh.demoMode")) {
                    yamlConfiguration.set("client", null);
                    yamlConfiguration.set("btn.app-id", "REDACTED_IN_DEMO_MODE");
                    yamlConfiguration.set("btn.app-secret", "REDACTED_IN_DEMO_MODE");
                    yamlConfiguration.set("pbh-plus-key", "REDACTED_IN_DEMO_MODE");
                    yamlConfiguration.set("server.token", "REDACTED_IN_DEMO_MODE");
                    yamlConfiguration.set("installation-id", "Not Available In Demo Mode");
                    yamlConfiguration.set("proxy", null);
                }
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

    @OpenApi(
            path = "/api/general/{configName}",
            methods = HttpMethod.PUT,
            summary = "更新模块配置",
            description = "更新指定模块配置并触发重载",
            tags = {"常规"},
            pathParams = {
                    @OpenApiParam(name = "configName", description = "配置名称", required = true)
            },
            responses = {
                    @OpenApiResponse(status = "201", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleConfigPut"
    )
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

    private static void mergeYaml(ConfigurationSection cfg, Map<String, Object> newMap, String target, String replacement) {
        String path = cfg.getCurrentPath();
        newMap.forEach((key, val) -> {
            final String originalKey = cfg.contains(key) ? key : key.replace(target, replacement);
            switch (originalKey) {
                // 推送服务转回字典
                case "push-notification" -> {
                    if ("".equals(path)) {
                        val = ((List<?>) val).stream()
                                .filter(Map.class::isInstance)
                                .map(Map.class::cast)
                                .collect(LinkedHashMap::new, (map, entry) -> {
                                    String name = (String) entry.remove("push_notification_name");
                                    map.put(name, entry);
                                }, LinkedHashMap::putAll);
                    }
                }
                // 对象列表转为字符串列表
                case "banned-peer-id" -> {
                    if ("module.peer-id-blacklist".equals(path)) {
                        val = ((List<?>) val).stream()
                                .filter(Map.class::isInstance)
                                .map(GSON::toJson)
                                .toList();
                    }
                }
                case "banned-client-name" -> {
                    if ("module.client-name-blacklist".equals(path)) {
                        val = ((List<?>) val).stream()
                                .filter(Map.class::isInstance)
                                .map(GSON::toJson)
                                .toList();
                    }
                }
                case "ptr-rules" -> {
                    if ("module.ptr-blacklist".equals(path)) {
                        val = ((List<?>) val).stream()
                                .filter(Map.class::isInstance)
                                .map(GSON::toJson)
                                .toList();
                    }
                }
            }
            // 如果值是 Map，递归替换子 cfg 中的键
            if (val instanceof Map<?, ?> map) {
                ConfigurationSection section = Optional.ofNullable(cfg.getConfigurationSection(originalKey))
                        .orElseGet(() -> cfg.createSection(originalKey));
                mergeYaml(section, (Map<String, Object>) map, target, replacement);
                cfg.set(originalKey, section);
            }
            // 如果值是 List，检查其中的元素是否是 Map，如果是也进行递归处理
            else if (val instanceof List<?> list) {
                List<Object> updatedList = list.stream()
                        .map(item -> item instanceof Map<?, ?> ? replaceKeys((Map<String, Object>) item, target, replacement) : item)
                        .toList();
                cfg.set(originalKey, updatedList);
            }
            // 直接添加非 Map 和非 List 的值
            else {
                cfg.set(originalKey, val); // 直接添加
            }
        });
    }

    @Override
    public void onDisable() {

    }

}
