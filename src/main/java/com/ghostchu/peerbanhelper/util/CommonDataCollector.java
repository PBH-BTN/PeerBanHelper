package com.ghostchu.peerbanhelper.util;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.traversal.btstun.StunManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.net.Proxy;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CommonDataCollector {
    @Autowired
    private HTTPUtil httpUtil;
    @Autowired
    private StunManager bTStunManager;
    @Autowired
    private SystemInfo systemInfo;

    public Map<String, Object> generatePbhData() {
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
        pbh.put("gui_available", Main.getGuiManager().isGuiAvailable());
        pbh.put("default_locale", Main.DEF_LOCALE);
        return pbh;
    }

    public Map<String, Object> generateSystemData() {
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
        var network = generateNetworkStats();
        os.put("network", network);
        return os;
    }

    public Map<String, Object> generateNetworkStats() {
        Map<String, Object> network = new LinkedHashMap<>();
        network.put("internet_access", httpUtil.getNetworkReachability());
        network.put("use_proxy", httpUtil.getProxyType() != Proxy.Type.DIRECT);
        network.put("nat_type", bTStunManager.getCachedNatType().name());
        return network;
    }

    public Map<String, Object> generateJvmData() {
        var runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("version", runtimeMXBean.getVmVersion());
        jvm.put("vendor", runtimeMXBean.getVmVendor());
        jvm.put("runtime", runtimeMXBean.getVmName());
        jvm.put("bitness", systemInfo.getOperatingSystem().getBitness());
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

}
