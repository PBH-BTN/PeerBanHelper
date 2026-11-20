package com.ghostchu.peerbanhelper.databasent.service;

import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.ghostchu.peerbanhelper.util.iocache.PBHCache;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PeerBatchLookupTest {
    @Test
    void loadsMissingRowsTogetherAndRecoversEvictedState() throws Exception {
        var queries = new AtomicInteger();
        var persisted = new ArrayList<String>();
        try (var scheduler = mockStatic(CommonUtil.class)) {
            scheduler.when(CommonUtil::getScheduler).thenReturn(mock(ScheduledExecutorService.class));
            try (var cache = new PBHCache<Integer, String>(1, null, null, false, false, false,
                    entries -> entries.forEach(e -> persisted.add(e.getRight())))) {
                var lookup = new PeerBatchLookup<>(cache, () -> List.of(1, 2), keys -> {
                    assertEquals(Set.of(1, 2), keys);
                    queries.incrementAndGet();
                    return Map.of(1, "first");
                });
                assertEquals("first", cache.getPendingOrLoad(1, () -> lookup.load(1, () -> fail("unexpected query"))));
                assertEquals("second", cache.getPendingOrLoad(2, () -> {
                    assertNull(lookup.load(2, () -> fail("unexpected query")));
                    return "second";
                }));
                assertEquals("first", cache.getPendingOrLoad(1, () -> fail("evicted state lost")));
                assertEquals(1, queries.get());
            }
        }
        assertTrue(persisted.containsAll(List.of("first", "second")));
    }

    @Test
    void failedWritesBoundNewAdmissionsWithoutDiscardingPendingValues() throws Exception {
        var unavailable = new AtomicBoolean(true);
        var written = new ArrayList<String>();
        try (var scheduler = mockStatic(CommonUtil.class)) {
            scheduler.when(CommonUtil::getScheduler).thenReturn(mock(ScheduledExecutorService.class));
            try (var cache = new PBHCache<Integer, String>(1, null, null, false, false, false, rows -> {
                if (unavailable.get()) throw new IllegalStateException("write failed");
                rows.forEach(row -> written.add(row.getRight()));
            })) {
                cache.getPendingOrLoad(1, () -> "first");
                cache.getPendingOrLoad(2, () -> "second");
                assertThrows(com.google.common.util.concurrent.UncheckedExecutionException.class,
                        () -> cache.getPendingOrLoad(3, () -> fail("database backlog is full")));
                unavailable.set(false);
                assertEquals("third", cache.getPendingOrLoad(3, () -> "third"));
            }
        }
        assertEquals(List.of("first", "second", "third"), written);
    }
}
