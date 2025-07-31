package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.database.dao.impl.EnhancedRuleSubInfoDao;
import com.ghostchu.peerbanhelper.database.dao.impl.EnhancedRuleSubLogDao;
import com.ghostchu.peerbanhelper.database.table.EnhancedRuleSubInfoEntity;
import com.ghostchu.peerbanhelper.database.table.EnhancedRuleSubLogEntity;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.ModuleManagerImpl;
import com.ghostchu.peerbanhelper.module.impl.rule.subscription.EnhancedRuleSubscriptionModule;
import com.ghostchu.peerbanhelper.module.impl.rule.subscription.RuleType;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.EnhancedSubInfoDTO;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.json.JsonUtil;
import com.ghostchu.peerbanhelper.util.query.Pageable;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
@Component
public final class EnhancedRuleSubController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    
    @Autowired
    private ModuleManagerImpl moduleManager;
    
    @Autowired
    private EnhancedRuleSubInfoDao enhancedRuleSubInfoDao;
    
    @Autowired
    private EnhancedRuleSubLogDao enhancedRuleSubLogDao;
    
    private EnhancedRuleSubscriptionModule enhancedRuleSubscriptionModule;
    
    @Override
    public boolean isConfigurable() {
        return false;
    }
    
    @Override
    public @NotNull String getName() {
        return "WebAPI - Enhanced Rule Subscription";
    }
    
    @Override
    public @NotNull String getConfigName() {
        return "webapi-enhanced-rule-subscription";
    }
    
    @Override
    public void onEnable() {
        moduleManager.getModules().stream()
                .filter(ele -> ele.getConfigName().equals("enhanced-rule-subscription"))
                .findFirst()
                .ifPresent(ele -> {
                    enhancedRuleSubscriptionModule = (EnhancedRuleSubscriptionModule) ele;
                    setupRoutes();
                });
    }
    
    @Override
    public void onDisable() {
    }
    
    private void setupRoutes() {
        webContainer.javalin()
                // Get rule types
                .get("/api/enhanced-sub/rule-types", this::getRuleTypes, Role.USER_READ)
                // Get check interval
                .get("/api/enhanced-sub/interval", this::getCheckInterval, Role.USER_READ)
                // Change check interval
                .patch("/api/enhanced-sub/interval", this::changeCheckInterval, Role.USER_WRITE)
                // Create subscription rule
                .put("/api/enhanced-sub/rule", ctx -> save(ctx, null, true), Role.USER_WRITE)
                // Update subscription rule
                .post("/api/enhanced-sub/rule/{ruleId}/update", ctx -> ctx.json(update(locale(ctx), ctx.pathParam("ruleId"))), Role.USER_READ)
                // Get subscription rule
                .get("/api/enhanced-sub/rule/{ruleId}", ctx -> ctx.json(get(locale(ctx), ctx.pathParam("ruleId"))), Role.USER_READ)
                // Modify subscription rule
                .post("/api/enhanced-sub/rule/{ruleId}", ctx -> save(ctx, ctx.pathParam("ruleId"), false), Role.USER_WRITE)
                // Delete subscription rule
                .delete("/api/enhanced-sub/rule/{ruleId}", this::delete, Role.USER_WRITE)
                // Enable/disable subscription rule
                .patch("/api/enhanced-sub/rule/{ruleId}", this::switcher, Role.USER_WRITE)
                // List subscription rules
                .get("/api/enhanced-sub/rules", ctx -> ctx.json(list(locale(ctx))), Role.USER_READ)
                // Manual update all subscription rules
                .post("/api/enhanced-sub/rules/update", ctx -> ctx.json(updateAll(locale(ctx))), Role.USER_WRITE)
                // Get all subscription rule logs
                .get("/api/enhanced-sub/logs", ctx -> logs(ctx, null), Role.USER_READ)
                // Get subscription rule logs
                .get("/api/enhanced-sub/logs/{ruleId}", ctx -> logs(ctx, ctx.pathParam("ruleId")), Role.USER_READ);
    }
    
    /**
     * 获取支持的规则类型
     * Get supported rule types
     */
    private void getRuleTypes(Context ctx) {
        List<RuleType> ruleTypes = Arrays.asList(RuleType.values());
        ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS), ruleTypes));
    }
    
    /**
     * 查询检查间隔
     * Get check interval
     */
    private void getCheckInterval(Context ctx) {
        ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_CHECK_INTERVAL_QUERY_SUCCESS), 
                enhancedRuleSubscriptionModule.getCheckInterval()));
    }
    
    /**
     * 修改检查间隔
     * Change check interval
     */
    private void changeCheckInterval(Context ctx) {
        try {
            long interval = JsonUtil.readObject(ctx.body()).get("checkInterval").getAsLong();
            enhancedRuleSubscriptionModule.changeCheckInterval(interval);
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_CHECK_INTERVAL_UPDATED), null));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_CHECK_INTERVAL_WRONG_PARAM), null));
        }
    }
    
    /**
     * 查询订阅规则日志
     * Get subscription rule logs
     */
    private void logs(Context ctx, String ruleId) {
        try {
            Pageable pageable = new Pageable(ctx);
            var builder = enhancedRuleSubLogDao.queryBuilder().orderBy("updateTime", false);
            if (ruleId != null) {
                builder = builder.where().eq("ruleId", ruleId).queryBuilder();
            }
            var logs = enhancedRuleSubLogDao.queryByPaging(builder, pageable);
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_LOG_QUERY_SUCCESS), logs));
        } catch (Exception e) {
            log.error(tlUI(Lang.IP_BAN_RULE_LOG_QUERY_ERROR), e);
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_LOG_QUERY_WRONG_PARAM), null));
        }
    }
    
    /**
     * 手动更新全部订阅规则
     * Manual update all subscription rules
     */
    private StdResp updateAll(String locale) {
        // TODO: Implement update all logic
        return new StdResp(true, tl(locale, Lang.IP_BAN_RULE_ALL_UPDATED), null);
    }
    
    /**
     * 手动更新订阅规则
     * Manual update subscription rule
     */
    private StdResp update(String locale, String ruleId) {
        if (ruleId == null || ruleId.isEmpty()) {
            return new StdResp(false, tlUI(Lang.IP_BAN_RULE_NO_ID), null);
        }
        
        try {
            EnhancedRuleSubInfoEntity ruleInfo = enhancedRuleSubInfoDao.queryForId(ruleId);
            if (ruleInfo == null) {
                return new StdResp(false, tlUI(Lang.IP_BAN_RULE_CANT_FIND, ruleId), null);
            }
            
            // TODO: Implement update logic
            return new StdResp(true, tl(locale, Lang.IP_BAN_RULE_UPDATED, ruleInfo.getRuleName()), null);
        } catch (SQLException e) {
            log.error("Error updating enhanced rule: {}", ruleId, e);
            return new StdResp(false, tl(locale, Lang.IP_BAN_RULE_UPDATE_FAILED, ruleId), null);
        }
    }
    
    /**
     * 启用/禁用订阅规则
     * Enable/disable subscription rule
     */
    private void switcher(Context ctx) throws SQLException {
        String ruleId = ctx.pathParam("ruleId");
        boolean enabled;
        try {
            enabled = JsonUtil.readObject(ctx.body()).get("enabled").getAsBoolean();
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_ENABLED_WRONG_PARAM), null));
            return;
        }
        
        EnhancedRuleSubInfoEntity ruleSubInfo = enhancedRuleSubInfoDao.queryForId(ruleId);
        if (ruleSubInfo == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_CANT_FIND, ruleId), null));
            return;
        }
        
        String msg = tl(locale(ctx), (enabled ? Lang.IP_BAN_RULE_ENABLED : Lang.IP_BAN_RULE_DISABLED), ruleSubInfo.getRuleName());
        if (enabled != ruleSubInfo.isEnabled()) {
            ruleSubInfo.setEnabled(enabled);
            enhancedRuleSubInfoDao.update(ruleSubInfo);
            log.info(msg);
            ctx.json(new StdResp(true, msg, null));
        } else {
            ctx.json(new StdResp(false, msg, null));
        }
    }
    
    /**
     * 删除订阅规则
     * Delete subscription rule
     */
    private void delete(Context ctx) throws SQLException {
        String ruleId = ctx.pathParam("ruleId");
        EnhancedRuleSubInfoEntity ruleSubInfo = enhancedRuleSubInfoDao.queryForId(ruleId);
        if (ruleSubInfo == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_CANT_FIND, ruleId), null));
            return;
        }
        
        enhancedRuleSubInfoDao.deleteById(ruleId);
        // Remove from matchers
        enhancedRuleSubscriptionModule.getRuleMatchers().removeIf(ele -> ele.getRuleId().equals(ruleId));
        String msg = tl(locale(ctx), Lang.IP_BAN_RULE_DELETED, ruleSubInfo.getRuleName());
        log.info(msg);
        ctx.json(new StdResp(true, msg, null));
    }
    
    /**
     * 保存订阅规则
     * Save subscription rule
     */
    private void save(Context ctx, String ruleId, boolean isAdd) throws SQLException {
        EnhancedSubInfoDTO subInfoDTO = ctx.bodyValidator(EnhancedSubInfoDTO.class).get();
        if (isAdd) {
            ruleId = subInfoDTO.ruleId();
        }
        
        if (ruleId == null || ruleId.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_NO_ID), null));
            return;
        }
        
        EnhancedRuleSubInfoEntity ruleSubInfo = enhancedRuleSubInfoDao.queryForId(ruleId);
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
        RuleType ruleType = subInfoDTO.ruleType();
        String description = subInfoDTO.description();
        
        if (isAdd) {
            if (ruleName == null || subUrl == null || ruleType == null || 
                ruleName.isEmpty() || subUrl.isEmpty()) {
                ctx.status(HttpStatus.BAD_REQUEST);
                ctx.json(new StdResp(false, tlUI(Lang.IP_BAN_RULE_PARAM_WRONG), null));
                return;
            }
        } else {
            if (ruleName == null) ruleName = ruleSubInfo.getRuleName();
            if (subUrl == null) subUrl = ruleSubInfo.getSubUrl();
            if (ruleType == null) ruleType = ruleSubInfo.getRuleTypeEnum();
            if (description == null) description = ruleSubInfo.getDescription();
        }
        
        if (ruleName.contains(".")) {
            throw new IllegalArgumentException("Illegal character (.) in name: " + ruleName);
        }
        
        try {
            EnhancedRuleSubInfoEntity newEntity = new EnhancedRuleSubInfoEntity(
                    ruleId, 
                    isAdd || ruleSubInfo.isEnabled(), 
                    ruleName, 
                    subUrl, 
                    ruleType.getCode(), 
                    0, 
                    0, 
                    description
            );
            
            if (isAdd) {
                enhancedRuleSubInfoDao.create(newEntity);
            } else {
                enhancedRuleSubInfoDao.update(newEntity);
            }
            
            ctx.json(new StdResp(true, tl(locale(ctx), Lang.IP_BAN_RULE_SAVED), null));
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(new StdResp(false, tl(locale(ctx), Lang.IP_BAN_RULE_URL_WRONG, ruleName), null));
            log.error("Unable to save enhanced rule subscription", e);
        }
    }
    
    /**
     * 查询订阅规则
     * Get subscription rule
     */
    private StdResp get(String locale, String ruleId) throws SQLException {
        EnhancedRuleSubInfoEntity ruleInfo = enhancedRuleSubInfoDao.queryForId(ruleId);
        return new StdResp(true, tl(locale, Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS), ruleInfo);
    }
    
    /**
     * 查询订阅规则列表
     * List subscription rules
     */
    private StdResp list(String locale) throws SQLException {
        List<EnhancedRuleSubInfoEntity> data = enhancedRuleSubInfoDao.queryForAll();
        return new StdResp(true, tl(locale, Lang.IP_BAN_RULE_INFO_QUERY_SUCCESS), data);
    }
}