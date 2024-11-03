package com.ghostchu.peerbanhelper.module.impl.registry;

import com.ghostchu.peerbanhelper.module.Runner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
@Slf4j
public class RunnerRegistryManager {
    private final Map<Class<? extends Runner>, List<Supplier<?>>> registry = new ConcurrentHashMap<>();

    public <Pack> void register(String description, Class<? extends Runner> clazz, Supplier<List<Pack>> supplier) {
        List<Supplier<?>> suppliers = registry.computeIfAbsent(clazz, k -> Collections.synchronizedList(new ArrayList<>()));
        suppliers.add(supplier);
    }

    public <Pack> void unregister(Class<? extends Runner> clazz, Supplier<List<Pack>> supplier) {
        List<Supplier<?>> suppliers = registry.get(clazz);
        if (suppliers != null) {
            suppliers.remove(supplier);
        }
    }

    public void unregisterAll(Class<? extends Runner> clazz) {
        registry.remove(clazz);
    }

    public <Pack> List<Pack> get(Class<? extends Runner> clazz, Class<Pack> packClass) {
        List<Supplier<?>> suppliers = registry.get(clazz);
        if (suppliers == null) {
            return Collections.emptyList();
        }
        List<Pack> packs = new LinkedList<>();
        suppliers.forEach(s->{
            try {
                //noinspection unchecked
                packs.addAll((Collection<? extends Pack>) s.get());
            }catch (Exception e){
                log.error("Unable to collect packs from supplier {}", s.getClass().getName(), e);
            }
        });
        return packs;
    }
}
