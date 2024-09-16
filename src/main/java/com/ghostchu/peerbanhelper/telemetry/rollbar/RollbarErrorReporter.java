package com.ghostchu.peerbanhelper.telemetry.rollbar;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.PBHShutdownEvent;
import com.ghostchu.peerbanhelper.telemetry.ErrorReporter;
import com.google.common.eventbus.Subscribe;
import com.rollbar.api.payload.data.Person;
import com.rollbar.api.payload.data.Request;
import com.rollbar.notifier.Rollbar;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.rollbar.notifier.config.ConfigBuilder.withAccessToken;

@Component
@Slf4j
public class RollbarErrorReporter implements ErrorReporter {
    private static final String ROLLBAR_ACCESS_TOKEN = "de443230fbf9407385ae82d040afa9ab";
    private final Rollbar rollbar;

    public RollbarErrorReporter() {
        var environment = "production";
        if (Main.getMeta().getAbbrev() == null) {
            environment = "development";
        }
        if (Main.getMeta().getVersion().contains("w")) {
            environment = "development";
        }
        if (Main.getMeta().getVersion().toUpperCase(Locale.ROOT).contains("SNAPSHOT")) {
            environment = "development";
        }
        rollbar = Rollbar.init(withAccessToken(ROLLBAR_ACCESS_TOKEN)
                .environment(environment)
                .platform(System.getProperty("os.name") + "@@" + System.getProperty("os.arch"))
                .codeVersion(Main.getMeta().getVersion() + "/" + Main.getMeta().getAbbrev())
                .person(() -> new Person.Builder().id(Main.getMainConfig().getString("installation-id", "not-initialized")).build())
                .request(() -> new Request.Builder().userIp("anonymize").build())
                .handleUncaughtErrors(false)
                .custom(this::makeMapping)
                //.enabled(Main.getMainConfig().getBoolean("privacy.error-reporting", false))
                .enabled(false) // 有 BUG，先不开
                .build());
        Main.getEventBus().register(this);
    }

    private Map<String, Object> makeMapping() {
        Map<String, Object> dataMapping = new LinkedHashMap<>();
        dataMapping.put("system_os", System.getProperty("os.name"));
        dataMapping.put("system_arch", System.getProperty("os.arch"));
        dataMapping.put("system_version", System.getProperty("os.version"));
        dataMapping.put("system_cores", String.valueOf(Runtime.getRuntime().availableProcessors()));
        dataMapping.put("runtime_version", Runtime.version().toString());
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        var heapMemory = memoryMXBean.getHeapMemoryUsage();
        var nonHeapMemory = memoryMXBean.getNonHeapMemoryUsage();
        dataMapping.put("runtime_mem_heap_init", heapMemory.getInit());
        dataMapping.put("runtime_mem_heap_commited", heapMemory.getCommitted());
        dataMapping.put("runtime_mem_heap_used", heapMemory.getUsed());
        dataMapping.put("runtime_mem_heap_max", heapMemory.getMax());
        dataMapping.put("runtime_mem_offheap_init", nonHeapMemory.getInit());
        dataMapping.put("runtime_mem_offheap_commited", nonHeapMemory.getCommitted());
        dataMapping.put("runtime_mem_offheap_used", nonHeapMemory.getUsed());
        dataMapping.put("runtime_mem_offheap_max", nonHeapMemory.getMax());
        dataMapping.put("data_directory_disk_free_space", Main.getDataDirectory().getUsableSpace());
        return dataMapping;
    }

    @Override
    public void handleUncaughtErrors() {
        rollbar.handleUncaughtErrors();
    }

    @Override
    public void error(Throwable throwable, Map<String, Object> custom) {
        Thread.ofVirtual().start(() -> rollbar.error(throwable, custom));
    }

    @Override
    public void warning(Throwable throwable, Map<String, Object> custom) {
        Thread.ofVirtual().start(() -> rollbar.warning(throwable, custom));
    }

    @Override
    public void error(String description, Map<String, Object> custom) {
        Thread.ofVirtual().start(() -> rollbar.error(description, custom));
    }

    @Override
    public void warning(String description, Map<String, Object> custom) {
        Thread.ofVirtual().start(() -> rollbar.warning(description, custom));
    }

    @SneakyThrows
    @Subscribe
    public void onShutDown(PBHShutdownEvent event) {
        CompletableFuture.runAsync(() -> {
                    try {
                        rollbar.close(true);
                    } catch (Exception e) {
                        log.warn("Unable to upload error-reports to Rollbar");
                    }
                }, Executors.newVirtualThreadPerTaskExecutor())
                .get(5, TimeUnit.SECONDS);
    }
}
