package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.table.RuleSubInfoEntity;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.IPBanRuleUpdateType;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import com.ghostchu.peerbanhelper.module.impl.rule.IPBlackRuleList;
import com.ghostchu.peerbanhelper.module.impl.webapi.common.SlimMsg;
import com.ghostchu.peerbanhelper.module.impl.webapi.common.StdMsg;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.JsonUtil;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.ghostchu.peerbanhelper.Main.DEF_LOCALE;
import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public class RuleSubController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    IPBlackRuleList ipBlackRuleList;

    @Autowired
    private ModuleManager moduleManager;
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
        moduleManager.getModules().stream().filter(ele -> ele.getConfigName().equals("ip-address-blocker-rules")).findFirst().ifPresent(ele -> {
            ipBlackRuleList = (IPBlackRuleList) ele;
            webContainer.javalin()
                    // 查询检查间隔
                    .get("/api/sub/interval", this::getCheckInterval, Role.USER_READ)
                    // 修改检查间隔
                    .patch("/api/sub/interval", this::changeCheckInterval, Role.USER_WRITE)
                    // 新增订阅规则
                    .put("/api/sub/rule", ctx -> save(ctx, null, true), Role.USER_WRITE)
                    // 更新订阅规则
                    .post("/api/sub/rule/{ruleId}/update", ctx -> ctx.json(update(locale(ctx), ctx.pathParam("ruleId"))), Role.USER_READ)
                    // 查询订阅规则
                    .get("/api/sub/rule/{ruleId}", ctx -> ctx.json(get(locale(ctx), ctx.pathParam("ruleId"))), Role.USER_READ)
                    // 修改订阅规则
                    .post("/api/sub/rule/{ruleId}", ctx -> save(ctx, ctx.pathParam("ruleId"), false), Role.USER_WRITE)
                    // 删除订阅规则
                    .delete("/api/sub/rule/{ruleId}", this::delete, Role.USER_WRITE)
                    // 启用/禁用订阅规则
                    .patch("/api/sub/rule/{ruleId}", this::switcher, Role.USER_WRITE)
                    // 查询订阅规则列表
                    .get("/api/sub/rules", ctx -> ctx.json(list(locale(ctx))), Role.USER_READ)
                    // 手动更新全部订阅规则
                    .post("/api/sub/rules/update", ctx -> ctx.json(updateAll()), Role.USER_WRITE)
                    // 查询全部订阅规则更新日志
                    .get("/api/sub/logs", ctx -> logs(ctx, null), Role.USER_READ)
                    // 查询订阅规则更新日志
                    .get("/api/sub/logs/{ruleId}", ctx -> logs(ctx, ctx.pathParam("ruleId")), Role.USER_READ);
        });
    }

    @Override
    public void onDisable() {
    }

    /**
     * 查询检查间隔
     *
     * @param ctx 上下文
     */
    private void getCheckInterval(Context ctx) {
        ctx.json(new StdMsg(true, tl(locale(ctx), Lang.IP_BAN_RULE_CHECK_INTERVAL_QUERY_SUCCESS), ipBlackRuleList.getCheckInterval()));
    }

    /**
     * 修改检查间隔
     *
     * @param ctx 上下文
     */
    private void changeCheckInterval(Context ctx) {
        try {
            long interval = JsonUtil.readObject(ctx.body()).get("checkInterval").getAsLong();
            ipBlackRuleList.changeCheckInterval(interval);
            ctx.json(new SlimMsg(true, tl(locale(ctx), Lang.IP_BAN_RULE_CHECK_INTERVAL_UPDATED), HttpStatus.OK.getCode()));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, tl(locale(ctx), Lang.IP_BAN_RULE_CHECK_INTERVAL_WRONG_PARAM), HttpStatus.BAD_REQUEST.getCode()));
        }
    }

    /**
     * 查询订阅规则日志
     *
     * @param ctx    上下文
     * @param ruleId 规则ID
     */
    private void logs(Context ctx, String ruleId) {
        String pageIndexStr = Objects.requireNonNullElse(ctx.queryParam("pageIndex"), "0");
        String pageSizeStr = Objects.requireNonNullElse(ctx.queryParam("pageSize"), "100");
        try {
            int pageIndex = Integer.parseInt(pageIndexStr.isEmpty() ? "0" : pageIndexStr);
            int pageSize = Integer.parseInt(pageSizeStr.isEmpty() ? "100" : pageSizeStr);
            Map<String, Object> map = new HashMap<>();
            map.put("pageIndex", pageIndex);
            map.put("pageSize", pageSize);
            map.put("results", ipBlackRuleList.queryRuleSubLogs(ruleId, pageIndex, pageSize));
            map.put("total", ipBlackRuleList.countRuleSubLogs(ruleId));
            ctx.json(new StdMsg(true, tl(locale(ctx), Lang.IP_BAN_RULE_LOG_QUERY_SUCCESS), map));
        } catch (Exception e) {
            log.error(tlUI(Lang.IP_BAN_RULE_LOG_QUERY_ERROR), e);
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, tl(locale(ctx), Lang.IP_BAN_RULE_LOG_QUERY_WRONG_PARAM), HttpStatus.BAD_REQUEST.getCode()));
        }
    }

    /**
     * 手动更新全部订阅规则
     *
     * @return 响应
     */
    private SlimMsg updateAll() {
        AtomicReference<SlimMsg> result = new AtomicReference<>();
        ipBlackRuleList.getRuleSubsConfig().getKeys(false).stream().map(k -> update(DEF_LOCALE, k)).filter(ele -> !ele.success())
                .findFirst()
                .ifPresentOrElse(result::set, () ->
                        result.set(new SlimMsg(true, tlUI(Lang.IP_BAN_RULE_ALL_UPDATED), 200)));
        return result.get();
    }

    /**
     * 手动更新订阅规则
     *
     * @param ruleId 规则ID
     * @return 响应
     */
    private SlimMsg update(String locale, String ruleId) {
        if (ruleId == null || ruleId.isEmpty()) {
            return new SlimMsg(false, tlUI(Lang.IP_BAN_RULE_NO_ID), HttpStatus.NOT_FOUND.getCode());
        }
        ConfigurationSection configurationSection = ipBlackRuleList.getRuleSubsConfig().getConfigurationSection(ruleId);
        if (null == configurationSection) {
            return new SlimMsg(false, tlUI(Lang.IP_BAN_RULE_CANT_FIND, ruleId), HttpStatus.NOT_FOUND.getCode());
        }
        return ipBlackRuleList.updateRule(locale, configurationSection, IPBanRuleUpdateType.MANUAL);
    }

    /**
     * 启用/禁用订阅规则
     *
     * @param ctx 上下文
     */
    private void switcher(Context ctx) throws SQLException, IOException {
        String ruleId = ctx.pathParam("ruleId");
        boolean enabled;
        try {
            enabled = JsonUtil.readObject(ctx.body()).get("enabled").getAsBoolean();
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, tl(locale(ctx), Lang.IP_BAN_RULE_ENABLED_WRONG_PARAM), HttpStatus.BAD_REQUEST.getCode()));
            return;
        }
        RuleSubInfoEntity ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
        if (null == ruleSubInfo) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, tl(locale(ctx), Lang.IP_BAN_RULE_CANT_FIND, ruleId), HttpStatus.BAD_REQUEST.getCode()));
            return;
        }
        String msg = tl(locale(ctx), (enabled ? Lang.IP_BAN_RULE_ENABLED : Lang.IP_BAN_RULE_DISABLED), ruleSubInfo.getRuleName());
        if (enabled != ruleSubInfo.isEnabled()) {
            ConfigurationSection configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfoEntity(ruleId, enabled, ruleSubInfo.getRuleName(), ruleSubInfo.getSubUrl(), 0, 0));
            ipBlackRuleList.updateRule(locale(ctx), configurationSection, IPBanRuleUpdateType.MANUAL);
            log.info(msg);
            ctx.json(new SlimMsg(true, msg, 200));
        } else {
            // 表示已经是启用/禁用状态
            ctx.json(new SlimMsg(false, msg, 200));
        }
    }

    /**
     * 删除订阅规则
     *
     * @param ctx 上下文
     */
    private void delete(Context ctx) throws IOException, SQLException {
        String ruleId = ctx.pathParam("ruleId");
        RuleSubInfoEntity ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
        if (null == ruleSubInfo) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, tl(locale(ctx), Lang.IP_BAN_RULE_CANT_FIND, ruleId), HttpStatus.BAD_REQUEST.getCode()));
            return;
        }
        ipBlackRuleList.deleteRuleSubInfo(ruleId);
        ipBlackRuleList.getIpBanMatchers().removeIf(ele -> ele.getRuleId().equals(ruleId));
        String msg = tl(locale(ctx), Lang.IP_BAN_RULE_DELETED, ruleSubInfo.getRuleName());
        log.info(msg);
        ctx.json(new SlimMsg(true, msg, 200));
    }

    /**
     * 修改订阅规则
     *
     * @param ctx    上下文
     * @param ruleId 规则ID
     */
    private void save(Context ctx, String ruleId, boolean isAdd) throws SQLException, IOException {
        SubInfo subInfo = ctx.bodyValidator(SubInfo.class).get();
        if (isAdd) {
            // 新增时从 body 中获取ruleId
            ruleId = subInfo.ruleId();
        }
        if (ruleId == null || ruleId.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, tl(locale(ctx), Lang.IP_BAN_RULE_NO_ID), HttpStatus.BAD_REQUEST.getCode()));
            return;
        }
        RuleSubInfoEntity ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
        if (isAdd && ruleSubInfo != null) {
            // 新增时检查规则是否存在
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, tl(locale(ctx), Lang.IP_BAN_RULE_ID_CONFLICT, ruleId), HttpStatus.BAD_REQUEST.getCode()));
            return;
        }
        if (!isAdd && ruleSubInfo == null) {
            // 更新时检查规则是否存在
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, tl(locale(ctx), Lang.IP_BAN_RULE_CANT_FIND, ruleId), HttpStatus.BAD_REQUEST.getCode()));
            return;
        }
        String ruleName = subInfo.ruleName();
        String subUrl = subInfo.subUrl();
        if (isAdd) {
            if (ruleName == null || subUrl == null || ruleName.isEmpty() || subUrl.isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(new SlimMsg(false, tlUI(Lang.IP_BAN_RULE_PARAM_WRONG), HttpStatus.BAD_REQUEST.getCode()));
                return;
            }
        } else {
            if (ruleName == null) {
                ruleName = ruleSubInfo.getRuleName();
            }
            if (subUrl == null) {
                subUrl = ruleSubInfo.getSubUrl();
            }
        }
        ConfigurationSection configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfoEntity(ruleId, isAdd || ruleSubInfo.isEnabled(), ruleName, subUrl, 0, 0));
        assert configurationSection != null;
        try {
            SlimMsg msg = ipBlackRuleList.updateRule(locale(ctx), configurationSection, IPBanRuleUpdateType.MANUAL);
            if (!msg.success()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(msg);
                return;
            }
            ctx.json(new SlimMsg(true, tlUI(Lang.IP_BAN_RULE_SAVED), HttpStatus.CREATED.getCode()));
        } catch (Exception e) {
            // 更新失败时回滚
            if (isAdd) {
                ipBlackRuleList.deleteRuleSubInfo(ruleId);
            } else {
                ipBlackRuleList.saveRuleSubInfo(ruleSubInfo);
            }
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new SlimMsg(false, tl(locale(ctx), Lang.IP_BAN_RULE_URL_WRONG, ruleName), HttpStatus.BAD_REQUEST.getCode()));
            log.error("Unable to retrieve the sub from given URL", e);
        }
    }

    /**
     * 查询订阅规则
     *
     * @param ruleId 规则ID
     * @return 响应
     */
    private StdMsg get(String locale, String ruleId) throws SQLException {
        return new StdMsg(true, tl(locale, Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS), ipBlackRuleList.getRuleSubInfo(ruleId));
    }

    /**
     * 查询订阅规则列表
     *
     * @return 响应
     */
    private StdMsg list(String locale) throws SQLException {
        List<String> list = ipBlackRuleList.getRuleSubsConfig().getKeys(false).stream().toList();
        List<RuleSubInfoEntity> data = new ArrayList<>(list.size());
        for (String s : list) {
            data.add(ipBlackRuleList.getRuleSubInfo(s));
        }
        return new StdMsg(true, tl(locale, Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS), data);
    }

    /**
     * 订阅规则信息
     *
     * @param ruleId   规则ID
     * @param enabled  是否启用
     * @param ruleName 规则名称
     * @param subUrl   订阅地址
     */
    record SubInfo(String ruleId, boolean enabled, String ruleName, String subUrl) {
    }

}
