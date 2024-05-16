package com.ghostchu.peerbanhelper.module.impl.webapi;

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
        }, () -> log.error("未找到 IPBlackRuleList 模块，无法启用 RuleSubApi 模块"));
    }

    @Override
    public void onDisable() {
        getServer().getWebManagerServer().unregister(this);
    }

    @Override
    public boolean shouldHandle(String uri) {
        return uri.equals("/api/rulesub");
    }

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session) {
        String method = null;
        List<String> methodParam = session.getParameters().get("method");
        if (null != methodParam && !methodParam.isEmpty()) {
            method = methodParam.get(0);
        }
        try {
            if ("query".equals(method)) {
                return query(session);
            } else if ("save".equals(method)) {
                return save(session);
            } else if ("delete".equals(method)) {
                return delete(session);
            } else if ("enable".equals(method)) {
                return switcher(session, true);
            } else if ("disable".equals(method)) {
                return switcher(session, false);
            } else if ("upgrade".equals(method)) {
                return upgrade(session);
            } else if ("logs".equals(method)) {
                return logs(session);
            }
            log.error(Lang.WEB_RULESUB_INTERNAL_ERROR);
        } catch (SQLException | IOException e) {
            log.error(Lang.WEB_RULESUB_INTERNAL_ERROR, e);
        }
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", "未知错误"))));
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
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", "查询成功", "data", map))));
    }

    /**
     * 手动更新订阅规则
     *
     * @param session 请求
     * @return 响应
     */
    private NanoHTTPD.Response upgrade(NanoHTTPD.IHTTPSession session) {
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        ipBlackRuleList.getIpBanMatchers().stream().filter(ele -> StrUtil.isEmpty(ruleId) || ele.getRuleId().equals(ruleId))
                .forEach(ele -> ipBlackRuleList.updateRule(Objects.requireNonNull(ipBlackRuleList.getRuleSubsConfig().getConfigurationSection(ele.getRuleId())), "手动更新"));
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", "订阅规则已更新"))));
    }

    /**
     * 启用/禁用订阅规则
     *
     * @param session 请求
     * @param enabled 启用/禁用
     * @return 响应
     */
    private NanoHTTPD.Response switcher(NanoHTTPD.IHTTPSession session, boolean enabled) throws SQLException, IOException {
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        if (StrUtil.isEmpty(ruleId)) {
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", "未找到对应的订阅规则: ruleId为空"))));
        }
        RuleSubInfo ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
        if (null == ruleSubInfo) {
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", String.format("未找到对应的订阅规则: %s", ruleId)))));
        }
        if (enabled != ruleSubInfo.enabled()) {
            ConfigurationSection configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfo(ruleId, !ruleSubInfo.enabled(), ruleSubInfo.ruleName(), ruleSubInfo.subUrl(), 0, 0));
            ipBlackRuleList.updateRule(configurationSection, "手动更新");
        }
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", "订阅规则已" + (enabled ? "启用" : "禁用")))));
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
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", "未找到对应的订阅规则: ruleId为空"))));
        }
        ipBlackRuleList.deleteRuleSubInfo(ruleId);
        ipBlackRuleList.getIpBanMatchers().removeIf(ele -> ele.getRuleId().equals(ruleId));
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true))));
    }

    /**
     * 保存订阅规则
     *
     * @param session 请求
     * @return 响应
     */
    private NanoHTTPD.Response save(NanoHTTPD.IHTTPSession session) throws IOException, SQLException {
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        if (StrUtil.isEmpty(ruleId)) {
            return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", "未找到对应的订阅规则: ruleId为空"))));
        }
        String enabled = session.getParameters().getOrDefault("enabled", List.of("false")).get(0);
        String ruleName = session.getParameters().getOrDefault("ruleName", List.of("")).get(0);
        String subUrl = session.getParameters().getOrDefault("subUrl", List.of("")).get(0);
        ConfigurationSection configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfo(ruleId, Boolean.parseBoolean(enabled), ruleName, subUrl, 0, 0));
        ipBlackRuleList.updateRule(configurationSection, "手动更新");
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true))));
    }

    /**
     * 查询订阅规则
     *
     * @param session 请求
     * @return 响应
     */
    private NanoHTTPD.Response query(NanoHTTPD.IHTTPSession session) {
        String ruleId = session.getParameters().getOrDefault("ruleId", List.of("")).get(0);
        List<RuleSubInfo> collect = ipBlackRuleList.getRuleSubsConfig().getKeys(false).stream().filter(ele -> StrUtil.isEmpty(ruleId) || ele.equals(ruleId))
                .map(ele -> {
                    try {
                        return ipBlackRuleList.getRuleSubInfo(ele);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();
        return HTTPUtil.cors(NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", "查询成功", "data", collect))));
    }


}
