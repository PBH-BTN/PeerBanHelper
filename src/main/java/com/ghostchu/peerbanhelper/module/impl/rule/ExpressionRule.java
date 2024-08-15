package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
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
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.time.InfoHashUtil;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.EvalMode;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import com.googlecode.aviator.exception.ExpressionSyntaxErrorException;
import com.googlecode.aviator.exception.TimeoutException;
import com.googlecode.aviator.runtime.JavaMethodReflectionFunctionMissing;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class ExpressionRule extends AbstractRuleFeatureModule implements Reloadable {
    private final long maxScriptExecuteTime = 1500;
    private final Map<ExpressionMetadata, ReentrantLock> threadLocks = new HashMap<>();
    private Map<Expression, ExpressionMetadata> expressions = new HashMap<>();
    private long banDuration;

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
    public @NotNull CheckResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        AtomicReference<CheckResult> checkResult = new AtomicReference<>(pass());
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Expression expression : expressions.keySet()) {
                exec.submit(() -> {
                    CheckResult expressionRun = runExpression(expression, torrent, peer, ruleExecuteExecutor);
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

    public CheckResult runExpression(Expression expression, @NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        ExpressionMetadata expressionMetadata = expressions.get(expression);
        return getCache().readCacheButWritePassOnly(this, expression.hashCode() + peer.getCacheKey(), () -> {
            CheckResult result;
            try {
                Map<String, Object> env = expression.newEnv();
                env.put("torrent", torrent);
                env.put("peer", peer);
                env.put("cacheable", new AtomicBoolean(false));
                Object returns;
                if (expressionMetadata.threadSafe()) {
                    returns = expression.execute(env);
                } else {
                    ReentrantLock lock = threadLocks.get(expressionMetadata);
                    lock.lock();
                    try {
                        returns = expression.execute(env);
                    } finally {
                        lock.unlock();
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
            System.exit(1);
        }
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
        threadLocks.clear();
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
                    if (!script.getName().endsWith(".av") || script.isHidden()) {
                        continue;
                    }
                    String scriptContent = java.nio.file.Files.readString(script.toPath(), StandardCharsets.UTF_8);
                    ExpressionMetadata expressionMetadata = parseScriptMetadata(script.getName(), scriptContent);
                    executor.submit(() -> {
                        try {
                            AviatorEvaluator.getInstance().validate(expressionMetadata.script());
                            Expression expression = AviatorEvaluator.getInstance().compile(expressionMetadata.script(), false);
                            expression.newEnv("peerbanhelper", getServer(), "moduleConfig", getConfig(), "ipdb", getServer().getIpdb());
                            userRules.put(expression, expressionMetadata);
                            if (!expressionMetadata.threadSafe()) {
                                threadLocks.put(expressionMetadata, new ReentrantLock());
                            }
                        } catch (ExpressionSyntaxErrorException err) {
                            log.error(tlUI(Lang.MODULE_EXPRESSION_RULE_BAD_EXPRESSION), err);
                        }
                    });
                }
            }
        }
        expressions = Map.copyOf(userRules);
        log.info(tlUI(Lang.MODULE_EXPRESSION_RULE_COMPILED, expressions.size(), System.currentTimeMillis() - start));
    }

    private ExpressionMetadata parseScriptMetadata(String fallbackName, String scriptContent) {
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
            return new ExpressionMetadata(name, author, cacheable, threadSafe, version, scriptContent);
        } catch (IOException e) {
            return new ExpressionMetadata("Failed to parse name", "Unknown", true, true, "null", scriptContent);
        }
    }

    private void initScripts() throws IOException {
        File scriptDir = new File(Main.getDataDirectory(), "scripts");
        if (scriptDir.exists()) {
            return;
        }
        scriptDir.mkdirs();
        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver(Main.class.getClassLoader());
        var res = resourcePatternResolver.getResources("classpath:scripts/**/*.*");
        for (Resource re : res) {
            String content = new String(re.getContentAsByteArray(), StandardCharsets.UTF_8);
            File file = new File(scriptDir, re.getFilename());
            file.createNewFile();
            Files.writeString(file.toPath(), content);
        }
    }

    record ExpressionMetadata(String name, String author, boolean cacheable, boolean threadSafe, String version,
                              String script) {
    }
}
