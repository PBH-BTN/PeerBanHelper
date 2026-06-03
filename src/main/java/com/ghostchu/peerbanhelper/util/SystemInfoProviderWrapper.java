package com.ghostchu.peerbanhelper.util;

import lombok.extern.slf4j.Slf4j;
import oshi.spi.SystemInfoFactory;
import oshi.spi.SystemInfoProvider;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class SystemInfoProviderWrapper {
    private static final AtomicBoolean AVAILABLE = new AtomicBoolean(false);
    private static final AtomicReference<SystemInfoProvider> PROVIDER_INSTANCE = new AtomicReference<>(null);

    static {
        try {
            // 进行两个 API 测试，确保可用
            SystemInfoProvider provider = SystemInfoFactory.create();
            provider.getOperatingSystem().getBitness();
            provider.getHardware().getMemory().getAvailable();
            PROVIDER_INSTANCE.set(provider);
            AVAILABLE.set(true);
            log.debug("Oshi loaded.");
        } catch (Throwable th) {
            AVAILABLE.set(false);
            PROVIDER_INSTANCE.set(null);
            log.debug("Error: Oshi not available on this platform.", th);
        }
    }

    public static boolean isAvailable() {
        return AVAILABLE.get();
    }

    public static Optional<SystemInfoProvider> find() {
        return Optional.ofNullable(PROVIDER_INSTANCE.get());
    }
}
