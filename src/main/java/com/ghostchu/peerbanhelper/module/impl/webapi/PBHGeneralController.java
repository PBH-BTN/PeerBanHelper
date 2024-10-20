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
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.InternetProtocolStats;

import javax.management.MBeanServer;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.util.*;

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
        Map<String, Object> data = new HashMap<>();
        data.put("os", generateOsData(systemInfo));
        data.put("network", generateNetworkStats(systemInfo));
        //data.put("networkInterface", generateIf(systemInfo));
        data.put("jvm", generateJvmData());
        data.put("heapMemory", generateMemoryData(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage()));
        data.put("nonHeapMemory", generateMemoryData(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage()));
        data.put("systemMemory", generateSystemMemoryData(systemInfo.getHardware()));
        data.put("peerbanhelper", generatePbhData());
        data.put("threads", generateThreadsData());
        data.put("cpu", generateProcessorData(systemInfo));
        data.put("sensor", generateSensorData(systemInfo));
        context.json(new StdResp(true, null, data));
    }

    private Map<String, Object> generateSensorData(SystemInfo systemInfo) {
        Map<String, Object> data = new HashMap<>();
        var sensors = systemInfo.getHardware().getSensors();
        data.put("cpuTemp", sensors.getCpuTemperature());
        data.put("cpuVoltage", sensors.getCpuVoltage());
        data.put("fanSpeed", String.join(", ", Arrays.stream(sensors.getFanSpeeds()).mapToObj(String::valueOf).toList()));
        return data;
    }

    private Map<String, Object> generateProcessorData(SystemInfo systemInfo) {
        Map<String, Object> data = new HashMap<>();
        var cpu = systemInfo.getHardware().getProcessor();
        //data.put("contextSwitches", cpu.getContextSwitches());
        data.put("currentFreq", String.join(", ", Arrays.stream(cpu.getCurrentFreq()).mapToObj(String::valueOf).toList()));
        //data.put("interrupts", cpu.getInterrupts());
        data.put("logicalProcessorCount", cpu.getLogicalProcessorCount());
        data.put("maxFreq", cpu.getMaxFreq());
        data.put("physicalPackageCount", cpu.getPhysicalPackageCount());
        data.put("physicalProcessorCount", cpu.getPhysicalProcessorCount());
        data.put("cpuLoadTicks", String.join(", ", Arrays.stream(cpu.getProcessorCpuLoadTicks()[0]).mapToObj(String::valueOf).toList()));
        var id = cpu.getProcessorIdentifier();
        data.put("family", id.getFamily());
        data.put("identifier", id.getIdentifier());
        data.put("microArch", id.getMicroarchitecture());
        data.put("model", id.getModel());
        data.put("name", id.getName());
        data.put("processorId", id.getProcessorID());
        data.put("stepping", id.getStepping());
        data.put("vendor", id.getVendor());
        data.put("vendorFreq", id.getVendorFreq());
        return data;
    }
