package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
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

import javax.management.MBeanServer;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

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
                .post("/api/general/heapdump", this::handleHeapDump, Role.USER_READ)
                .post("/api/general/reload", this::handleReloading, Role.USER_READ)
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
        var osMXBean = ManagementFactory.getOperatingSystemMXBean();
        var runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        var threadsMXBean = ManagementFactory.getThreadMXBean();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> os = new HashMap<>();
        os.put("name", osMXBean.getName());
        os.put("version", osMXBean.getVersion());
        os.put("availableProcessors", osMXBean.getAvailableProcessors());
        os.put("arch", osMXBean.getArch());
        os.put("systemLoadAverage", osMXBean.getSystemLoadAverage());
        data.put("os", os);
        Map<String, Object> jvm = new HashMap<>();
        jvm.put("specification", runtimeMXBean.getSpecName());
        jvm.put("version", runtimeMXBean.getVmVersion());
        jvm.put("vendor", runtimeMXBean.getVmVendor());
        jvm.put("classVersion", System.getProperty("java.class.version"));
        jvm.put("installDir", System.getProperty("java.home"));
        jvm.put("tmpDir", System.getProperty("java.io.tmpdir"));
        data.put("jvm", jvm);
        data.put("heapMemory", generateMemoryData(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage()));
        data.put("nonHeapMemory", generateMemoryData(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage()));
        Map<String, Object> pbh = new HashMap<>();
        pbh.put("dataDir", Main.getDataDirectory().getAbsolutePath());
        pbh.put("userAgent", Main.getUserAgent());
        pbh.put("startupArgs", String.join(" ", Main.getStartupArgs()));
        pbh.put("guiAvailable", Main.getGuiManager().isGuiAvailable());
        pbh.put("defaultLocale", Main.DEF_LOCALE);
        data.put("peerbanhelper", pbh);
        Map<String, Object> threads = new HashMap<>();
        threads.put("count", threadsMXBean.getThreadCount());
        threads.put("daemonCount", threadsMXBean.getDaemonThreadCount());
        threads.put("peakCount", threadsMXBean.getPeakThreadCount());
        threads.put("totalStartedThreadCount", threadsMXBean.getTotalStartedThreadCount());
        threads.put("details", Arrays.stream(threadsMXBean.dumpAllThreads(true, true)).map(ThreadInfo::toString).collect(Collectors.joining("\n\n")));
        data.put("threads", threads);
        context.json(new StdResp(true, null, data));
    }


    private Map<String, Object> generateMemoryData(MemoryUsage heapMemoryMXBean) {
        Map<String, Object> nonHeapMem = new HashMap<>();
        nonHeapMem.put("init", heapMemoryMXBean.getInit());
        nonHeapMem.put("max", heapMemoryMXBean.getMax());
        nonHeapMem.put("used", heapMemoryMXBean.getUsed());
        nonHeapMem.put("committed", heapMemoryMXBean.getCommitted());
        return nonHeapMem;
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
        context.json(new StdResp(true, null, entryList));
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
