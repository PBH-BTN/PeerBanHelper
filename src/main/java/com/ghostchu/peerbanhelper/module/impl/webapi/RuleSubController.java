package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.table.PeerNameRuleSubInfoEntity;
import com.ghostchu.peerbanhelper.database.table.RuleSubInfoEntity;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.IPBanRuleUpdateType;
import com.ghostchu.peerbanhelper.module.ModuleManagerImpl;
import com.ghostchu.peerbanhelper.module.impl.rule.IPBlackRuleList;
import com.ghostchu.peerbanhelper.module.impl.rule.PeerNameBlackRuleList;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.SubInfoDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.query.Pageable;
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

/**
 * 规则订阅控制器，支持 IP 规则和 PeerName 规则
 * 通过 ?type=ip 或 ?type=peer-name 参数区分
 */
@Slf4j
@Component
public final class RuleSubController extends AbstractFeatureModule {
    private static final String TYPE_IP = "ip";
    private static final String TYPE_PEER_NAME = "peer-name";

    @Autowired
    private JavalinWebContainer webContainer;

    @Autowired
    private ModuleManagerImpl moduleManager;

    private IPBlackRuleList ipBlackRuleList;
    private PeerNameBlackRuleList peerNameBlackRuleList;

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
        // 初始化 IP 规则列表
        moduleManager.getModules().stream()
                .filter(ele -> ele.getConfigName().equals("ip-address-blocker-rules"))
                .findFirst()
                .ifPresent(ele -> ipBlackRuleList = (IPBlackRuleList) ele);

        // 初始化 PeerName 规则列表
        moduleManager.getModules().stream()
                .filter(ele -> ele.getConfigName().equals("peer-name-blocker-rules"))
                .findFirst()
                .ifPresent(ele -> peerNameBlackRuleList = (PeerNameBlackRuleList) ele);