//
//    private List<Map<String, Object>> generateIf(SystemInfo systemInfo) {
//        List<Map<String, Object>> nifs = new ArrayList<>();
//        var ifs = systemInfo.getHardware().getNetworkIFs();
//        ifs.forEach(nif -> {
//            Map<String, Object> ifdat = new HashMap<>();
//            ifdat.put("name", nif.getName());
//            ifdat.put("displayName", nif.getDisplayName());
//            ifdat.put("recvBytes", nif.getBytesRecv());
//            ifdat.put("sentBytes", nif.getBytesSent());
//            ifdat.put("collisions", nif.getCollisions());
//            ifdat.put("alias", nif.getIfAlias());
//            ifdat.put("operStatus", nif.getIfOperStatus().getValue());
//            ifdat.put("index", nif.getIndex());
//            ifdat.put("inDrops", nif.getInDrops());
//            ifdat.put("inErrors", nif.getInErrors());
//            ifdat.put("addrs4", String.join(", ", nif.getIPv4addr()));
//            ifdat.put("addrs6", String.join(", ", nif.getIPv6addr()));
//            ifdat.put("macaddr", nif.getMacaddr());
//            ifdat.put("ifType", nif.getIfType());
//            // ifdat.put("mtu", nif.getMTU());
//            ifdat.put("nDisPhysicalMediumType", nif.getNdisPhysicalMediumType());
//            ifdat.put("outErrors", nif.getOutErrors());
//            ifdat.put("recvPackets", nif.getPacketsRecv());
//            ifdat.put("sentPackets", nif.getPacketsSent());
//            ifdat.put("prefixLengths", String.join(", ", Arrays.stream(nif.getPrefixLengths()).map(String::valueOf).toList()));
//            ifdat.put("speed", nif.getSpeed());
//            ifdat.put("subnetMasks", String.join(", ", Arrays.stream(nif.getSubnetMasks()).map(String::valueOf).toList()));
//            ifdat.put("timestamp", nif.getTimeStamp());
//            nifs.add(ifdat);
//        });
//        return nifs;
//    }

    private Map<String, Object> generateThreadsData() {
        var threadsMXBean = ManagementFactory.getThreadMXBean();
        Map<String, Object> threads = new HashMap<>();
        threads.put("count", threadsMXBean.getThreadCount());
        threads.put("daemonCount", threadsMXBean.getDaemonThreadCount());
        threads.put("peakCount", threadsMXBean.getPeakThreadCount());
        threads.put("totalStartedThreadCount", threadsMXBean.getTotalStartedThreadCount());
        return threads;
    }

    private Map<String, Object> generatePbhData() {
        Map<String, Object> pbh = new HashMap<>();
        pbh.put("dataDir", Main.getDataDirectory().getAbsolutePath());
        pbh.put("userAgent", Main.getUserAgent());
        pbh.put("startupArgs", String.join(" ", Main.getStartupArgs()));
        pbh.put("guiAvailable", Main.getGuiManager().isGuiAvailable());
        pbh.put("defaultLocale", Main.DEF_LOCALE);
        return pbh;
    }

    private Map<String, Object> generateOsData(SystemInfo systemInfo) {
        var osMXBean = ManagementFactory.getOperatingSystemMXBean();
        var operatingSystem = systemInfo.getOperatingSystem();
        Map<String, Object> os = new HashMap<>();
        os.put("name", osMXBean.getName());
        os.put("version", osMXBean.getVersion());
        os.put("availableProcessors", osMXBean.getAvailableProcessors());
        os.put("arch", osMXBean.getArch());
        os.put("systemLoadAverage", osMXBean.getSystemLoadAverage());
        os.put("family", operatingSystem.getFamily());
        os.put("bitness", operatingSystem.getBitness());
        os.put("bootTime", operatingSystem.getSystemBootTime());
        os.put("upTime", operatingSystem.getSystemUptime());
        os.put("manufacturer", operatingSystem.getManufacturer());
        os.put("processCount", operatingSystem.getProcessCount());
        var versionInfo = operatingSystem.getVersionInfo();
        os.put("buildNumber", versionInfo.getBuildNumber());
        os.put("codeName", versionInfo.getCodeName());
        os.put("osVersion", versionInfo.getVersion());
        return os;
    }

    private Map<String, Object> generateNetworkStats(SystemInfo systemInfo) {
        var operatingSystem = systemInfo.getOperatingSystem();
        Map<String, Object> network = new HashMap<>();
        var networkParams = operatingSystem.getNetworkParams();
        var tcpipStats = operatingSystem.getInternetProtocolStats();
        network.put("hostname", networkParams.getHostName());
        network.put("dns", String.join(", ", networkParams.getDnsServers()));
        network.put("domainName", networkParams.getDomainName());
        network.put("ipv4DefaultGateway", networkParams.getIpv4DefaultGateway());
        network.put("ipv6DefaultGateway", networkParams.getIpv6DefaultGateway());
        network.put("tcp4Stats", generateTcpStats(tcpipStats.getTCPv4Stats()));
        network.put("tcp6Stats", generateTcpStats(tcpipStats.getTCPv6Stats()));
        network.put("udp4Stats", generateUdpStats(tcpipStats.getUDPv4Stats()));
        network.put("udp6Stats", generateUdpStats(tcpipStats.getUDPv6Stats()));
        return network;
    }

    private Map<String, Object> generateJvmData() {
        var runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> jvm = new HashMap<>();
        jvm.put("specification", runtimeMXBean.getSpecName());
        jvm.put("version", runtimeMXBean.getVmVersion());
        jvm.put("vendor", runtimeMXBean.getVmVendor());
        jvm.put("classVersion", System.getProperty("java.class.version"));
        jvm.put("installDir", System.getProperty("java.home"));
        jvm.put("tmpDir", System.getProperty("java.io.tmpdir"));
        return jvm;
    }

    private Map<String, Object> generateSystemMemoryData(HardwareAbstractionLayer hardware) {
        Map<String, Object> data = new HashMap<>();
        var mem = hardware.getMemory();
        data.put("available", mem.getAvailable());
        data.put("pageSize", mem.getPageSize());
        data.put("total", mem.getTotal());
        return data;
    }


    private Map<String, Object> generateUdpStats(InternetProtocolStats.UdpStats stats) {
        Map<String, Object> data = new HashMap<>();
        data.put("datagramsNoPort", stats.getDatagramsNoPort());
        data.put("datagramsReceived", stats.getDatagramsReceived());
        data.put("datagramsReceivedErrors", stats.getDatagramsReceivedErrors());
        data.put("datagramsSent", stats.getDatagramsSent());
        return data;
    }

    private Map<String, Object> generateTcpStats(InternetProtocolStats.TcpStats stats) {
        Map<String, Object> data = new HashMap<>();
        data.put("connectionFailures", stats.getConnectionFailures());
        data.put("connectionActive", stats.getConnectionsActive());
        data.put("connectionEstablished", stats.getConnectionsEstablished());
        data.put("connectionPassive", stats.getConnectionsPassive());
        data.put("connectionReset", stats.getConnectionsReset());
        data.put("inErrors", stats.getInErrors());
        data.put("outResets", stats.getOutResets());
        data.put("segmentsReceived", stats.getSegmentsReceived());
        data.put("segmentsRetransmitted", stats.getSegmentsRetransmitted());
        data.put("segmentsSent", stats.getSegmentsSent());
        return data;
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
