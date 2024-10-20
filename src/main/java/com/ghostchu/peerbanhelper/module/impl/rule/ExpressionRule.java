package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.CheckResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.StrUtil;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.time.InfoHashUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.EvalMode;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import com.googlecode.aviator.exception.ExpressionSyntaxErrorException;
import com.googlecode.aviator.exception.TimeoutException;
import com.googlecode.aviator.runtime.JavaMethodReflectionFunctionMissing;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
@IgnoreScan
public class ExpressionRule extends AbstractRuleFeatureModule implements Reloadable {
    private final static String VERSION = "2";
    private final long maxScriptExecuteTime = 1500;
    private final JavalinWebContainer javalinWebContainer;
    private Map<Expression, ExpressionMetadata> expressions = new HashMap<>();
    private long banDuration;

    public ExpressionRule(JavalinWebContainer javalinWebContainer) {
        super();
        this.javalinWebContainer = javalinWebContainer;
    }


    @Override
    public void onEnable() {
        // 默认启用脚本编译缓存
        AviatorEvaluator.getInstance().setCachedExpressionByDefault(true);
        // ASM 性能优先
        AviatorEvaluator.getInstance().setOption(Options.EVAL_MODE, EvalMode.ASM);
        // EVAL 性能优先
        AviatorEvaluator.getInstance().setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL);
        // 降低浮点计算精度
        AviatorEvaluator.getInstance().setOption(Options.MATH_CONTEXT, MathContext.DECIMAL32);
        // 启用变量语法糖
        AviatorEvaluator.getInstance().setOption(Options.ENABLE_PROPERTY_SYNTAX_SUGAR, true);
//        // 表达式允许序列化和反序列化
//        AviatorEvaluator.getInstance().setOption(Options.SERIALIZABLE, true);
        // 用户规则写糊保护
        AviatorEvaluator.getInstance().setOption(Options.MAX_LOOP_COUNT, 5000);
        AviatorEvaluator.getInstance().setOption(Options.EVAL_TIMEOUT_MS, maxScriptExecuteTime);
        // 启用反射方法查找
        AviatorEvaluator.getInstance().setFunctionMissing(JavaMethodReflectionFunctionMissing.getInstance());
        // 注册反射调用
        registerFunctions(IPAddressUtil.class);
        registerFunctions(HTTPUtil.class);
        registerFunctions(JsonUtil.class);
        registerFunctions(Lang.class);
        registerFunctions(StrUtil.class);
        registerFunctions(PeerBanHelperServer.class);
        registerFunctions(InfoHashUtil.class);
        registerFunctions(Main.class);
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
//        test code
//        Torrent torrent = new TorrentImpl("1", "","",1,1.00d, 1,1);
//        Peer peer = new PeerImpl(new PeerAddress("2408:8214:1551:bf20::1", 51413),
//                "2408:8214:1551:bf20::1","-TR2940-", "Transmission 2.94",
//                1,1,1,1,0.0d,null);
//        System.out.println(shouldBanPeer(torrent,peer, Executors.newVirtualThreadPerTaskExecutor()));
    }

    private void deleteScript(Context context) throws IOException {
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
        var scriptId = context.pathParam("scriptId");
        File readFile = getIfAllowedScriptId(scriptId);
        if (readFile == null) {
            context.status(HttpStatus.BAD_REQUEST);
            context.json(new StdResp(false, "Access to this resource is disallowed", null));
            return;
        }
        Files.write(readFile.toPath(), context.bodyAsBytes(), StandardOpenOption.CREATE);
        context.json(new StdResp(true, "OK!", null));
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

    private boolean insideDirectory(File allowRange, File targetFile) {
        Path path = allowRange.toPath().toAbsolutePath();
        Path target = targetFile.toPath().toAbsolutePath();
        return target.startsWith(path);
    }

    private void listScripts(Context context) {
        List<ExpressionMetadataDto> list = new ArrayList<>();
        for (Map.Entry<Expression, ExpressionMetadata> entry : expressions.entrySet()) {
            var metadata = entry.getValue();
            list.add(new ExpressionMetadataDto(
                    metadata.file().getName(),
                    metadata.name(),
                    metadata.author(),
                    metadata.cacheable(),
                    metadata.threadSafe(),
                    metadata.version()
            ));
        }
        context.json(new StdResp(true, null, list));
    }


    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Nullable
    public CheckResult handleResult(Expression expression, Object returns) {
        ExpressionMetadata meta = expressions.get(expression);
        if (returns instanceof Boolean status) {
            if (status) {

                return new CheckResult(getClass(), PeerAction.BAN, banDuration,
                        new TranslationComponent(Lang.USER_SCRIPT_RULE),
                        new TranslationComponent(Lang.USER_SCRIPT_RUN_RESULT, meta.name(), "true"));
            }
            return null;
        }
        if (returns instanceof Number number) {
            int i = number.intValue();
            if (i == 0) {
                return null;
            } else if (i == 1) {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration,
                        new TranslationComponent(Lang.USER_SCRIPT_RULE),
                        new TranslationComponent(Lang.USER_SCRIPT_RUN_RESULT, meta.name(), String.valueOf(number)));
            } else if (i == 2) {
                return new CheckResult(getClass(), PeerAction.SKIP, banDuration,
                        new TranslationComponent(Lang.USER_SCRIPT_RULE),
                        new TranslationComponent(Lang.USER_SCRIPT_RUN_RESULT, meta.name(), String.valueOf(number)));
            } else {
                log.error(tlUI(Lang.MODULE_EXPRESSION_RULE_INVALID_RETURNS, meta));
                return null;
            }
        }
        if (returns instanceof PeerAction action) {
            return new CheckResult(getClass(), action, banDuration,
                    new TranslationComponent(Lang.USER_SCRIPT_RULE),
                    new TranslationComponent(Lang.USER_SCRIPT_RUN_RESULT, meta.name(), action.name()));
        }
        if (returns instanceof String string) {
            if (string.isBlank()) {
                return pass();
            } else if (string.startsWith("@")) {
                return new CheckResult(getClass(), PeerAction.SKIP, banDuration,
                        new TranslationComponent(Lang.USER_SCRIPT_RULE),
                        new TranslationComponent(string.substring(1)));
            } else {
                return new CheckResult(getClass(), PeerAction.BAN, banDuration,
                        new TranslationComponent(Lang.USER_SCRIPT_RULE),
                        new TranslationComponent(Lang.USER_SCRIPT_RUN_RESULT, meta.name(), string));
            }
        }
        if (returns instanceof CheckResult checkResult) {
            return checkResult;
        }
        log.error(tlUI(Lang.MODULE_EXPRESSION_RULE_INVALID_RETURNS, meta));
        return null;
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

    private void registerFunctions(Class<?> clazz) {
        try {
            AviatorEvaluator.addInstanceFunctions(clazz.getSimpleName(), clazz);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            log.error("Internal error: failed on register static functions: {}", clazz.getName(), e);
        }
    }

    @SneakyThrows
    @Override
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        AtomicReference<CheckResult> checkResult = new AtomicReference<>(pass());
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Expression expression : expressions.keySet()) {
                exec.submit(() -> {
                    CheckResult expressionRun = runExpression(expression, torrent, peer, downloader, ruleExecuteExecutor);
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

    public CheckResult runExpression(Expression expression, @NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader, @NotNull ExecutorService ruleExecuteExecutor) {
        ExpressionMetadata expressionMetadata = expressions.get(expression);
        return getCache().readCacheButWritePassOnly(this, expression.hashCode() + peer.getCacheKey(), () -> {
            CheckResult result;
            try {
                Map<String, Object> env = expression.newEnv();
                env.put("torrent", torrent);
                env.put("peer", peer);
                env.put("downloader", downloader);
                env.put("cacheable", new AtomicBoolean(false));
                Object returns;
                if (expressionMetadata.threadSafe()) {
                    returns = expression.execute(env);
                } else {
                    synchronized (expressionMetadata) {
                        returns = expression.execute(env);
                    }
                }
                result = handleResult(expression, returns);
            } catch (TimeoutException timeoutException) {
                log.error(tlUI(Lang.MODULE_EXPRESSION_RULE_TIMEOUT, maxScriptExecuteTime), timeoutException);
                return pass();
            } catch (Exception ex) {
                log.error(tlUI(Lang.MODULE_EXPRESSION_RULE_ERROR, expressionMetadata.name()), ex);
                return pass();
            }
            if (result != null && result.action() != PeerAction.NO_ACTION) {
                return result;
            } else {
                return pass();
            }
        }, expressionMetadata.cacheable());
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
        expressions.clear();
        this.banDuration = getConfig().getLong("ban-duration", 0);
        initScripts();
        log.info(tlUI(Lang.MODULE_EXPRESSION_RULE_COMPILING));
        long start = System.currentTimeMillis();
        Map<Expression, ExpressionMetadata> userRules = new ConcurrentHashMap<>();
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
                                ExpressionMetadata expressionMetadata = parseScriptMetadata(script, script.getName(), scriptContent);
                                AviatorEvaluator.getInstance().validate(expressionMetadata.script());
                                Expression expression = AviatorEvaluator.getInstance().compile(expressionMetadata.script(), false);
                                expression.newEnv("peerbanhelper", getServer(), "moduleConfig", getConfig(), "ipdb", getServer().getIpdb());
                                userRules.put(expression, expressionMetadata);
                            } catch (IOException e) {
                                log.error("Unable to load script file", e);
                            }
                        } catch (ExpressionSyntaxErrorException err) {
                            log.error(tlUI(Lang.MODULE_EXPRESSION_RULE_BAD_EXPRESSION), err);
                        }
                    });
                }
            }
        }
        expressions = new HashMap<>(userRules);
        getCache().invalidateAll();
        log.info(tlUI(Lang.MODULE_EXPRESSION_RULE_COMPILED, expressions.size(), System.currentTimeMillis() - start));
    }

    private ExpressionMetadata parseScriptMetadata(File file, String fallbackName, String scriptContent) {
        try (BufferedReader reader = new BufferedReader(new StringReader(scriptContent))) {
            String name = fallbackName;
            String author = "Unknown";
            String version = "null";
            boolean cacheable = true;
            boolean threadSafe = true;
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.startsWith("#")) {
                    line = line.substring(2).trim();
                    if (line.startsWith("@NAME")) {
                        name = line.substring(5).trim();
                    } else if (line.startsWith("@AUTHOR")) {
                        author = line.substring(7).trim();
                    } else if (line.startsWith("@CACHEABLE")) {
                        cacheable = Boolean.parseBoolean(line.substring(10).trim());
                    } else if (line.startsWith("@VERSION")) {
                        version = line.substring(8).trim();
                    } else if (line.startsWith("@THREADSAFE")) {
                        threadSafe = Boolean.parseBoolean(line.substring(11).trim());
                    }
                }
            }
            return new ExpressionMetadata(file, name, author, cacheable, threadSafe, version, scriptContent);
        } catch (IOException e) {
            return new ExpressionMetadata(file, "Failed to parse name", "Unknown", true, true, "null", scriptContent);
        }
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

    record ExpressionMetadata(File file, String name, String author, boolean cacheable, boolean threadSafe,
                              String version,
                              String script) {
    }

    record ExpressionMetadataDto(String id, String name, String author, boolean cacheable, boolean threadSafe,
                                 String version) {
    }
}
