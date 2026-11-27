package com.ghostchu.peerbanhelper.util.scriptengine;

import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScriptSnapshotTest {
    @Test
    void exportsPeerDataButNeverDownloaderCredentialsOrServiceObjects() {
        var peer = mock(Peer.class);
        var torrent = mock(Torrent.class);
        var downloader = mock(Downloader.class);
        when(peer.getPeerAddress()).thenReturn(new PeerAddress("2001:db8::1", 6881, "2001:db8::1"));
        when(downloader.getId()).thenReturn("primary");
        when(downloader.getName()).thenReturn("My client");
        when(downloader.getType()).thenReturn("qbittorrent");
        var snapshot = ScriptSnapshot.create(torrent, peer, downloader, 15_000);
        assertEquals(Set.of("peer", "torrent", "downloader", "banDuration"), snapshot.keySet());
        assertEquals(Map.of("id", "primary", "name", "My client", "type", "qbittorrent"), snapshot.get("downloader"));
        var peerData = (Map<?, ?>) snapshot.get("peer");
        assertNull(peerData.get("flags"));
        assertEquals(Map.of("address", "2001:db8::1", "ip", "2001:db8::1", "port", 6881), peerData.get("peerAddress"));
        assertDataOnly(snapshot);
    }

    private static void assertDataOnly(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Object member : map.values()) assertDataOnly(member);
        } else {
            assertTrue(value == null || value instanceof String || value instanceof Boolean || value instanceof Number);
        }
    }
}
