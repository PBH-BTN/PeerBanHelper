package com.ghostchu.peerbanhelper.configuration.pf4j;

import com.ghostchu.peerbanhelper.DownloaderServerImpl;
import com.ghostchu.peerbanhelper.PeerBanHelper;
import com.ghostchu.peerbanhelper.alert.AlertManager;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.banpipeline.data.CheckResultBatch;
import com.ghostchu.peerbanhelper.banpipeline.data.FetchedPeersBatch;
import com.ghostchu.peerbanhelper.banpipeline.organ.RunCheckModuleOrgan;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.*;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.format.util.DualIPv4v6Tries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.PluginState;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PluginLifecycleTest {
    private static final String DEMO = "demo-client-rule";
    @TempDir Path plugins;
    private final AnnotationConfigApplicationContext host = new AnnotationConfigApplicationContext();
    private PBHSpringPluginManager manager;
    private ModuleManagerImpl modules;

    @BeforeEach
    void setUp() throws IOException {
        Files.copy(Path.of(System.getProperty("pbh.test.demoJar")), plugins.resolve("demo.jar"));
        host.getBeanFactory().registerSingleton("server", mock(PeerBanHelper.class));
        host.getBeanFactory().registerSingleton("web", mock(JavalinWebContainer.class));
        host.getBeanFactory().registerSingleton("cache", mock(ModuleMatchCache.class));
        host.registerBean(ModuleManagerImpl.class, () -> spy(new ModuleManagerImpl()));
        host.registerBean(PBHSpringPluginManager.class,
                () -> new PBHSpringPluginManager(plugins, "9.6.0", host.getBean(ModuleManagerImpl.class)));
        host.refresh();
        manager = host.getBean(PBHSpringPluginManager.class);
        modules = host.getBean(ModuleManagerImpl.class);
        assertTrue(manager.getPlugins().isEmpty(), "Refreshing Spring must not execute plugins");
    }

    @AfterEach
    void close() {
        if (manager != null) {
            manager.stopPlugins();
            for (var plugin : manager.getPlugins()) manager.unloadPlugin(plugin.getPluginId());
        }
        host.close();
    }

    @Test
    void packagedRuleExecutesAndOldSnapshotsStayRetired() {
        manager.loadPlugins();
        manager.startPlugins();
        assertEquals(PluginState.STARTED, manager.getPlugin(DEMO).getPluginState());
        var invocation = modules.getInvocations(RuleFeatureModule.class).getFirst();
        var peer = mock(Peer.class);
        when(peer.getClientName()).thenReturn("PeerBanHelper-Demo/1.0", "ordinary-client", null);
        var result = new AtomicReference<CheckResult>();
        invocation.invoke(rule -> result.set(rule.shouldBanPeer(null, peer, null, null)));
        assertEquals(PeerAction.BAN, result.get().action());
        assertEquals(60_000, result.get().duration());
        assertSame(manager.getPluginClassLoader(DEMO), result.get().moduleContext().getClassLoader());
        for (int i = 0; i < 2; i++) {
            invocation.invoke(rule -> result.set(rule.shouldBanPeer(null, peer, null, null)));
            assertEquals(PeerAction.NO_ACTION, result.get().action());
        }
        assertSame(invocation.module(), manager.getExtensions(RuleFeatureModule.class, DEMO).getFirst());
        assertTrue(host.getBeansOfType(RuleFeatureModule.class).isEmpty());
        assertEquals(PluginState.STOPPED, manager.stopPlugin(DEMO));
        assertTrue(modules.getModules().isEmpty());
        manager.startPlugin(DEMO);
        manager.startPlugin(DEMO);
        assertEquals(1, modules.getModules().size());
        assertNotSame(invocation.module(), modules.getModules().getFirst());
        invocation.invoke(rule -> fail("A queued callback survived restart"));
        assertTrue(manager.unloadPlugin(DEMO));
        assertTrue(modules.getModules().isEmpty());
    }

    @Test
    void firstPeerBatchRunsTheInstalledRule() throws Exception {
        manager.loadPlugins();
        manager.startPlugins();
        var server = mock(DownloaderServerImpl.class);
        when(server.getIgnoreAddresses()).thenReturn(new DualIPv4v6Tries());
        var peer = mock(Peer.class);
        when(peer.getClientName()).thenReturn("PeerBanHelper-Demo/1.0");
        var address = mock(PeerAddress.class);
        when(address.getAddress()).thenReturn(new IPAddressString("192.0.2.1").getAddress());
        when(peer.getPeerAddress()).thenReturn(address);
        var batch = new FetchedPeersBatch(mock(Downloader.class), mock(Torrent.class), List.of(peer));
        var organ = new RunCheckModuleOrgan(Runnable::run, Runnable::run, null, null,
                5, TimeUnit.SECONDS, modules, mock(AlertManager.class), server);
        try {
            var result = new CompletableFuture<CheckResultBatch>();
            organ.digest(batch, result::complete, new PipelineTask<>(null, organ, "first batch"));
            assertEquals(PeerAction.BAN, result.get(5, TimeUnit.SECONDS).checkResult().action());
        } finally {
            organ.endSession();
        }
    }

    @Test
    void registrationFailureRollsBackAndCanBeRetried() {
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("failure after module enable");
        }).doCallRealMethod().when(modules).register(any(FeatureModule.class), anyString());
        manager.loadPlugins();
        assertEquals(PluginState.FAILED, manager.startPlugin(DEMO));
        assertEquals("failure after module enable", manager.getPlugin(DEMO).getFailedException().getMessage());
        assertTrue(modules.getModules().isEmpty());
        assertTrue(manager.getStartedPlugins().isEmpty());
        assertEquals(PluginState.STARTED, manager.startPlugin(DEMO));
        assertEquals(1, modules.getModules().size());
    }

    @Test
    void stoppingWaitsForAnInFlightRule() throws Exception {
        manager.loadPlugins();
        manager.startPlugins();
        var invocation = modules.getInvocations(RuleFeatureModule.class).getFirst();
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var call = executor.submit(() -> invocation.invoke(rule -> {
                entered.countDown();
                try {
                    assertTrue(finish.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var stop = executor.submit(() -> manager.stopPlugin(DEMO));
            try {
                assertThrows(java.util.concurrent.TimeoutException.class, () -> stop.get(100, TimeUnit.MILLISECONDS));
            } finally {
                finish.countDown();
            }
            call.get(5, TimeUnit.SECONDS);
            assertEquals(PluginState.STOPPED, stop.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void brokenPluginsDoNotPreventHealthyRulesFromStarting() throws IOException {
        descriptor("missing", "plugin.dependencies", "absent");
        descriptor("cycle-a", "plugin.dependencies", "cycle-b");
        descriptor("cycle-b", "plugin.dependencies", "cycle-a");
        descriptor("future", "plugin.requires", ">=100.0.0");
        descriptor("broken", "plugin.class", "missing.Entry");
        descriptor("dependent", "plugin.dependencies", "broken");
        manager.loadPlugins();
        manager.startPlugins();
        assertEquals(PluginState.STARTED, manager.getPlugin(DEMO).getPluginState());
        assertEquals(PluginState.DISABLED, manager.getPlugin("future").getPluginState());
        for (String id : new String[]{"missing", "broken", "dependent", "cycle-a", "cycle-b"}) {
            assertNotEquals(PluginState.STARTED, manager.getPlugin(id).getPluginState(), id);
            assertNotNull(manager.getPlugin(id).getFailedException(), id);
        }
        descriptor("absent", "plugin.description", "Newly installed dependency");
        manager.loadPlugin(plugins.resolve("absent.jar"));
        assertEquals(PluginState.STARTED, manager.startPlugin("missing"));
    }

    private void descriptor(String id, String key, String value) throws IOException {
        var props = new Properties();
        props.setProperty("plugin.id", id);
        props.setProperty("plugin.version", "1.0.0");
        props.setProperty(key, value);
        try (var jar = new JarOutputStream(Files.newOutputStream(plugins.resolve(id + ".jar")))) {
            jar.putNextEntry(new JarEntry("plugin.properties"));
            props.store(jar, null);
        }
    }
}
