package com.ghostchu.peerbanhelper.util.rule.matcher;

import com.ghostchu.peerbanhelper.Main;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.format.util.DualIPv4v6AssociativeTries;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

class SubscriptionIPMatcherTest {
    @Test
    void compressedHostsPreserveMatchingCommentsAndRefresh() {
        try (var main = mockStatic(Main.class)) {
            main.when(Main::getMainConfig).thenReturn(new YamlConfiguration());
            var data = new DualIPv4v6AssociativeTries<String>();
            var entries = List.of("0.0.0.0", "9.0.0.0/8", "9.1.2.3", "126.0.0.1", "128.0.0.0/32",
                    "192.0.2.9", "255.255.255.255", "2001:db8::/48", "2001:db8::55");
            for (var entry : entries) data.put(new IPAddressString(entry).getAddress(), entry);
            var expected = new IPMatcher("test", "original", List.of(data));
            var actual = new IPMatcher.Subscription("test", "compact", List.of(data));
            assertEquals(expected.size(), actual.size());
            for (var text : List.of("0.0.0.0", "9.1.2.3", "9.1.2.4", "126.0.0.1", "128.0.0.0",
                    "192.0.2.9", "255.255.255.255", "127.0.0.1", "2001:db8::55", "2001:db8::56", "::1")) {
                var reference = expected.match0(text);
                var result = actual.match0(text);
                assertEquals(reference.result(), result.result(), text);
                assertEquals(reference.comment().getKey(), result.comment().getKey(), text);
            }
            actual.setData("empty", List.of(new DualIPv4v6AssociativeTries<>()));
            assertEquals(0, actual.size());
        }
    }
}
