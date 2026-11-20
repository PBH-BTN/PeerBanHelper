package com.ghostchu.peerbanhelper;

import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import inet.ipaddr.IPAddressString;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BanListRangeTest {
    @Test
    void onlyReportsLiveSourcesInTheSameSubnet() {
        var bans = new BanList();
        var source = new IPAddressString("192.0.2.12").getAddress();
        var ignored = new IPAddressString("192.0.2.1").getAddress();
        var peer = new IPAddressString("192.0.2.225").getAddress();
        var v6 = new IPAddressString("2001:db8:ab::123").getAddress();
        var disconnect = mock(BanMetadata.class);
        when(disconnect.isBanForDisconnect()).thenReturn(true);
        var ban = mock(BanMetadata.class);
        bans.addAll(Map.of(source, ban, ignored, disconnect, v6, ban));
        assertEquals(source, bans.findRangeBanSource(peer, 24));
        assertNull(bans.findRangeBanSource(peer, 25));
        assertEquals(v6, bans.findRangeBanSource(new IPAddressString("2001:db8:ab::234").getAddress(), 64));
        bans.remove(source);
        assertNull(bans.findRangeBanSource(peer, 24));
        bans.reset();
        assertNull(bans.findRangeBanSource(v6, 64));
    }
}
