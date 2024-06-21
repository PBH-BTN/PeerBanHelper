package com.ghostchu.peerbanhelper.module.impl.rule;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.module.AbstractRuleFeatureModule;
import com.ghostchu.peerbanhelper.module.BanResult;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.IPAddressUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.util.StrUtil;
import com.ghostchu.peerbanhelper.util.time.InfoHashUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.EvalMode;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import com.googlecode.aviator.exception.ExpressionSyntaxErrorException;
import com.googlecode.aviator.exception.TimeoutException;
import com.googlecode.aviator.runtime.JavaMethodReflectionFunctionMissing;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ExpressionRule extends AbstractRuleFeatureModule {
    private final long maxScriptExecuteTime = 1500;
    private Map<Expression, ExpressionMetadata> expressions = new HashMap<>();
    private Cache<String, Map<String, Optional<BanResult>>> cacheMap = CacheBuilder.newBuilder()
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .maximumSize(2000)
            .softValues()
            .build();

    public ExpressionRule(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isCheckCacheable() {
        return false;
    }

    @Override
    public boolean needCheckHandshake() {
        return true;
    }

    @SneakyThrows
    @Override
    public @NotNull BanResult shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull ExecutorService ruleExecuteExecutor) {
        String cacheKey = peer.getCacheKey();
        Map<String, Object> env = new HashMap<>();
        env.put("torrent", torrent);
        env.put("peer", peer);
        for (Expression expression : expressions.keySet()) {
            ExpressionMetadata expressionMetadata = expressions.get(expression);
            Map<String, Optional<BanResult>> cached = cacheMap.get(cacheKey, HashMap::new);
            BanResult result;
            if (cached.containsKey(expressionMetadata.name())) {
                result = cached.get(expressionMetadata.name()).orElse(null);
            } else {
                try {
                    Object returns = expression.execute(env);
                    result = handleResult(expression, returns);
                    if (expressionMetadata.cacheable()) {
                        cached.put(expressionMetadata.name(), Optional.ofNullable(result));
                    }
                } catch (TimeoutException timeoutException) {
                    log.warn(Lang.MODULE_EXPRESSION_RULE_TIMEOUT, maxScriptExecuteTime, timeoutException);
                    continue;
                } catch (Exception ex) {
                    log.warn(Lang.MODULE_EXPRESSION_RULE_ERROR, expressionMetadata.name(), ex);
                    continue;
                }
            }
            if (cached.isEmpty()) {
                cacheMap.invalidate(cacheKey);
            }
            if (result != null && result.action() != PeerAction.NO_ACTION) {
                return result;
            }
        }
        return new BanResult(this, PeerAction.NO_ACTION, "N/A", "All ok!");
    }

    @Nullable
    public BanResult handleResult(Expression expression, Object returns) {
        if (returns instanceof Boolean status) {
            if (status) {
                return new BanResult(this, PeerAction.BAN, "User Rule", expressions.get(expression).name());
            }
            return null;
        }
        if (returns instanceof Number number) {
            int i = number.intValue();
            if (i == 0) {
                return null;
            } else if (i == 1) {
                return new BanResult(this, PeerAction.BAN, "User Rule", expressions.get(expression).name());
            } else if (i == 2) {
                return new BanResult(this, PeerAction.SKIP, "User Rule", expressions.get(expression).name());
            } else {
                log.warn(Lang.MODULE_EXPRESSION_RULE_INVALID_RETURNS, expressions.get(expression));
                return null;
            }
        }
        if (returns instanceof PeerAction action) {
            return new BanResult(this, action, "User Rule", expressions.get(expression).name());
        }
        if (returns instanceof BanResult banResult) {
            return banResult;
        }
        log.warn(Lang.MODULE_EXPRESSION_RULE_INVALID_RETURNS, expressions.get(expression));
        return null;
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
    public void onEnable() {
        // ASM 性能优先
        AviatorEvaluator.getInstance().setOption(Options.EVAL_MODE, EvalMode.ASM);
        // EVAL 性能优先
        AviatorEvaluator.getInstance().setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL);
        // 降低浮点计算精度
        AviatorEvaluator.getInstance().setOption(Options.MATH_CONTEXT, MathContext.DECIMAL128);
        // 启用变量语法糖
        AviatorEvaluator.getInstance().setOption(Options.ENABLE_PROPERTY_SYNTAX_SUGAR, true);
        // 表达式允许序列化和反序列化
        AviatorEvaluator.getInstance().setOption(Options.SERIALIZABLE, true);
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
        reloadConfig();
    }

    private void registerFunctions(Class<?> clazz) {
        try {
            AviatorEvaluator.addInstanceFunctions(clazz.getSimpleName(), clazz);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            log.error("Internal error: failed on register static functions: {}", clazz.getName(), e);
        }
    }

    private void reloadConfig() {
        expressions.clear();
        log.info(Lang.MODULE_EXPRESSION_RULE_COMPILING);
        ConfigurationSection section = getConfig().getConfigurationSection("rules");
        long start = System.currentTimeMillis();
        Map<Expression, ExpressionMetadata> userRules = new ConcurrentHashMap<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String ruleName : section.getKeys(false)) {
                ConfigurationSection ruleSection = section.getConfigurationSection(ruleName);
                if (ruleSection == null) {
                    continue;
                }
                boolean cacheable = ruleSection.getBoolean("cacheable", true);
                String script = ruleSection.getString("script", "");
                executor.submit(() -> {
                    try {
                        AviatorEvaluator.getInstance().validate(script);
                        Expression expression = AviatorEvaluator.getInstance().compile(script);
                        expression.newEnv("peerbanhelper", getServer(), "moduleConfig", getConfig(), "ipdb", getServer().getIpdb());
                        userRules.put(expression, new ExpressionMetadata(ruleName, cacheable, script));
                    } catch (ExpressionSyntaxErrorException err) {
                        log.warn(Lang.MODULE_EXPRESSION_RULE_BAD_EXPRESSION, err);
                    }
                });
            }
        }
        expressions = ImmutableMap.copyOf(userRules);
        log.info(Lang.MODULE_EXPRESSION_RULE_COMPILED, expressions.size(), System.currentTimeMillis() - start);
    }

    @Override
    public void onDisable() {

    }

    record ExpressionMetadata(String name, boolean cacheable, String script) {
    }
}
