package com.ghostchu.peerbanhelper.module.impl.monitor;

import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.databasent.service.PeerRecordService;
import com.ghostchu.peerbanhelper.databasent.service.impl.common.PeerRecordServiceImpl.PeerRecordCachingEntire;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddressString;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PeerRecordingServiceModuleTest {
    @Test
    void writesWindowEndpointsInOrderAcrossResetsPortChangesAndRollback() throws ReflectiveOperationException {
        var module = new PeerRecordingServiceModule();
        var dao = mock(PeerRecordService.class);
        var transaction = mock(TransactionTemplate.class);
        var writes = new ArrayList<Long>();
        doAnswer(call -> {
            var sample = call.getArgument(0, PeerRecordCachingEntire.class);
            writes.add(sample.getPeer().getUploaded());
            return null;
        }).when(dao).flushToDatabase(any());
        doAnswer(call -> {
            call.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transaction).executeWithoutResult(any());
        set(module, "peerRecordDao", dao);
        set(module, "transactionTemplate", transaction);
        set(module, "accepting", true);
        var downloader = mock(Downloader.class);
        when(downloader.getId()).thenReturn("client");
        var torrent = mock(Torrent.class);
        when(torrent.getHash()).thenReturn("torrent");
        var peer = mock(Peer.class);
        var ip = new IPAddressString("203.0.113.2").getAddress();
        var task = mock(PipelineTask.class);
        for (long upload : List.of(10L, 12L, 15L, 2L, 4L, 7L)) {
            var address = mock(PeerAddress.class);
            when(address.getAddress()).thenReturn(ip);
            when(address.getPort()).thenReturn(upload == 7 ? 3020 : 3010);
            when(peer.getPeerAddress()).thenReturn(address);
            when(peer.getUploaded()).thenReturn(upload);
            module.onPeersRetrieved(downloader, torrent, List.of(peer), task);
        }
        assertEquals(List.of(10L, 15L, 2L, 4L), writes);
        var failing = mock(TransactionTemplate.class);
        doThrow(new IllegalStateException("rollback")).when(failing).executeWithoutResult(any());
        set(module, "transactionTemplate", failing);
        assertThrows(IllegalStateException.class, module::flush);
        set(module, "transactionTemplate", transaction);
        module.onDisable();
        module.flush();
        assertEquals(List.of(10L, 15L, 2L, 4L, 7L), writes);
    }

    private static void set(PeerRecordingServiceModule module, String name, Object value) throws ReflectiveOperationException {
        var field = PeerRecordingServiceModule.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(module, value);
    }
}