        // 注册路由
        webContainer.javalin()
                // 查询检查间隔
                .get("/api/sub/interval", this::getCheckInterval, Role.USER_READ)
                // 修改检查间隔
                .patch("/api/sub/interval", this::changeCheckInterval, Role.USER_WRITE)
                // 新增订阅规则
                .put("/api/sub/rule", ctx -> save(ctx, null, true), Role.USER_WRITE)
                // 更新订阅规则
                .post("/api/sub/rule/{ruleId}/update", ctx -> ctx.json(update(ctx, ctx.pathParam("ruleId"))), Role.USER_WRITE)
                // 查询订阅规则
                .get("/api/sub/rule/{ruleId}", ctx -> ctx.json(get(ctx, ctx.pathParam("ruleId"))), Role.USER_READ)
                // 修改订阅规则
                .post("/api/sub/rule/{ruleId}", ctx -> save(ctx, ctx.pathParam("ruleId"), false), Role.USER_WRITE)
                // 删除订阅规则
                .delete("/api/sub/rule/{ruleId}", this::delete, Role.USER_WRITE)
                // 启用/禁用订阅规则
                .patch("/api/sub/rule/{ruleId}", this::switcher, Role.USER_WRITE)
                // 查询订阅规则列表
                .get("/api/sub/rules", ctx -> ctx.json(list(ctx)), Role.USER_READ)
                // 手动更新全部订阅规则
                .post("/api/sub/rules/update", ctx -> ctx.json(updateAll(ctx)), Role.USER_WRITE)
                // 查询全部订阅规则更新日志
                .get("/api/sub/logs", ctx -> logs(ctx, null), Role.USER_READ)
                // 查询订阅规则更新日志
                .get("/api/sub/logs/{ruleId}", ctx -> logs(ctx, ctx.pathParam("ruleId")), Role.USER_READ);
    }

    @Override
    public void onDisable() {
    }

    /**
     * 获取规则类型，默认为 ip
     */
    private String getType(Context ctx) {
        return ctx.queryParam("type") != null ? ctx.queryParam("type") : TYPE_IP;
    }

    /**
     * 检查规则类型是否有效
     */
    private boolean isValidType(String type) {
        return TYPE_IP.equals(type) || TYPE_PEER_NAME.equals(type);
    }

    /**
     * 检查对应类型的模块是否可用
     */
    private boolean isModuleAvailable(String type) {
        if (TYPE_IP.equals(type)) {
            return ipBlackRuleList != null;
        } else if (TYPE_PEER_NAME.equals(type)) {
            return peerNameBlackRuleList != null;
        }
        return false;
    }

    /**
     * 返回模块不可用错误
     */
    private void moduleNotAvailable(Context ctx, String type) {
        ctx.status(HttpStatus.BAD_REQUEST);
        ctx.json(new StdResp(false, tl(locale(ctx), Lang.RULE_SUB_API_INTERNAL_ERROR, "Module for type '" + type + "' is not available"), null));
    }

    /**
     * 查询检查间隔
     */
    private void getCheckInterval(Context ctx) {
        String type = getType(ctx);
        if (!isValidType(type)) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, "Invalid type parameter. Use 'ip' or 'peer-name'", null));
            return;
        }
        if (!isModuleAvailable(type)) {
            moduleNotAvailable(ctx, type);
            return;
        }

        if (TYPE_IP.equals(type)) {
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_CHECK_INTERVAL_QUERY_SUCCESS), ipBlackRuleList.getCheckInterval()));
        } else {
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.PEER_NAME_RULE_CHECK_INTERVAL_QUERY_SUCCESS), peerNameBlackRuleList.getCheckInterval()));
        }
    }

    /**
     * 修改检查间隔
     */
    private void changeCheckInterval(Context ctx) {
        String type = getType(ctx);
        if (!isValidType(type)) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, "Invalid type parameter. Use 'ip' or 'peer-name'", null));
            return;
        }
        if (!isModuleAvailable(type)) {
            moduleNotAvailable(ctx, type);
            return;
        }

        try {
            long interval = JsonUtil.readObject(ctx.body()).get("checkInterval").getAsLong();
            if (TYPE_IP.equals(type)) {
                ipBlackRuleList.changeCheckInterval(interval);
                ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_CHECK_INTERVAL_UPDATED), null));
            } else {
                peerNameBlackRuleList.changeCheckInterval(interval);
                ctx.json(new StdResp(true, tl(locale(ctx), Lang.PEER_NAME_RULE_CHECK_INTERVAL_UPDATED), null));
            }
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            if (TYPE_IP.equals(type)) {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_CHECK_INTERVAL_WRONG_PARAM), null));
            } else {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PEER_NAME_RULE_CHECK_INTERVAL_WRONG_PARAM), null));
            }
        }
    }

    /**
     * 查询订阅规则日志
     */
    private void logs(Context ctx, String ruleId) {
        String type = getType(ctx);
        if (!isValidType(type)) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, "Invalid type parameter. Use 'ip' or 'peer-name'", null));
            return;
        }
        if (!isModuleAvailable(type)) {
            moduleNotAvailable(ctx, type);
            return;
        }

        try {
            Pageable pageable = new Pageable(ctx);
            if (TYPE_IP.equals(type)) {
                ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_LOG_QUERY_SUCCESS), ipBlackRuleList.queryRuleSubLogs(ruleId, pageable)));
            } else {
                ctx.json(new StdResp(true, tl(locale(ctx), Lang.PEER_NAME_RULE_LOG_QUERY_SUCCESS), peerNameBlackRuleList.queryRuleSubLogs(ruleId, pageable)));
            }
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            if (TYPE_IP.equals(type)) {
                log.error(tlUI(Lang.IP_BAN_RULE_LOG_QUERY_ERROR), e);
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_LOG_QUERY_WRONG_PARAM), null));
            } else {
                log.error(tlUI(Lang.PEER_NAME_RULE_LOG_QUERY_ERROR), e);
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PEER_NAME_RULE_LOG_QUERY_WRONG_PARAM), null));
            }
        }
    }

    /**
     * 手动更新全部订阅规则
     */
    private StdResp updateAll(Context ctx) {
        String type = getType(ctx);
        String locale = locale(ctx);
        if (!isValidType(type)) {
            return new StdResp(false, "Invalid type parameter. Use 'ip' or 'peer-name'", null);
        }
        if (!isModuleAvailable(type)) {
            return new StdResp(false, tl(locale, Lang.RULE_SUB_API_INTERNAL_ERROR, "Module for type '" + type + "' is not available"), null);
        }

        AtomicReference<StdResp> result = new AtomicReference<>();
        if (TYPE_IP.equals(type)) {
            ipBlackRuleList.getRuleSubsConfig().getKeys(false).stream()
                    .map(k -> updateIpRule(locale, k))
                    .filter(ele -> !ele.isSuccess())
                    .findFirst()
                    .ifPresentOrElse(result::set, () ->
                            result.set(new StdResp(true, tl(locale, Lang.IP_BAN_RULE_ALL_UPDATED), null)));
        } else {
            peerNameBlackRuleList.getRuleSubsConfig().getKeys(false).stream()
                    .map(k -> updatePeerNameRule(locale, k))
                    .filter(ele -> !ele.isSuccess())
                    .findFirst()
                    .ifPresentOrElse(result::set, () ->
                            result.set(new StdResp(true, tl(locale, Lang.PEER_NAME_RULE_ALL_UPDATED), null)));
        }
        return result.get();
    }

    /**
     * 手动更新订阅规则
     */
    private StdResp update(Context ctx, String ruleId) {
        String type = getType(ctx);
        String locale = locale(ctx);
        if (!isValidType(type)) {
            return new StdResp(false, "Invalid type parameter. Use 'ip' or 'peer-name'", null);
        }
        if (!isModuleAvailable(type)) {
            return new StdResp(false, tl(locale, Lang.RULE_SUB_API_INTERNAL_ERROR, "Module for type '" + type + "' is not available"), null);
        }

        if (TYPE_IP.equals(type)) {
            return updateIpRule(locale, ruleId);
        } else {
            return updatePeerNameRule(locale, ruleId);
        }
    }

    private StdResp updateIpRule(String locale, String ruleId) {
        if (ruleId == null || ruleId.isEmpty()) {
            return new StdResp(false, tlUI(Lang.IP_BAN_RULE_NO_ID), null);
        }
        ConfigurationSection configurationSection = ipBlackRuleList.getRuleSubsConfig().getConfigurationSection(ruleId);
        if (null == configurationSection) {
            return new StdResp(false, tlUI(Lang.IP_BAN_RULE_CANT_FIND, ruleId), null);
        }
        return ipBlackRuleList.updateRule(locale, configurationSection, IPBanRuleUpdateType.MANUAL);
    }

    private StdResp updatePeerNameRule(String locale, String ruleId) {
        if (ruleId == null || ruleId.isEmpty()) {
            return new StdResp(false, tlUI(Lang.PEER_NAME_RULE_NO_ID), null);
        }
        ConfigurationSection configurationSection = peerNameBlackRuleList.getRuleSubsConfig().getConfigurationSection(ruleId);
        if (null == configurationSection) {
            return new StdResp(false, tlUI(Lang.PEER_NAME_RULE_CANT_FIND, ruleId), null);
        }
        return peerNameBlackRuleList.updateRule(locale, configurationSection, IPBanRuleUpdateType.MANUAL);
    }

    /**
     * 启用/禁用订阅规则
     */
    private void switcher(Context ctx) throws SQLException, IOException {
        String type = getType(ctx);
        if (!isValidType(type)) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, "Invalid type parameter. Use 'ip' or 'peer-name'", null));
            return;
        }
        if (!isModuleAvailable(type)) {
            moduleNotAvailable(ctx, type);
            return;
        }

        String ruleId = ctx.pathParam("ruleId");
        boolean enabled;
        try {
            enabled = JsonUtil.readObject(ctx.body()).get("enabled").getAsBoolean();
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            if (TYPE_IP.equals(type)) {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_ENABLED_WRONG_PARAM), null));
            } else {
                ctx.json(new StdResp(false, tl(locale(ctx), Lang.PEER_NAME_RULE_ENABLED_WRONG_PARAM), null));
            }
            return;
        }

        if (TYPE_IP.equals(type)) {
            switchIpRule(ctx, ruleId, enabled);
        } else {
            switchPeerNameRule(ctx, ruleId, enabled);
        }
    }

    private void switchIpRule(Context ctx, String ruleId, boolean enabled) throws SQLException, IOException {
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
            ctx.json(new StdResp(false, msg, null));
        }
    }

    private void switchPeerNameRule(Context ctx, String ruleId, boolean enabled) throws SQLException, IOException {
        PeerNameRuleSubInfoEntity ruleSubInfo = peerNameBlackRuleList.getRuleSubInfo(ruleId);
        if (null == ruleSubInfo) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PEER_NAME_RULE_CANT_FIND, ruleId), null));
            return;
        }
        String msg = tl(locale(ctx), (enabled ? Lang.PEER_NAME_RULE_ENABLED : Lang.PEER_NAME_RULE_DISABLED), ruleSubInfo.getRuleName());
        if (enabled != ruleSubInfo.isEnabled()) {
            ConfigurationSection configurationSection = peerNameBlackRuleList.saveRuleSubInfo(new PeerNameRuleSubInfoEntity(ruleId, enabled, ruleSubInfo.getRuleName(), ruleSubInfo.getSubUrl(), 0, 0));
            peerNameBlackRuleList.updateRule(locale(ctx), configurationSection, IPBanRuleUpdateType.MANUAL);
            log.info(msg);
            ctx.json(new StdResp(true, msg, null));
        } else {
            ctx.json(new StdResp(false, msg, null));
        }
    }

    /**
     * 删除订阅规则
     */
    private void delete(Context ctx) throws IOException, SQLException {
        String type = getType(ctx);
        if (!isValidType(type)) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, "Invalid type parameter. Use 'ip' or 'peer-name'", null));
            return;
        }
        if (!isModuleAvailable(type)) {
            moduleNotAvailable(ctx, type);
            return;
        }

        String ruleId = ctx.pathParam("ruleId");
        if (TYPE_IP.equals(type)) {
            deleteIpRule(ctx, ruleId);
        } else {
            deletePeerNameRule(ctx, ruleId);
        }
    }

    private void deleteIpRule(Context ctx, String ruleId) throws IOException, SQLException {
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

    private void deletePeerNameRule(Context ctx, String ruleId) throws IOException, SQLException {
        PeerNameRuleSubInfoEntity ruleSubInfo = peerNameBlackRuleList.getRuleSubInfo(ruleId);
        if (null == ruleSubInfo) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PEER_NAME_RULE_CANT_FIND, ruleId), null));
            return;
        }
        peerNameBlackRuleList.deleteRuleSubInfo(ruleId);
        peerNameBlackRuleList.getPeerNameMatchers().removeIf(ele -> ele.getRuleId().equals(ruleId));
        String msg = tl(locale(ctx), Lang.PEER_NAME_RULE_DELETED, ruleSubInfo.getRuleName());
        log.info(msg);
        ctx.json(new StdResp(true, msg, null));
    }

    /**
     * 保存订阅规则（新增/修改）
     */
    private void save(Context ctx, String ruleId, boolean isAdd) throws SQLException, IOException {
        String type = getType(ctx);
        if (!isValidType(type)) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, "Invalid type parameter. Use 'ip' or 'peer-name'", null));
            return;
        }
        if (!isModuleAvailable(type)) {
            moduleNotAvailable(ctx, type);
            return;
        }

        if (TYPE_IP.equals(type)) {
            saveIpRule(ctx, ruleId, isAdd);
        } else {
            savePeerNameRule(ctx, ruleId, isAdd);
        }
    }

    private void saveIpRule(Context ctx, String ruleId, boolean isAdd) throws SQLException, IOException {
        SubInfoDTO subInfoDTO = ctx.bodyValidator(SubInfoDTO.class).get();
        if (isAdd) {
            ruleId = subInfoDTO.ruleId();
        }
        if (ruleId == null || ruleId.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_NO_ID), null));
            return;
        }
        RuleSubInfoEntity ruleSubInfo = ipBlackRuleList.getRuleSubInfo(ruleId);
        if (isAdd && ruleSubInfo != null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_ID_CONFLICT, ruleId), null));
            return;
        }
        if (!isAdd && ruleSubInfo == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_CANT_FIND, ruleId), null));
            return;
        }
        String ruleName = subInfoDTO.ruleName();
        String subUrl = subInfoDTO.subUrl();
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
            if (!msg.isSuccess()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(msg);
                return;
            }
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_SAVED), null));
        } catch (Exception e) {
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

    private void savePeerNameRule(Context ctx, String ruleId, boolean isAdd) throws SQLException, IOException {
        SubInfoDTO subInfoDTO = ctx.bodyValidator(SubInfoDTO.class).get();
        if (isAdd) {
            ruleId = subInfoDTO.ruleId();
        }
        if (ruleId == null || ruleId.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PEER_NAME_RULE_NO_ID), null));
            return;
        }
        PeerNameRuleSubInfoEntity ruleSubInfo = peerNameBlackRuleList.getRuleSubInfo(ruleId);
        if (isAdd && ruleSubInfo != null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PEER_NAME_RULE_ID_CONFLICT, ruleId), null));
            return;
        }
        if (!isAdd && ruleSubInfo == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PEER_NAME_RULE_CANT_FIND, ruleId), null));
            return;
        }
        String ruleName = subInfoDTO.ruleName();
        String subUrl = subInfoDTO.subUrl();
        if (isAdd) {
            if (ruleName == null || subUrl == null || ruleName.isEmpty() || subUrl.isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(new StdResp(false, tlUI(Lang.PEER_NAME_RULE_PARAM_WRONG), null));
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
        ConfigurationSection configurationSection = peerNameBlackRuleList.saveRuleSubInfo(new PeerNameRuleSubInfoEntity(ruleId, isAdd || ruleSubInfo.isEnabled(), ruleName, subUrl, 0, 0));
        assert configurationSection != null;
        try {
            StdResp msg = peerNameBlackRuleList.updateRule(locale(ctx), configurationSection, IPBanRuleUpdateType.MANUAL);
            if (!msg.isSuccess()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(msg);
                return;
            }
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.PEER_NAME_RULE_SAVED), null));
        } catch (Exception e) {
            if (isAdd) {
                peerNameBlackRuleList.deleteRuleSubInfo(ruleId);
            } else {
                peerNameBlackRuleList.saveRuleSubInfo(ruleSubInfo);
            }
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.PEER_NAME_RULE_URL_WRONG, ruleName), null));
            log.error("Unable to retrieve the sub from given URL", e);
        }
    }

    /**
     * 查询订阅规则
     */
    private StdResp get(Context ctx, String ruleId) throws SQLException {
        String type = getType(ctx);
        String locale = locale(ctx);
        if (!isValidType(type)) {
            return new StdResp(false, "Invalid type parameter. Use 'ip' or 'peer-name'", null);
        }
        if (!isModuleAvailable(type)) {
            return new StdResp(false, tl(locale, Lang.RULE_SUB_API_INTERNAL_ERROR, "Module for type '" + type + "' is not available"), null);
        }

        if (TYPE_IP.equals(type)) {
            return new StdResp(true, tl(locale, Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS), ipBlackRuleList.getRuleSubInfo(ruleId));
        } else {
            return new StdResp(true, tl(locale, Lang.PEER_NAME_RULE_INFO_QUERY_SUCCESS), peerNameBlackRuleList.getRuleSubInfo(ruleId));
        }
    }

    /**
     * 查询订阅规则列表
     */
    private StdResp list(Context ctx) throws SQLException {
        String type = getType(ctx);
        String locale = locale(ctx);
        if (!isValidType(type)) {
            return new StdResp(false, "Invalid type parameter. Use 'ip' or 'peer-name'", null);
        }
        if (!isModuleAvailable(type)) {
            return new StdResp(false, tl(locale, Lang.RULE_SUB_API_INTERNAL_ERROR, "Module for type '" + type + "' is not available"), null);
        }

        if (TYPE_IP.equals(type)) {
            List<String> keys = ipBlackRuleList.getRuleSubsConfig().getKeys(false).stream().toList();
            List<RuleSubInfoEntity> data = new ArrayList<>(keys.size());
            for (String s : keys) {
                data.add(ipBlackRuleList.getRuleSubInfo(s));
            }
            return new StdResp(true, tl(locale, Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS), data);
        } else {
            List<String> keys = peerNameBlackRuleList.getRuleSubsConfig().getKeys(false).stream().toList();
            List<PeerNameRuleSubInfoEntity> data = new ArrayList<>(keys.size());
            for (String s : keys) {
                data.add(peerNameBlackRuleList.getRuleSubInfo(s));
            }
            return new StdResp(true, tl(locale, Lang.PEER_NAME_RULE_INFO_QUERY_SUCCESS), data);
        }
    }
}
