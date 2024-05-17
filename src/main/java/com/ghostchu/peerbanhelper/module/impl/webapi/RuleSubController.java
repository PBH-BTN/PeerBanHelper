package com.ghostchu.peerbanhelper.module.impl.webapi;

import cn.hutool.core.util.StrUtil;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.database.RuleSubInfo;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.FeatureModule;
import com.ghostchu.peerbanhelper.module.impl.rule.IPBlackRuleList;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

@Slf4j
public class RuleSubController extends AbstractFeatureModule {

    IPBlackRuleList ipBlackRuleList;

    public RuleSubController(PeerBanHelperServer server, YamlConfiguration profile) {
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
            getServer().getJavalinWebContainer().getJavalin()
                    // 修改检查间隔
                    .patch("/api/sub", this::changeCheckInterval)
                    // 新增订阅规则
                    .put("/api/sub/rule", this::add)
                    // 更新订阅规则
                    .get("/api/sub/rule/update/{ruleId}", ctx -> ctx.json(update(ctx.pathParam("ruleId"))))
                    // 查询订阅规则
                    .get("/api/sub/rule/{ruleId}", this::get)
                    // 修改订阅规则
                    .post("/api/sub/rule/{ruleId}", ctx -> save(ctx, ctx.pathParam("ruleId")))
                    // 删除订阅规则
                    .delete("/api/sub/rule/{ruleId}", this::delete)
                    // 启用/禁用订阅规则
                    .patch("/api/sub/rule/{ruleId}", ctx -> ctx.json(switcher(ctx, ctx.pathParam("ruleId"))))
                    // 查询订阅规则列表
                    .get("/api/sub/rules", this::list)
                    // 手动更新全部订阅规则
                    .get("/api/sub/rules/update", ctx -> ctx.json(updateAll()))
                    // 查询全部订阅规则更新日志
                    .get("/api/sub/logs", ctx -> logs(ctx, null))
                    // 查询订阅规则更新日志
                    .get("/api/sub/logs/{ruleId}", ctx -> logs(ctx, ctx.pathParam("ruleId")));
        }, () -> log.error(Lang.RULE_SUB_API_NO_DEPENDENCY));
    }

    @Override
    public void onDisable() {
    }

    /**
     * 修改检查间隔
     *
     * @param ctx 上下文
     */
    private void changeCheckInterval(Context ctx) {
        String checkInterval = ctx.formParam("checkInterval");
        String respStr = JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.RULE_SUB_API_INTERNAL_ERROR));
        if (StrUtil.isEmpty(checkInterval)) {
            ctx.status(HttpStatus.BAD_REQUEST);
            respStr = JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_NO_CHECK_INTERVAL));
        } else {
            try {
                ipBlackRuleList.changeCheckInterval(Long.parseLong(checkInterval));
                ctx.status(HttpStatus.OK);
                respStr = JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_CHECK_INTERVAL_UPDATED));
            } catch (IOException e) {
                log.error(Lang.RULE_SUB_API_INTERNAL_ERROR, e);
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        ctx.json(respStr);
    }

    /**
     * 查询订阅规则日志
     *
     * @param ctx    上下文
     * @param ruleId 规则ID
     */
    private void logs(Context ctx, String ruleId) {
        int pageIndex = Integer.parseInt(Objects.requireNonNullElse(ctx.queryParam("pageIndex"), "0"));
        int pageSize = Integer.parseInt(Objects.requireNonNullElse(ctx.queryParam("pageSize"), "100"));
        Map<String, Object> map = new HashMap<>();
        map.put("pageIndex", pageIndex);
        map.put("pageSize", pageSize);
        String respStr = "";
        try {
            map.put("results", ipBlackRuleList.queryRuleSubLogs(ruleId, pageIndex, pageSize));
            map.put("total", ipBlackRuleList.countRuleSubLogs(ruleId));
            ctx.status(HttpStatus.OK);
            respStr = JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_LOG_QUERY_SUCCESS, "data", map));
        } catch (SQLException e) {
            log.error(Lang.RULE_SUB_API_INTERNAL_ERROR, e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        ctx.json(respStr);
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
        if (StrUtil.isEmpty(ruleId)) {
            return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_CANT_FIND));
        }
        ipBlackRuleList.getIpBanMatchers().stream().filter(ele -> ele.getRuleId().equals(ruleId))
                .forEach(ele -> ipBlackRuleList.updateRule(Objects.requireNonNull(ipBlackRuleList.getRuleSubsConfig().getConfigurationSection(ele.getRuleId())), Lang.IP_BAN_RULE_UPDATE_TYPE_MANUAL));
        return JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_UPDATED));
    }

    /**
     * 启用/禁用订阅规则
     *
     * @param ctx    上下文
     * @param ruleId 规则ID
     * @return 响应
     */
    private String switcher(Context ctx, String ruleId) {
        boolean enabled = Boolean.parseBoolean(Objects.requireNonNullElse(ctx.formParam("enabled"), "false"));
        if (StrUtil.isEmpty(ruleId)) {
            return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_CANT_FIND));
        }
        try {
            RuleSubInfo ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
            if (null == ruleSubInfo) {
                return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_CANT_FIND));
            }
            if (enabled != ruleSubInfo.enabled()) {
                ConfigurationSection configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfo(ruleId, !ruleSubInfo.enabled(), ruleSubInfo.ruleName(), ruleSubInfo.subUrl(), 0, 0));
                ipBlackRuleList.updateRule(configurationSection, Lang.IP_BAN_RULE_UPDATE_TYPE_MANUAL);
            }
        } catch (SQLException | IOException e) {
            log.error(Lang.RULE_SUB_API_INTERNAL_ERROR, e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            return JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.RULE_SUB_API_INTERNAL_ERROR));
        }
        return JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", enabled ? Lang.IP_BAN_RULE_ENABLED : Lang.IP_BAN_RULE_DISABLED));
    }

    /**
     * 删除订阅规则
     *
     * @param ctx 上下文
     */
    private void delete(Context ctx) {
        String ruleId = Objects.requireNonNullElse(ctx.pathParam("ruleId"), "");
        if (StrUtil.isEmpty(ruleId)) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_CANT_FIND)));
        }
        try {
            ipBlackRuleList.deleteRuleSubInfo(ruleId);
        } catch (IOException e) {
            log.error(Lang.RULE_SUB_API_INTERNAL_ERROR, e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.RULE_SUB_API_INTERNAL_ERROR)));
        }
        ipBlackRuleList.getIpBanMatchers().removeIf(ele -> ele.getRuleId().equals(ruleId));
        ctx.status(HttpStatus.OK);
        ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_DELETED)));
    }

    /**
     * 新增订阅规则
     *
     * @param ctx 上下文
     */
    private void add(Context ctx) {
        String ruleId = Objects.requireNonNullElse(ctx.formParam("ruleId"), "");
        if (StrUtil.isEmpty(ruleId)) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_NO_ID)));
            return;
        }
        RuleSubInfo ruleSubInfo;
        try {
            ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
        } catch (SQLException e) {
            log.error(Lang.RULE_SUB_API_INTERNAL_ERROR, e);
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.RULE_SUB_API_INTERNAL_ERROR)));
            return;
        }
        if (ruleSubInfo != null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_ID_CONFLICT)));
            return;
        }
        save(ctx, ruleId);
    }

    /**
     * 修改订阅规则
     *
     * @param ctx    上下文
     * @param ruleId 规则ID
     */
    private void save(Context ctx, String ruleId) {
        if (StrUtil.isEmpty(ruleId)) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_NO_ID)));
            return;
        }
        boolean enabled = Boolean.parseBoolean(Objects.requireNonNullElse(ctx.formParam("enabled"), "false"));
        String ruleName = Objects.requireNonNullElse(ctx.formParam("ruleName"), "");
        String subUrl = Objects.requireNonNullElse(ctx.formParam("subUrl"), "");
        if (StrUtil.isEmpty(ruleName) || StrUtil.isEmpty(subUrl)) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_PARAM_WRONG)));
            return;
        }
        ConfigurationSection configurationSection = null;
        try {
            RuleSubInfo ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
            if (ruleSubInfo == null) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.IP_BAN_RULE_CANT_FIND)));
                return;
            }
            configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfo(ruleId, enabled, ruleName, subUrl, 0, 0));
        } catch (IOException | SQLException e) {
            log.error(Lang.RULE_SUB_API_INTERNAL_ERROR, e);
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.RULE_SUB_API_INTERNAL_ERROR)));
        }
        assert configurationSection != null;
        ipBlackRuleList.updateRule(configurationSection, Lang.IP_BAN_RULE_UPDATE_TYPE_MANUAL);
        ctx.status(HttpStatus.OK);
        ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_SAVED)));
    }

    /**
     * 查询订阅规则
     *
     * @param ctx 上下文
     */
    private void get(Context ctx) {
        String ruleId = Objects.requireNonNullElse(ctx.pathParam("ruleId"), "");
        try {
            ctx.status(HttpStatus.OK);
            ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS, "data", ipBlackRuleList.getRuleSubInfo(ruleId))));
        } catch (SQLException e) {
            log.error(Lang.RULE_SUB_API_INTERNAL_ERROR, e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.RULE_SUB_API_INTERNAL_ERROR)));
        }
    }

    /**
     * 查询订阅规则列表
     *
     * @param ctx 上下文
     */
    private void list(Context ctx) {
        String ruleId = Objects.requireNonNullElse(ctx.queryParam("ruleId"), "");
        List<String> list = ipBlackRuleList.getRuleSubsConfig().getKeys(false).stream().filter(ele -> StrUtil.isEmpty(ruleId) || ele.equals(ruleId)).toList();
        ctx.status(HttpStatus.OK);
        List<RuleSubInfo> data = new ArrayList<>(list.size());
        for (String s : list) {
            try {
                data.add(ipBlackRuleList.getRuleSubInfo(s));
            } catch (SQLException e) {
                ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
                ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", false, "message", Lang.RULE_SUB_API_INTERNAL_ERROR)));
                break;
            }
        }
        ctx.json(JsonUtil.prettyPrinting().toJson(Map.of("success", true, "message", Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS, "data", data)));
    }

}
