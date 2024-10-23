package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
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
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

import javax.management.MBeanServer;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;

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
        webContainer.javalin()
                .get("/api/general/status", this::handleStatusGet, Role.USER_READ)
                .post("/api/general/heapdump", this::handleHeapDump, Role.USER_WRITE)
                .post("/api/general/reload", this::handleReloading, Role.USER_WRITE)
                .get("/api/general/{configName}", this::handleConfigGet, Role.USER_READ)
                .put("/api/general/{configName}", this::handleConfigPut, Role.USER_WRITE);
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
        //pbh.put("data_dir", Main.getDataDirectory().getAbsolutePath());
        //pbh.put("gui_available", Main.getGuiManager().isGuiAvailable());
        //pbh.put("default_locale", Main.DEF_LOCALE);
        return pbh;
    }

    private Map<String, Object> generateSystemData(Context context, SystemInfo systemInfo) {
        var osMXBean = ManagementFactory.getOperatingSystemMXBean();
        var operatingSystem = systemInfo.getOperatingSystem();
        Map<String, Object> os = new LinkedHashMap<>();
        os.put("os", osMXBean.getName());
        os.put("version", osMXBean.getVersion());
        os.put("architecture", osMXBean.getArch());
        os.put("cores", osMXBean.getAvailableProcessors());
        //os.put("family", operatingSystem.getFamily());
        //os.put("bitness", operatingSystem.getBitness());
        //os.put("manufacturer", operatingSystem.getManufacturer());
        //var versionInfo = operatingSystem.getVersionInfo();
        //os.put("build_number", versionInfo.getBuildNumber());
        //os.put("code_name", versionInfo.getCodeName());
        //os.put("os_version", versionInfo.getVersion());
        //os.put("boot_time", operatingSystem.getSystemBootTime());
        //os.put("up_time", operatingSystem.getSystemUptime());
        var mem = generateSystemMemoryData(systemInfo.getHardware());
        os.put("memory", mem);
        os.put("load", osMXBean.getSystemLoadAverage());
        var network = generateNetworkStats(context, context.ip(), userIp(context), systemInfo);
        os.put("network", network);
        return os;
    }

    private Map<String, Object> generateNetworkStats(Context context, String clientIp, String userIp, SystemInfo systemInfo) {
        Map<String, Object> network = new LinkedHashMap<>();
        var proxy = Main.getMainConfig().getInt("proxy.setting");
        network.put("internet_access", true); // ?
        network.put("use_proxy", proxy == 1 || proxy == 2 || proxy == 3);
        network.put("reverse_proxy", MiscUtil.isUsingReserveProxy(context));
        network.put("client_ip", userIp);
        //network.put("user_ip", userIp);
        return network;
    }

    private Map<String, Object> generateJvmData() {
        var runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("version", runtimeMXBean.getVmVersion());
        jvm.put("vendor", runtimeMXBean.getVmVendor());
        jvm.put("runtime", runtimeMXBean.getVmName());
        jvm.put("bitness", Short.parseShort(System.getProperty("sun.arch.data.model")));
        //jvm.put("specification", runtimeMXBean.getSpecName());
        //jvm.put("class_version", System.getProperty("java.class.version"));
        //jvm.put("uptime", runtimeMXBean.getUptime());
        //jvm.put("start_time", runtimeMXBean.getStartTime());
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

    private void handleConfigGet(Context context) throws FileNotFoundException {
        File configFile;
        switch (context.pathParam("configName")) {
            case "config" -> configFile = Main.getMainConfigFile();
            case "profile" -> configFile = Main.getProfileConfigFile();
            default -> {
                context.status(HttpStatus.NOT_FOUND);
                return;
            }
        }
        Map<String, Object> originalYaml = new Yaml().load(new FileReader(configFile));
        context.json(new StdResp(true, null, replaceKeys(originalYaml)));
    }

    private void handleConfigPut(Context context) throws IOException {
        File configFile;
        YamlConfiguration config;
        switch (context.pathParam("configName")) {
            case "config" -> {
                config = Main.getMainConfig();
                configFile = Main.getMainConfigFile();
            }
            case "profile" -> {
                config = Main.getProfileConfig();
                configFile = Main.getProfileConfigFile();
            }
            default -> {
                context.status(HttpStatus.NOT_FOUND);
                return;
            }
        }
        Map<String, Object> newData = GSON.fromJson(context.body(), Map.class);
        mergeYaml(config, newData);
        config.save(configFile);
        //moduleMatchCache.invalidateAll();
        context.status(HttpStatus.CREATED);
        //context.json(new StdResp(true, tl(locale(context), Lang.OPERATION_EXECUTE_SUCCESSFULLY), null));
        handleReloading(context);
    }

    private Map<String, Object> replaceKeys(Map<String, Object> originalMap) {
        return originalMap.entrySet().stream()
                .collect(LinkedHashMap::new, (map, entry) -> {
                    String updatedKey = entry.getKey().replace("-", "_");
                    Object value = entry.getValue();
                    // 字符串列表转为对象列表
                    if (updatedKey.equals("banned_peer_id") || updatedKey.equals("banned_client_name")) {
                        if (value instanceof List<?> list) {
                            List<Map<String, String>> parsedBannedPeerIdList = list.stream()
                                    .filter(String.class::isInstance)
                                    .map(String.class::cast)
                                    .map(item -> (Map<String, String>) GSON.fromJson(item, new TypeToken<Map<String, String>>() {
                                    }.getType()))
                                    .toList();
                            map.put(updatedKey, parsedBannedPeerIdList); // 替换为对象列表
                        }
                    }
                    // 如果值是 Map，递归替换子 Map 中的键
                    else if (value instanceof Map<?, ?>) {
                        map.put(updatedKey, replaceKeys((Map<String, Object>) value));
                    }
                    // 如果值是 List，检查其中的元素是否是 Map，如果是也进行递归处理
                    else if (value instanceof List<?> list) {
                        List<Object> updatedList = list.stream()
                                .map(item -> item instanceof Map<?, ?> ? replaceKeys((Map<String, Object>) item) : item)
                                .toList();
                        map.put(updatedKey, updatedList);
                    } else {
                        map.put(updatedKey, value); // 直接添加非 Map 和非 List 的值
                    }
                }, LinkedHashMap::putAll);
    }

    private void mergeYaml(ConfigurationSection originalConfig, Map<String, Object> newMap) {
        newMap.forEach((key, value) -> {
            String originalKey = key.replace("_", "-");
            // 对象列表转为字符串列表
            if (originalKey.equals("banned-peer-id") || originalKey.equals("banned-client-name")) {
                if (value instanceof List<?> list) {
                    List<String> parsedList = list.stream()
                            .filter(item -> item instanceof Map)
                            .map(GSON::toJson)
                            .toList();
                    originalConfig.set(originalKey, parsedList);
                }
            }
            // 如果值是 Map，递归替换子 Map 中的键
            else if (value instanceof Map<?, ?> map) {
                ConfigurationSection section = Optional.ofNullable(originalConfig.getConfigurationSection(originalKey))
                        .orElseGet(() -> originalConfig.createSection(originalKey));
                mergeYaml(section, (Map<String, Object>) map);
                originalConfig.set(originalKey, section);
            }
            // 如果值是 List 或者其他，直接替换
            else {
                originalConfig.set(originalKey, value); // 直接添加
            }
        });
    }

    @Override
    public void onDisable() {

    }

    public record ReloadEntry(
            String reloadable,
            String reloadResult
    ) {
    }

}
