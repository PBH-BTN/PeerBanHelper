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
        return List.of(NanoHTTPD.Method.GET, NanoHTTPD.Method.POST, NanoHTTPD.Method.DELETE);
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        String uri = session.getUri();
        String subUri = uri.substring(uri.lastIndexOf("/"));
        AtomicReference<NanoHTTPD.Response> resp = new AtomicReference<>();
        resp.set(HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.RULE_SUB_API_INTERNAL_ERROR)))));
        switch (subUri) {
            case "/logs" -> limitRequestMethod(session, NanoHTTPD.Method.GET).ifPresentOrElse(resp::set, () -> {
                try {
                    resp.set(logs(session));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            case "/interval" -> limitRequestMethod(session, NanoHTTPD.Method.POST).ifPresentOrElse(resp::set, () -> {
                try {
                    resp.set(changeCheckInterval(session));
                } catch (IOException | NanoHTTPD.ResponseException e) {
                    throw new RuntimeException(e);
                }
            });
            case "/list" ->
                    limitRequestMethod(session, NanoHTTPD.Method.GET).ifPresentOrElse(resp::set, () -> resp.set(list(session)));
            case "/save" -> limitRequestMethod(session, NanoHTTPD.Method.POST).ifPresentOrElse(resp::set, () -> {
                try {
                    resp.set(save(session));
                } catch (IOException | SQLException | NanoHTTPD.ResponseException e) {
                    throw new RuntimeException(e);
                }
            });
            case "/remove" -> limitRequestMethod(session, NanoHTTPD.Method.DELETE).ifPresentOrElse(resp::set, () -> {
                try {
                    resp.set(delete(session));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            case "/enable" -> limitRequestMethod(session, NanoHTTPD.Method.POST).ifPresentOrElse(resp::set, () -> {
                try {
                    resp.set(switcher(session, true));
                } catch (IOException | SQLException | NanoHTTPD.ResponseException e) {
                    throw new RuntimeException(e);
                }
            });
            case "/disable" -> limitRequestMethod(session, NanoHTTPD.Method.POST).ifPresentOrElse(resp::set, () -> {
                try {
                    resp.set(switcher(session, false));
                } catch (IOException | SQLException | NanoHTTPD.ResponseException e) {
                    throw new RuntimeException(e);
                }
            });
            case "/update" -> limitRequestMethod(session, NanoHTTPD.Method.POST).ifPresentOrElse(resp::set, () -> {
                try {
                    resp.set(update(session));
                } catch (NanoHTTPD.ResponseException | IOException e) {
                    throw new RuntimeException(e);
                }
            });
            default -> log.error(Lang.RULE_SUB_API_INTERNAL_ERROR);
        }
        return resp.get();
    }

    private NanoHTTPD.Response changeCheckInterval(NanoHTTPD.IHTTPSession session) throws IOException, NanoHTTPD.ResponseException {
        session.parseBody(new HashMap<String, String>(1)); // damn you, NanoHTTPD
        String checkInterval = session.getParameters().getOrDefault("checkInterval", List.of("")).get(0);
        if (StrUtil.isEmpty(checkInterval)) {
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_NO_CHECK_INTERVAL))));
        }
        ipBlackRuleList.changeCheckInterval(Long.parseLong(checkInterval));
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_CHECK_INTERVAL_UPDATED))));
    }

    private NanoHTTPD.Response logs(NanoHTTPD.IHTTPSession session) throws SQLException {
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        int pageIndex = Integer.parseInt(session.getParameters().getOrDefault("pageIndex", List.of("0")).get(0));
        int pageSize = Integer.parseInt(session.getParameters().getOrDefault("pageSize", List.of("100")).get(0));
        Map<String, Object> map = new HashMap<>();
        map.put("pageIndex", pageIndex);
        map.put("pageSize", pageSize);
        map.put("results", ipBlackRuleList.queryRuleSubLogs(ruleId, pageIndex, pageSize));
        map.put("total", ipBlackRuleList.countRuleSubLogs(ruleId));
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_LOG_QUERY_SUCCESS, "data", map))));
    }

    /**
     * 手动更新订阅规则
     *
     * @param session 请求
     * @return 响应
     */
    private NanoHTTPD.Response update(NanoHTTPD.IHTTPSession session) throws NanoHTTPD.ResponseException, IOException {
        session.parseBody(new HashMap<String, String>(1)); // damn you, NanoHTTPD
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        ipBlackRuleList.getIpBanMatchers().stream().filter(ele -> StrUtil.isEmpty(ruleId) || ele.getRuleId().equals(ruleId))
                .forEach(ele -> ipBlackRuleList.updateRule(Objects.requireNonNull(ipBlackRuleList.getRuleSubsConfig().getConfigurationSection(ele.getRuleId())), Lang.IP_BAN_RULE_UPDATE_TYPE_MANUAL));
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_UPDATED))));
    }

    /**
     * 启用/禁用订阅规则
     *
     * @param session 请求
     * @param enabled 启用/禁用
     * @return 响应
     */
    private NanoHTTPD.Response switcher(NanoHTTPD.IHTTPSession session, boolean enabled) throws SQLException, IOException, NanoHTTPD.ResponseException {
        session.parseBody(new HashMap<String, String>(1)); // damn you, NanoHTTPD
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        if (StrUtil.isEmpty(ruleId)) {
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_CANT_FIND))));
        }
        RuleSubInfo ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
        if (null == ruleSubInfo) {
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_CANT_FIND))));
        }
        if (enabled != ruleSubInfo.enabled()) {
            ConfigurationSection configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfo(ruleId, !ruleSubInfo.enabled(), ruleSubInfo.ruleName(), ruleSubInfo.subUrl(), 0, 0));
            ipBlackRuleList.updateRule(configurationSection, Lang.IP_BAN_RULE_UPDATE_TYPE_MANUAL);
        }
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", enabled ? Lang.IP_BAN_RULE_ENABLED : Lang.IP_BAN_RULE_DISABLED))));
    }

    /**
     * 删除订阅规则
     *
     * @param session 请求
     * @return 响应
     */
    private NanoHTTPD.Response delete(NanoHTTPD.IHTTPSession session) throws IOException {
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        if (StrUtil.isEmpty(ruleId)) {
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_CANT_FIND))));
        }
        ipBlackRuleList.deleteRuleSubInfo(ruleId);
        ipBlackRuleList.getIpBanMatchers().removeIf(ele -> ele.getRuleId().equals(ruleId));
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_DELETED))));
    }

    /**
     * 保存订阅规则
     *
     * @param session 请求
     * @return 响应
     */
    private NanoHTTPD.Response save(NanoHTTPD.IHTTPSession session) throws IOException, SQLException, NanoHTTPD.ResponseException {
        session.parseBody(new HashMap<String, String>(1)); // damn you, NanoHTTPD
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        if (StrUtil.isEmpty(ruleId)) {
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_CANT_FIND))));
        }
        String enableStr = session.getParameters().getOrDefault("enabled", List.of("false")).get(0);
        String ruleName = session.getParameters().getOrDefault("ruleName", List.of("")).get(0);
        String subUrl = session.getParameters().getOrDefault("subUrl", List.of("")).get(0);
        if (!List.of("TRUE", "FALSE").contains(enableStr.toUpperCase()) || StrUtil.isEmpty(ruleName) || StrUtil.isEmpty(subUrl)) {
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_PARAM_WRONG))));
        }
        boolean enabled = BooleanUtil.toBoolean(enableStr);
        ConfigurationSection configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfo(ruleId, enabled, ruleName, subUrl, 0, 0));
        ipBlackRuleList.updateRule(configurationSection, Lang.IP_BAN_RULE_UPDATE_TYPE_MANUAL);
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_SAVED))));
    }

    /**
     * 查询订阅规则
     *
     * @param session 请求
     * @return 响应
     */
    private NanoHTTPD.Response list(NanoHTTPD.IHTTPSession session) {
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        List<RuleSubInfo> collect = ipBlackRuleList.getRuleSubsConfig().getKeys(false).stream().filter(ele -> StrUtil.isEmpty(ruleId) || ele.equals(ruleId))
                .map(ele -> {
                    try {
                        return ipBlackRuleList.getRuleSubInfo(ele);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS, "data", collect))));
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
