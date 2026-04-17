package com.ghostchu.peerbanhelper.util.observable;

import com.ghostchu.peerbanhelper.util.Pair;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ReportManager {
    private final List<Pair<String, WeakReference<ReportGenerator>>> registeredGenerator = new CopyOnWriteArrayList<>();

    public void register(String id, ReportGenerator generator) {
        registeredGenerator.add(Pair.of(id, new WeakReference<>(generator)));
    }

    @NotNull
    public String generateReport() {
        Map<String, Object> jsonObject = new LinkedHashMap<>();
        var it = registeredGenerator.iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var key = entry.getKey();
            var value = entry.getValue().get();
            if (value == null) {
                it.remove();
                continue;
            }
            jsonObject.put(key, value.createReportJsonObject());
        }
        return JsonUtil.standard().toJson(jsonObject);
    }

}
