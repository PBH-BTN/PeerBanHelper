package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.database.dao.impl.ScriptStorageDao;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.scriptengine.CompiledScript;
import com.ghostchu.peerbanhelper.scriptengine.ScriptEngine;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.MiscUtil;
import com.ghostchu.peerbanhelper.util.SharedObject;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.paging.Page;
import com.ghostchu.peerbanhelper.util.paging.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.googlecode.aviator.exception.ExpressionSyntaxErrorException;
import com.googlecode.aviator.exception.TimeoutException;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.SneakyThrows;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
@IgnoreScan
public class ExpressionRule extends AbstractRuleFeatureModule implements Reloadable {
    private final static String VERSION = "2";
    private final long maxScriptExecuteTime = 1500;
    private final JavalinWebContainer javalinWebContainer;
    private final ScriptEngine scriptEngine;
    private final List<CompiledScript> scripts = Collections.synchronizedList(new LinkedList<>());
    private final ScriptStorageDao scriptStorageDao;
    private long banDuration;

    public ExpressionRule(JavalinWebContainer javalinWebContainer, ScriptEngine scriptEngine, ScriptStorageDao scriptStorageDao) {
        super();
        this.scriptEngine = scriptEngine;
        this.javalinWebContainer = javalinWebContainer;
        this.scriptStorageDao = scriptStorageDao;
    }


    @Override
    public void onEnable() {
        // 默认启用脚本编译缓存
        try {
            reloadConfig();
        } catch (Exception e) {
            log.error("Failed to load scripts", e);
        }
        javalinWebContainer.javalin()
                .get("/api/" + getConfigName() + "/scripts", this::listScripts, Role.USER_READ)
                .get("/api/" + getConfigName() + "/{scriptId}", this::readScript, Role.USER_READ)
                .put("/api/" + getConfigName() + "/{scriptId}", this::writeScript, Role.USER_WRITE)
                .delete("/api/" + getConfigName() + "/{scriptId}", this::deleteScript, Role.USER_WRITE);
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
        Files.write(readFile.toPath(), context.bodyAsBytes(), StandardOpenOption.CREATE);
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
        if (!scriptId.endsWith(".av")) {
            return null;
        }
        File scriptDir = new File(Main.getDataDirectory(), "scripts");
        File readFile = new File(scriptDir, scriptId);
        if (insideDirectory(scriptDir, readFile)) {
            return readFile;
        }
        return null;
    }

    private boolean isSafeNetworkEnvironment(Context context){
        var value = System.getProperty("pbh.please-disable-safe-network-environment-check-i-know-this-is-very-dangerous-and-i-may-lose-my-data-and-hacker-may-attack-me-via-this-endpoint-and-steal-my-data-or-destroy-my-computer-i-am-fully-responsible-for-this-action-and-i-will-not-blame-the-developer-for-any-loss");
        if(value != null && value.equals("true")){
            return true;
        }
        var ip = IPAddressUtil.getIPAddress(context.ip());
        if(ip == null){
            throw new IllegalArgumentException("Safe check for IPAddress failed, the IP cannot be null");
        }
        return (ip.isLocal() || ip.isLoopback()) && !MiscUtil.isUsingReserveProxy(context);
    }

    private boolean insideDirectory(File allowRange, File targetFile) {
        Path path = allowRange.toPath().normalize().toAbsolutePath();
        Path target = targetFile.toPath().normalize().toAbsolutePath();
        return target.startsWith(path);
    }

    private void listScripts(Context context) {
        Pageable pageable = new Pageable(context);
        List<ExpressionMetadataDto> list = new ArrayList<>();
        for (var script : scripts) {
            list.add(new ExpressionMetadataDto(
                    script.file().getName(),
                    script.name(),
                    script.author(),
                    script.cacheable(),
                    script.threadSafe(),
                    script.version()
            ));
        }
        var r = list.stream().skip(pageable.getZeroBasedPage() * pageable.getSize()).limit(pageable.getSize()).toList();
        context.json(new StdResp(true, null, new Page<>(pageable, list.size(), r)));
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

    @SneakyThrows
    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        AtomicReference<CheckResult> checkResult = new AtomicReference<>(pass());
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var compiledScript : scripts) {
                exec.submit(() -> {
                    CheckResult expressionRun = runExpression(compiledScript, torrent, peer, downloader, ruleExecuteExecutor);
                    if (expressionRun.action() == PeerAction.SKIP) {
                        checkResult.set(expressionRun); // 提前退出
                        return;
                    }
                    if (expressionRun.action() == PeerAction.BAN) {
                        if (checkResult.get().action() != PeerAction.SKIP) {
                            checkResult.set(expressionRun);
                        }
                    }
                });
            }
        }
        return checkResult.get();
    }

    public CheckResult runExpression(CompiledScript script, @NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        return getCache().readCacheButWritePassOnly(this, script.expression().hashCode() + peer.getCacheKey(), () -> {
            CheckResult result;
            try {
                Map<String, Object> env = script.expression().newEnv();
                env.put("torrent", torrent);
                env.put("peer", peer);
                env.put("downloader", downloader);
                env.put("cacheable", new AtomicBoolean(false));
                env.put("server", getServer());
                env.put("moduleInstance", this);
                env.put("banDuration", banDuration);
                env.put("persistStorage", scriptStorageDao);
                env.put("ramStorage", SharedObject.SCRIPT_THREAD_SAFE_MAP);
                Object returns;
                if (script.threadSafe()) {
                    returns = script.expression().execute(env);
                } else {
                    synchronized (script.expression()) {
                        returns = script.expression().execute(env);
                    }
                }
                result = scriptEngine.handleResult(script,banDuration, returns);
            } catch (TimeoutException timeoutException) {
                return pass();
            } catch (Exception ex) {
                log.error(tlUI(Lang.RULE_ENGINE_ERROR, script.name()), ex);
                return pass();
            }
            if (result != null && result.action() != PeerAction.NO_ACTION) {
                return result;
            } else {
                return pass();
            }
        }, script.cacheable());
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

    private void reloadConfig() throws IOException {
        scripts.clear();
        this.banDuration = getConfig().getLong("ban-duration", 0);
        initScripts();
        log.info(tlUI(Lang.RULE_ENGINE_COMPILING));
        long start = System.currentTimeMillis();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            File scriptDir = new File(Main.getDataDirectory(), "scripts");
            File[] scripts = scriptDir.listFiles();
            if (scripts != null) {
                for (File script : scripts) {
                    executor.submit(() -> {
                        try {
                            if (!script.getName().endsWith(".av") || script.isHidden()) {
                                return;
                            }
                            try {
                                String scriptContent = java.nio.file.Files.readString(script.toPath(), StandardCharsets.UTF_8);
                                var compiledScript = scriptEngine.compileScript(script,script.getName(),scriptContent);
                                if(compiledScript == null) return;
                                this.scripts.add(compiledScript);
                            } catch (IOException e) {
                                log.error("Unable to load script file", e);
                            }
                        } catch (ExpressionSyntaxErrorException err) {
                            log.error(tlUI(Lang.RULE_ENGINE_BAD_EXPRESSION), err);
                        }
                    });
                }
            }
        }
        getCache().invalidateAll();
        log.info(tlUI(Lang.RULE_ENGINE_COMPILED, scripts.size(), System.currentTimeMillis() - start));
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
                                 String version) {
    }
}
