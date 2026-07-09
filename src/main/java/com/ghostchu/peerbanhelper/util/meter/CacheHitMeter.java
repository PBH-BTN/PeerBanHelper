package com.ghostchu.peerbanhelper.util.meter;

import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.observable.ReportGenerator;
import com.google.common.cache.Cache;
import org.springframework.stereotype.Component;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class CacheHitMeter implements ReportGenerator {
    private final Map<String, WeakReference<Cache<?, ?>>> registeredCache = Collections.synchronizedMap(new HashMap<>());

    public CacheHitMeter() {
        CommonUtil.getScheduler().scheduleAtFixedRate(this::cleanup, 30, 30, TimeUnit.MINUTES);
    }

    private void cleanup() {
        registeredCache.entrySet().removeIf(entry -> entry.getValue().get() == null);
    }

    public void register(String name, Cache<?, ?> cacheObject) {
        registeredCache.put(name, new WeakReference<>(cacheObject));
    }

    @Override
    public Map<String, Object> createReportJsonObject() {
        Map<String, Object> report = new LinkedHashMap<>();
        var it = registeredCache.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var key = entry.getKey();
            var value = entry.getValue().get();
            if (value == null) {
                it.remove();
                continue;
            }
            report.put(key, value.stats());
        }
        return report;
    }
}
