package com.ghostchu.peerbanhelper.module.impl.webapi;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.database.RuleSubInfo;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.impl.rule.IPBlackRuleList;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.HTTPUtil;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.PBHAPI;
import fi.iki.elonen.NanoHTTPD;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class RuleSubApi extends AbstractFeatureModule implements PBHAPI {

    IPBlackRuleList ipBlackRuleList;

    public RuleSubApi(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Rule Subscription";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-rule-subscription";
    }

    @Override
    public void onEnable() {
        Optional<FeatureModule> first = getServer().getModuleManager().getModules().stream().filter(ele -> ele.getConfigName().equals("ip-address-blocker-rules")).findFirst();
        first.ifPresentOrElse(ele -> {
            ipBlackRuleList = (IPBlackRuleList) ele;
            getServer().getWebManagerServer().register(this);
        }, () -> log.error(Lang.RULE_SUB_API_NO_DEPENDENCY));
    }

    @Override
    public void onDisable() {
        getServer().getWebManagerServer().unregister(this);
    }

    @Override
    public boolean shouldHandle(String uri) {
        return uri.startsWith("/api/rulesub");
    }

    @Override
    public List<NanoHTTPD.Method> shouldHandleMethods() {
        return List.of(NanoHTTPD.Method.GET, NanoHTTPD.Method.POST, NanoHTTPD.Method.DELETE, NanoHTTPD.Method.PUT, NanoHTTPD.Method.PATCH);
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        String respStr = JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.RULE_SUB_API_INTERNAL_ERROR));
        AtomicReference<NanoHTTPD.Response> resp = new AtomicReference<>();
        resp.set(HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.RULE_SUB_API_INTERNAL_ERROR)))));
        String subUri = session.getUri().substring(12);
        if (StrUtil.isEmpty(subUri) && session.getMethod() == NanoHTTPD.Method.PATCH) {
            // 更新检查间隔
            try {
                respStr = changeCheckInterval(session);
                return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", respStr));
            } catch (IOException | NanoHTTPD.ResponseException e) {
                throw new RuntimeException(e);
            }
        }
        String[] split = subUri.split("/");
        String func = split[1];
        try {
            switch (func) {
                case "rule" -> {
                    switch (session.getMethod()) {
                        // 新增订阅规则
                        case PUT -> respStr = add(session);
                        // 查询订阅规则
                        case GET -> {
                            if (split.length > 3 && "update".equals(split[3])) {
                                respStr = update(split[2]);
                            } else {
                                respStr = getRule(split[2]);
                            }
                        }
                        // 保存订阅规则
                        case POST -> respStr = save(session, split[2]);
                        // 删除订阅规则
                        case DELETE -> respStr = delete(split[2]);
                        // 启用/禁用订阅规则
                        case PATCH -> respStr = switcher(session, split[2]);
                        default -> log.error(Lang.RULE_SUB_API_INTERNAL_ERROR);
                    }
                }
                case "rules" -> {
                    // 查询订阅规则列表
                    if (Objects.requireNonNull(session.getMethod()) == NanoHTTPD.Method.GET) {
                        if (split.length > 2 && "update".equals(split[2])) {
                            respStr = updateAll();
                        } else {
                            respStr = list(session);
                        }
                    } else {
                        log.error(Lang.RULE_SUB_API_INTERNAL_ERROR);
                    }
                }
                // 查询订阅规则日志
                case "logs" -> {
                    if (Objects.requireNonNull(session.getMethod()) == NanoHTTPD.Method.GET) {
                        respStr = logs(session);
                    } else {
                        log.error(Lang.RULE_SUB_API_INTERNAL_ERROR);
                    }
                }
                default -> log.error(Lang.RULE_SUB_API_INTERNAL_ERROR);
            }
        } catch (Exception e) {
            log.error(Lang.RULE_SUB_API_INTERNAL_ERROR);
        }
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", respStr));
    }

    /**
     * 修改检查间隔
     *
     * @param session 请求
     * @return 响应
     */
    private String changeCheckInterval(NanoHTTPD.IHTTPSession session) throws IOException, NanoHTTPD.ResponseException {
        session.parseBody(new HashMap<String, String>(1)); // damn you, NanoHTTPD
        String checkInterval = session.getParameters().getOrDefault("checkInterval", List.of("")).get(0);
        if (StrUtil.isEmpty(checkInterval)) {
            return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_NO_CHECK_INTERVAL));
        } else {
            ipBlackRuleList.changeCheckInterval(Long.parseLong(checkInterval));
        }
        return JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_CHECK_INTERVAL_UPDATED));
    }

    private String logs(NanoHTTPD.IHTTPSession session) throws SQLException {
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        int pageIndex = Integer.parseInt(session.getParameters().getOrDefault("pageIndex", List.of("0")).get(0));
        int pageSize = Integer.parseInt(session.getParameters().getOrDefault("pageSize", List.of("100")).get(0));
        Map<String, Object> map = new HashMap<>();
        map.put("pageIndex", pageIndex);
        map.put("pageSize", pageSize);
        map.put("results", ipBlackRuleList.queryRuleSubLogs(ruleId, pageIndex, pageSize));
        map.put("total", ipBlackRuleList.countRuleSubLogs(ruleId));
        return JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_LOG_QUERY_SUCCESS, "data", map));
    }

    /**
     * 手动更新全部订阅规则
     *
     * @return 响应
     */
    private String updateAll() {
        ipBlackRuleList.getIpBanMatchers().forEach(ele -> ipBlackRuleList.updateRule(Objects.requireNonNull(ipBlackRuleList.getRuleSubsConfig().getConfigurationSection(ele.getRuleId())), Lang.IP_BAN_RULE_UPDATE_TYPE_MANUAL));
        return JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_UPDATED));
    }

    /**
     * 手动更新订阅规则
     *
     * @param ruleId 规则ID
     * @return 响应
     */
    private String update(String ruleId) {
        ipBlackRuleList.getIpBanMatchers().stream().filter(ele -> StrUtil.isEmpty(ruleId) || ele.getRuleId().equals(ruleId))
                .forEach(ele -> ipBlackRuleList.updateRule(Objects.requireNonNull(ipBlackRuleList.getRuleSubsConfig().getConfigurationSection(ele.getRuleId())), Lang.IP_BAN_RULE_UPDATE_TYPE_MANUAL));
        return JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_UPDATED));
    }

    /**
     * 启用/禁用订阅规则
     *
     * @param session 请求
     * @param ruleId  规则ID
     * @return 响应
     */
    private String switcher(NanoHTTPD.IHTTPSession session, String ruleId) throws SQLException, IOException, NanoHTTPD.ResponseException {
        session.parseBody(new HashMap<String, String>(1)); // damn you, NanoHTTPD
        boolean enabled = Boolean.parseBoolean(session.getParameters().getOrDefault("enabled", List.of("")).get(0));
        if (StrUtil.isEmpty(ruleId)) {
            return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_CANT_FIND));
        }
        RuleSubInfo ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
        if (null == ruleSubInfo) {
            return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_CANT_FIND));
        }
        if (enabled != ruleSubInfo.enabled()) {
            ConfigurationSection configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfo(ruleId, !ruleSubInfo.enabled(), ruleSubInfo.ruleName(), ruleSubInfo.subUrl(), 0, 0));
            ipBlackRuleList.updateRule(configurationSection, Lang.IP_BAN_RULE_UPDATE_TYPE_MANUAL);
        }
        return JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", enabled ? Lang.IP_BAN_RULE_ENABLED : Lang.IP_BAN_RULE_DISABLED));
    }

    /**
     * 删除订阅规则
     *
     * @param ruleId 规则ID
     * @return 响应
     */
    private String delete(String ruleId) throws IOException {
        if (StrUtil.isEmpty(ruleId)) {
            return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_CANT_FIND));
        }
        ipBlackRuleList.deleteRuleSubInfo(ruleId);
        ipBlackRuleList.getIpBanMatchers().removeIf(ele -> ele.getRuleId().equals(ruleId));
        return JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_DELETED));
    }

    /**
     * 新增订阅规则
     *
     * @param session 请求
     * @return 响应
     */
    private String add(NanoHTTPD.IHTTPSession session) throws IOException, SQLException, NanoHTTPD.ResponseException {
        session.parseBody(new HashMap<String, String>(1)); // damn you, NanoHTTPD
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        if (StrUtil.isEmpty(ruleId)) {
            return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_NO_ID));
        }
        String enableStr = session.getParameters().getOrDefault("enabled", List.of("false")).get(0);
        String ruleName = session.getParameters().getOrDefault("ruleName", List.of("")).get(0);
        String subUrl = session.getParameters().getOrDefault("subUrl", List.of("")).get(0);
        if (!List.of("TRUE", "FALSE").contains(enableStr.toUpperCase()) || StrUtil.isEmpty(ruleName) || StrUtil.isEmpty(subUrl)) {
            return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_PARAM_WRONG));
        }
        RuleSubInfo ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
        if (ruleSubInfo != null) {
            return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_ID_CONFLICT));
        }
        boolean enabled = BooleanUtil.toBoolean(enableStr);
        ConfigurationSection configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfo(ruleId, enabled, ruleName, subUrl, 0, 0));
        ipBlackRuleList.updateRule(configurationSection, Lang.IP_BAN_RULE_UPDATE_TYPE_MANUAL);
        return JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_SAVED));
    }

    /**
     * 保存订阅规则
     *
     * @param session 请求
     * @param ruleId  规则ID
     * @return 响应
     */
    private String save(NanoHTTPD.IHTTPSession session, String ruleId) throws IOException, SQLException, NanoHTTPD.ResponseException {
        session.parseBody(new HashMap<String, String>(1)); // damn you, NanoHTTPD
        if (StrUtil.isEmpty(ruleId)) {
            return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_NO_ID));
        }
        String enableStr = session.getParameters().getOrDefault("enabled", List.of("false")).get(0);
        String ruleName = session.getParameters().getOrDefault("ruleName", List.of("")).get(0);
        String subUrl = session.getParameters().getOrDefault("subUrl", List.of("")).get(0);
        if (!List.of("TRUE", "FALSE").contains(enableStr.toUpperCase()) || StrUtil.isEmpty(ruleName) || StrUtil.isEmpty(subUrl)) {
            return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_PARAM_WRONG));
        }
        boolean enabled = BooleanUtil.toBoolean(enableStr);
        ConfigurationSection configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfo(ruleId, enabled, ruleName, subUrl, 0, 0));
        ipBlackRuleList.updateRule(configurationSection, Lang.IP_BAN_RULE_UPDATE_TYPE_MANUAL);
        return JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_SAVED));
    }

    /**
     * 查询订阅规则
     *
     * @param ruleId 规则ID
     * @return 响应
     * @throws SQLException SQL 异常
     */
    private String getRule(String ruleId) throws SQLException {
        return JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS, "data", ipBlackRuleList.getRuleSubInfo(ruleId)));
    }

    /**
     * 查询订阅规则列表
     *
     * @param session 请求
     * @return 响应
     */
    private String list(NanoHTTPD.IHTTPSession session) {
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        List<RuleSubInfo> collect = ipBlackRuleList.getRuleSubsConfig().getKeys(false).stream().filter(ele -> StrUtil.isEmpty(ruleId) || ele.equals(ruleId))
                .map(ele -> {
                    try {
                        return ipBlackRuleList.getRuleSubInfo(ele);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();
        return JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS, "data", collect));
    }

    /**
     * 限制请求方法
     *
     * @param session 请求
     * @param method  方法
     * @return 响应
     */
    private Optional<NanoHTTPD.Response> limitRequestMethod(NanoHTTPD.IHTTPSession session, NanoHTTPD.Method method) {
        if (session.getMethod() != method) {
            NanoHTTPD.Response.Status methodNotAllowed = NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED;
            return Optional.of(HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(methodNotAllowed, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", methodNotAllowed.getDescription())))));
        }
        return Optional.empty();
    }

}
