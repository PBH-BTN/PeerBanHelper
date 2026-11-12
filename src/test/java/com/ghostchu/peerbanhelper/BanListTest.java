package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.banlist.BanlistRuleProviderRegistry;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import inet.ipaddr.IPAddressString;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanListTest {
    @Test
    void keepsPrimaryAndPrefilledViewsSeparate() {
        PrimaryBanList primaryBanList = new PrimaryBanList();
        PrefilledBanList prefilledBanList = new PrefilledBanList();
        BanList banList = new BanList(primaryBanList, prefilledBanList, new BanlistRuleProviderRegistry());

        var primaryAddress = new IPAddressString("192.0.2.10").getAddress();
        var prefilledAddress = new IPAddressString("198.51.100.20").getAddress();
        primaryBanList.directAccess(true, trie -> trie.put(primaryAddress, new BanMetadata()));
        prefilledBanList.replaceAll(Map.of(prefilledAddress, new BanMetadata()));

        assertEquals(2, banList.size());
        assertEquals(2, banList.toMap().size());
        assertTrue(banList.toPrimaryMap().containsKey(primaryAddress));
        assertTrue(banList.toPrefilledMap().containsKey(prefilledAddress));

        AtomicInteger entries = new AtomicInteger();
        banList.forEach((address, metadata) -> entries.incrementAndGet());
        assertEquals(2, entries.get());

        banList.remove(primaryAddress);
        assertFalse(banList.toPrimaryMap().containsKey(primaryAddress));
        assertTrue(banList.toPrefilledMap().containsKey(prefilledAddress));
        assertEquals(1, banList.size());
    }
}
