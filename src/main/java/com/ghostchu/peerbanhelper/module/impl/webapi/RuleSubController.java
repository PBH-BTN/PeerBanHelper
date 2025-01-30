package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.table.RuleSubInfoEntity;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.IPBanRuleUpdateType;
import com.ghostchu.peerbanhelper.module.ModuleManager;
import com.ghostchu.peerbanhelper.module.impl.rule.IPBlackRuleList;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.paging.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
@IgnoreScan
public final class RuleSubController extends AbstractFeatureModule {
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
                    .post("/api/sub/rules/update", ctx -> ctx.json(updateAll(locale(ctx))), Role.USER_WRITE)
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
        ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_CHECK_INTERVAL_QUERY_SUCCESS), ipBlackRuleList.getCheckInterval()));
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
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_CHECK_INTERVAL_UPDATED), null));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_CHECK_INTERVAL_WRONG_PARAM), null));
        }
    }

    /**
     * 查询订阅规则日志
     *
     * @param ctx    上下文
     * @param ruleId 规则ID
     */
    private void logs(Context ctx, String ruleId) {
        try {
            Pageable pageable = new Pageable(ctx);
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_LOG_QUERY_SUCCESS), ipBlackRuleList.queryRuleSubLogs(ruleId, pageable)));
        } catch (Exception e) {
            log.error(tlUI(Lang.IP_BAN_RULE_LOG_QUERY_ERROR), e);
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_LOG_QUERY_WRONG_PARAM), null));
        }
    }

    /**
     * 手动更新全部订阅规则
     *
     * @return 响应
     */
    private StdResp updateAll(String locale) {
        AtomicReference<StdResp> result = new AtomicReference<>();
        ipBlackRuleList.getRuleSubsConfig().getKeys(false).stream().map(k -> update(locale, k)).filter(ele -> !ele.success())
                .findFirst()
                .ifPresentOrElse(result::set, () ->
                        result.set(new StdResp(true, tl(locale, Lang.IP_BAN_RULE_ALL_UPDATED), null)));
        return result.get();
    }

    /**
     * 手动更新订阅规则
     *
     * @param ruleId 规则ID
     * @return 响应
     */
    private StdResp update(String locale, String ruleId) {
        if (ruleId == null || ruleId.isEmpty()) {
            return new StdResp(false, tlUI(Lang.IP_BAN_RULE_NO_ID), null);
        }
        ConfigurationSection configurationSection = ipBlackRuleList.getRuleSubsConfig().getConfigurationSection(ruleId);
        if (null == configurationSection) {
            return new StdResp(false, tlUI(Lang.IP_BAN_RULE_CANT_FIND, ruleId), null);
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
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_ENABLED_WRONG_PARAM), null));
            return;
        }
        RuleSubInfoEntity ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
        if (null == ruleSubInfo) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_CANT_FIND, ruleId), null));
            return;
        }
        String msg = tl(locale(ctx), (enabled ? Lang.IP_BAN_RULE_ENABLED : Lang.IP_BAN_RULE_DISABLED), ruleSubInfo.getRuleName());
        if (enabled != ruleSubInfo.isEnabled()) {
            ConfigurationSection configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfoEntity(ruleId, enabled, ruleSubInfo.getRuleName(), ruleSubInfo.getSubUrl(), 0, 0));
            ipBlackRuleList.updateRule(locale(ctx), configurationSection, IPBanRuleUpdateType.MANUAL);
            log.info(msg);
            ctx.json(new StdResp(true, msg, null));
        } else {
            // 表示已经是启用/禁用状态
            ctx.json(new StdResp(false, msg, null));
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
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_CANT_FIND, ruleId), null));
            return;
        }
        ipBlackRuleList.deleteRuleSubInfo(ruleId);
        ipBlackRuleList.getIpBanMatchers().removeIf(ele -> ele.getRuleId().equals(ruleId));
        String msg = tl(locale(ctx), Lang.IP_BAN_RULE_DELETED, ruleSubInfo.getRuleName());
        log.info(msg);
        ctx.json(new StdResp(true, msg, null));
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
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_NO_ID), null));
            return;
        }
        RuleSubInfoEntity ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
        if (isAdd && ruleSubInfo != null) {
            // 新增时检查规则是否存在
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_ID_CONFLICT, ruleId), null));
            return;
        }
        if (!isAdd && ruleSubInfo == null) {
            // 更新时检查规则是否存在
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_CANT_FIND, ruleId), null));
            return;
        }
        String ruleName = subInfo.ruleName();
        String subUrl = subInfo.subUrl();
        if (isAdd) {
            if (ruleName == null || subUrl == null || ruleName.isEmpty() || subUrl.isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(new StdResp(false, tlUI(Lang.IP_BAN_RULE_PARAM_WRONG), null));
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
        if (ruleName.contains(".")) {
            throw new IllegalArgumentException("Illegal character (.) in name: " + ruleName);
        }
        ConfigurationSection configurationSection = ipBlackRuleList.saveRuleSubInfo(new RuleSubInfoEntity(ruleId, isAdd || ruleSubInfo.isEnabled(), ruleName, subUrl, 0, 0));
        assert configurationSection != null;
        try {
            StdResp msg = ipBlackRuleList.updateRule(locale(ctx), configurationSection, IPBanRuleUpdateType.MANUAL);
            if (!msg.success()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(msg);
                return;
            }
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_SAVED), null));
        } catch (Exception e) {
            // 更新失败时回滚
            if (isAdd) {
                ipBlackRuleList.deleteRuleSubInfo(ruleId);
            } else {
                ipBlackRuleList.saveRuleSubInfo(ruleSubInfo);
            }
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_URL_WRONG, ruleName), null));
            log.error("Unable to retrieve the sub from given URL", e);
        }
    }

    /**
     * 查询订阅规则
     *
     * @param ruleId 规则ID
     * @return 响应
     */
    private StdResp get(String locale, String ruleId) throws SQLException {
        return new StdResp(true, tl(locale, Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS), ipBlackRuleList.getRuleSubInfo(ruleId));
    }

    /**
     * 查询订阅规则列表
     *
     * @return 响应
     */
    private StdResp list(String locale) throws SQLException {
        List<String> list = ipBlackRuleList.getRuleSubsConfig().getKeys(false).stream().toList();
        List<RuleSubInfoEntity> data = new ArrayList<>(list.size());
        for (String s : list) {
            data.add(ipBlackRuleList.getRuleSubInfo(s));
        }
        return new StdResp(true, tl(locale, Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS), data);
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
