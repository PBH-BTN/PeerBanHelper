package com.ghostchu.peerbanhelper.platform.firewall;

import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirewallManagerTest {
    @Test
    void delegatesOperationsAndClosesOnlyOnce() throws Exception {
        FakeFirewall delegate = new FakeFirewall();
        FirewallManager manager = new FirewallManager(delegate);
        InetAddress address = InetAddress.ofLiteral("192.0.2.40");

        manager.add(Set.of(address));
        assertEquals(Set.of(address), manager.addresses());
        manager.replace(Set.of());
        assertTrue(manager.addresses().isEmpty());
        manager.close();
        manager.close();

        assertEquals(1, delegate.closeCount.get());
        assertThrows(IllegalStateException.class, manager::addresses);
    }

    private static final class FakeFirewall implements Firewall {
        private final LinkedHashSet<InetAddress> addresses = new LinkedHashSet<>();
        private final AtomicInteger closeCount = new AtomicInteger();

        @Override
        public @NotNull FirewallCapabilities capabilities() {
            return new FirewallCapabilities(
                    FirewallBackend.UNSUPPORTED,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    false,
                    false,
                    FirewallCrashCleanup.NONE,
                    "test backend"
            );
        }

        @Override
        public @NotNull Set<InetAddress> addresses() {
            return FirewallAddresses.copyOf(addresses);
        }

        @Override
        public void add(@NotNull Collection<? extends InetAddress> addresses) {
            this.addresses.addAll(addresses);
        }

        @Override
        public void remove(@NotNull Collection<? extends InetAddress> addresses) {
            this.addresses.removeAll(addresses);
        }

        @Override
        public void replace(@NotNull Collection<? extends InetAddress> addresses) {
            this.addresses.clear();
            this.addresses.addAll(addresses);
        }

        @Override
        public void clear() {
            addresses.clear();
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }
    }
}
