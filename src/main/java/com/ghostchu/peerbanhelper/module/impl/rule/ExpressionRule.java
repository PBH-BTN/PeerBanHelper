package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.ExternalSwitch;
import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.banpipeline.PipelineTask;
import com.ghostchu.peerbanhelper.bittorrent.peer.Peer;
import com.ghostchu.peerbanhelper.bittorrent.torrent.Torrent;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.SharedObject;
import com.ghostchu.peerbanhelper.util.WebUtil;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskManager;
import com.ghostchu.peerbanhelper.util.backgroundtask.BackgroundTaskProgressBarType;
import com.ghostchu.peerbanhelper.util.backgroundtask.FunctionalBackgroundTask;
import com.ghostchu.peerbanhelper.util.query.PBHPage;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.util.scriptengine.CompiledScript;
import com.ghostchu.peerbanhelper.util.scriptengine.ScriptEngineManager;
import com.ghostchu.peerbanhelper.util.scriptengine.ScriptSnapshot;
import com.ghostchu.peerbanhelper.util.scriptengine.sandbox.SandboxProtocol;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public final class ExpressionRule extends AbstractRuleFeatureModule implements Reloadable {
    private final static String VERSION = "2";
    private final ReentrantReadWriteLock policyLock = new ReentrantReadWriteLock();
    private volatile boolean sandbox = true;
    private final JavalinWebContainer javalinWebContainer;
    private final ScriptEngineManager scriptEngineManager;
    private final BackgroundTaskManager backgroundTaskManager;
    private volatile List<CompiledScript> scripts = List.of();
    private long banDuration;
    private final ExecutorService parallelService = Executors.newWorkStealingPool();

    public ExpressionRule(JavalinWebContainer javalinWebContainer, ScriptEngineManager scriptEngineManager, BackgroundTaskManager backgroundTaskManager) {
        super();
        this.scriptEngineManager = scriptEngineManager;
        this.javalinWebContainer = javalinWebContainer;
        this.backgroundTaskManager = backgroundTaskManager;
    }


    @Override
    public void onEnable() {
        // 默认启用脚本编译缓存
        try {
            reloadConfig();
        } catch (Exception e) {
            log.error("Failed to load scripts", e);
            Sentry.captureException(e);
        }
        javalinWebContainer.routes()
                .get("/api/" + getConfigName() + "/scripts", this::listScripts, Role.USER_READ)
                .get("/api/" + getConfigName() + "/engines", ctx -> ctx.json(new StdResp(true, null, scriptEngineManager.getSupportedExtensions())), Role.USER_READ)
                .get("/api/" + getConfigName() + "/editable", this::editable, Role.USER_READ)
                .get("/api/" + getConfigName() + "/{scriptId}", this::readScript, Role.USER_READ)
                .put("/api/" + getConfigName() + "/{scriptId}", this::writeScript, Role.USER_WRITE)
                .delete("/api/" + getConfigName() + "/{scriptId}", this::deleteScript, Role.USER_WRITE);
    }

    private void editable(Context context) {
        Map<String, Object> map = new HashMap<>();
        var editable = isSafeNetworkEnvironment(context);
        map.put("editable", editable);
        map.put("reason", editable ? null : tl(locale(context), Lang.EXPRESS_RULE_ENGINE_DISALLOW_UNSAFE_SOURCE_ACCESS, context.ip()));
        context.json(new StdResp(true, null, map));
    }

    private void deleteScript(Context context) throws IOException {
        if (!isSafeNetworkEnvironment(context)) {
            context.status(HttpStatus.FORBIDDEN);
            context.json(new StdResp(false, tl(locale(context), Lang.EXPRESS_RULE_ENGINE_DISALLOW_UNSAFE_SOURCE_ACCESS, context.ip()), null));
            return;
        }
        var scriptId = context.pathParam("scriptId");
        File readFile = getIfAllowedScriptId(scriptId);
        if (readFile == null) {
            context.status(HttpStatus.BAD_REQUEST);
            context.json(new StdResp(false, "Access to this resource is disallowed", null));
            return;
        }
        if (!readFile.exists()) {
            context.status(HttpStatus.NOT_FOUND);
            context.json(new StdResp(false, "This script are not exists", null));
            return;
        }
        readFile.delete();
        context.json(new StdResp(true, "OK!", null));
        reloadConfig();
    }

    private void writeScript(Context context) throws IOException {
        if (!isSafeNetworkEnvironment(context)) {
            context.status(HttpStatus.FORBIDDEN);
            context.json(new StdResp(false, tl(locale(context), Lang.EXPRESS_RULE_ENGINE_DISALLOW_UNSAFE_SOURCE_ACCESS, context.ip()), null));
            return;
        }
        var scriptId = context.pathParam("scriptId");
        File readFile = getIfAllowedScriptId(scriptId);
        if (readFile == null) {
            context.status(HttpStatus.BAD_REQUEST);
            context.json(new StdResp(false, "Access to this resource is disallowed", null));
            return;
        }
        var content = context.bodyAsBytes();
        var platform = Main.getPlatform();
        if (platform != null) {
            try (var scanner = platform.getMalwareScanner()) {
                if (scanner != null) {
                    if (scanner.isMalicious(context.body())) {
                        context.status(HttpStatus.BAD_REQUEST);
                        context.json(new StdResp(false, tl(locale(context), Lang.MALWARE_SCANNER_DETECTED, "WebAPI-ScriptCreate", scriptId), null));
                        log.error(tlUI(Lang.MALWARE_SCANNER_DETECTED, "WebAPI-ScriptCreate", scriptId));
                        return;
                    }
                }
            } catch (Exception e) {
                log.debug("Malware scan failed for pending to add script", e);
                Sentry.captureException(e);
            }
        }
        var scriptContent = new String(content, StandardCharsets.UTF_8);
        boolean valid;
        var lock = policyLock.readLock();
        lock.lock();
        try {
            valid = content.length <= SandboxProtocol.MAX_FRAME && scriptEngineManager.validateScriptSyntax(scriptId, scriptContent, sandbox);
        } catch (IllegalArgumentException invalidScript) {
            valid = false;
        } catch (IOException unavailable) {
            log.warn("Unable to validate script {}", scriptId, unavailable);
            context.status(HttpStatus.SERVICE_UNAVAILABLE);
            context.json(new StdResp(false, tl(locale(context), Lang.RULE_ENGINE_SANDBOX_UNAVAILABLE), null));
            return;
        } finally {
            lock.unlock();
        }
        if (!valid) {
            context.status(HttpStatus.BAD_REQUEST);
            context.json(new StdResp(false, tl(locale(context), Lang.RULE_ENGINE_BAD_EXPRESSION), null));
            return;
        }
        Files.write(readFile.toPath(), content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        context.json(new StdResp(true, tl(locale(context), Lang.EXPRESS_RULE_ENGINE_SAVED), null));
        reloadConfig();
    }

    private void readScript(Context context) throws IOException {
        var scriptId = context.pathParam("scriptId");
        File readFile = getIfAllowedScriptId(scriptId);
        if (readFile == null) {
            context.status(HttpStatus.BAD_REQUEST);
            context.json(new StdResp(false, "Access to this resource is disallowed", null));
            return;
        }
        if (!readFile.exists()) {
            context.status(HttpStatus.NOT_FOUND);
            context.json(new StdResp(false, "This script are not exists", null));
            return;
        }
        context.json(new StdResp(true, null, Files.readString(readFile.toPath(), StandardCharsets.UTF_8)));
    }

    @Nullable
    private File getIfAllowedScriptId(String scriptId) {
        if (scriptId.isBlank())
            throw new IllegalArgumentException("ScriptId cannot be null or blank");
        if (scriptId.contains("/") || scriptId.contains("\\") || scriptEngineManager.getEngineByFileName(scriptId) == null) {
            return null;
        }
        File scriptDir = new File(Main.getDataDirectory(), "scripts");
        File readFile = new File(scriptDir, scriptId);
        if (insideDirectory(scriptDir, readFile) && !scriptId.contains(":") && !Files.isSymbolicLink(readFile.toPath())) {
            return readFile;
        }
        return null;
    }

    private boolean isSafeNetworkEnvironment(Context context) {
        var value = ExternalSwitch.parse("pbh.please-disable-safe-network-environment-check-i-know-this-is-very-dangerous-and-i-may-lose-my-data-and-hacker-may-attack-me-via-this-endpoint-and-steal-my-data-or-destroy-my-computer-i-am-fully-responsible-for-this-action-and-i-will-not-blame-the-developer-for-any-loss");
        if ("true".equals(value)) {
            return true;
        }
        var ip = IPAddressUtil.getIPAddress(context.ip());
        if (ip == null) {
            throw new IllegalArgumentException("Safe check for IPAddress failed, the IP cannot be null");
        }
        return (ip.isLocal() || ip.isLoopback()) && !WebUtil.isUsingReserveProxy(context);
    }

    private boolean insideDirectory(File allowRange, File targetFile) {
        Path path = allowRange.toPath().normalize().toAbsolutePath();
        Path target = targetFile.toPath().normalize().toAbsolutePath();
        return target.startsWith(path);
    }

    private void listScripts(Context context) {
        Pageable pageable = new Pageable(context);
        List<ExpressionMetadataDto> list = new ArrayList<>();
        Map<File, CompiledScript> loaded = new HashMap<>();
        scripts.forEach(script -> loaded.put(script.file(), script));
        for (File file : scriptFiles()) {
            var script = loaded.get(file);
            list.add(script == null
                    ? new ExpressionMetadataDto(file.getName(), file.getName(), "—", false, false, "—", false)
                    : new ExpressionMetadataDto(file.getName(), script.name(), script.author(), script.cacheable(),
                    script.threadSafe(), script.version(), true));
        }
        var r = list.stream().skip(pageable.getZeroBasedPage() * pageable.getSize()).limit(pageable.getSize()).toList();
        context.json(new StdResp(true, null, new PBHPage<>(pageable.getPage(), pageable.getSize(), list.size(), r)));
    }


    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    @Override
    public @NotNull String getName() {
        return "Expression Engine";
    }

    @Override
    public @NotNull String getConfigName() {
        return "expression-engine";
    }


    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull PipelineTask<?> task) {
        var lock = policyLock.readLock();
        lock.lock();
        try {
            var futures = scripts.stream().map(script -> CompletableFuture.supplyAsync(
                    () -> runExpression(script, torrent, peer, downloader), parallelService)).toList();
            CheckResult result = pass();
            for (var future : futures) {
                var decision = future.join();
                if (decision.action() == PeerAction.SKIP || (decision.action() == PeerAction.BAN && result.action() != PeerAction.SKIP)) {
                    result = decision;
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    private CheckResult runExpression(CompiledScript script, Torrent torrent, Peer peer, Downloader downloader) {
        var cached = getCache().readCacheButWritePassOnly(this, script.scriptHashCode() + ":" + peer.getCacheKey(), () -> {
            CheckResult result;
            try {
                Map<String, Object> env;
                if (!script.usesHostEnvironment()) {
                    env = ScriptSnapshot.create(torrent, peer, downloader, banDuration);
                } else {
                    env = script.newEnv();
                    env.put("torrent", torrent);
                    env.put("peer", peer);
                    env.put("downloader", downloader);
                    env.put("cacheable", new AtomicBoolean(false));
                    env.put("server", getServer());
                    env.put("moduleInstance", this);
                    env.put("banDuration", banDuration);
                    env.put("ramStorage", SharedObject.SCRIPT_THREAD_SAFE_MAP);
                }
                Object returns = script.execute(env);
                result = scriptEngineManager.handleResult(script, banDuration, returns);
            } catch (Exception ex) {
                log.error(tlUI(Lang.RULE_ENGINE_ERROR, script.name()), ex);
                return null;
            }
            if (result != null && result.action() != PeerAction.NO_ACTION) {
                return result;
            } else {
                return pass();
            }
        }, script.cacheable());
        return cached == null ? pass() : cached;
    }

    @Override
    public void onDisable() {
        Main.getReloadManager().unregister(this);
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        reloadConfig();
        return Reloadable.super.reloadModule();
    }

    private synchronized void reloadConfig() throws IOException {
        var lock = policyLock.writeLock();
        lock.lock();
        try {
            scripts = List.of();
            getCache().invalidateAll();
            sandbox = getConfig().getBoolean("sandbox", true);
            banDuration = getConfig().getLong("ban-duration", 0);
        } finally {
            lock.unlock();
        }
        loadScripts();
    }

    private void loadScripts() throws IOException {
        initScripts();

        backgroundTaskManager.addTaskAsync(new FunctionalBackgroundTask(
                new TranslationComponent(Lang.RULE_ENGINE_COMPILING),
                (task, callback) -> {
                    log.info(tlUI(Lang.RULE_ENGINE_COMPILING));
                    long start = System.currentTimeMillis();

                    var supportedScripts = scriptFiles();

                    task.setMax(supportedScripts.size());
                    task.setCurrent(0);
                    task.setBarType(BackgroundTaskProgressBarType.DETERMINATE);
                    callback.accept(task);

                    List<CompiledScript> loaded = new ArrayList<>();
                    int completed = 0;
                    for (File file : supportedScripts) {
                        try {
                            if (file.length() > SandboxProtocol.MAX_FRAME) throw new IOException("Script source too large");
                            var compiled = scriptEngineManager.compileLocal(file, Files.readString(file.toPath()), sandbox);
                            if (compiled != null) loaded.add(compiled);
                        } catch (Exception ex) {
                            log.warn("Unable to load script {}", file.getName(), ex);
                        } finally {
                            task.setCurrent(++completed);
                            callback.accept(task);
                        }
                    }
                    scripts = List.copyOf(loaded);
                    log.info(tlUI(Lang.RULE_ENGINE_COMPILED, scripts.size(), System.currentTimeMillis() - start));
                }
        )).join();
    }

    private List<File> scriptFiles() {
        var files = new File(Main.getDataDirectory(), "scripts").listFiles();
        if (files == null) return List.of();
        return Arrays.stream(files).filter(file -> file.isFile() && !file.isHidden())
                .filter(file -> getIfAllowedScriptId(file.getName()) != null).sorted().toList();
    }

    private void initScripts() throws IOException {
        File scriptDir = new File(Main.getDataDirectory(), "scripts");
        scriptDir.mkdirs();
        File versionFile = new File(scriptDir, "version");
        if (!versionFile.exists()) {
            versionFile.createNewFile();
        }
        var version = Files.readString(versionFile.toPath(), StandardCharsets.UTF_8);
        if (VERSION.equals(version)) {
            return;
        }
        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver(Main.class.getClassLoader());
        var res = resourcePatternResolver.getResources("classpath:scripts/**/*.*");
        for (Resource re : res) {
            String content = new String(re.getContentAsByteArray(), StandardCharsets.UTF_8);
            File file = new File(scriptDir, re.getFilename());
            if (file.exists()) continue;
            file.createNewFile();
            Files.writeString(file.toPath(), content);
        }
        Files.writeString(versionFile.toPath(), VERSION, StandardCharsets.UTF_8);
    }

    record ExpressionMetadataDto(String id, String name, String author, boolean cacheable, boolean threadSafe,
                                 String version, boolean loaded) {
    }
}
