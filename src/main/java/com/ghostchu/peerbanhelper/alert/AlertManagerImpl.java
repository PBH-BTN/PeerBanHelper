package com.ghostchu.peerbanhelper.alert;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.databasent.service.AlertService;
import com.ghostchu.peerbanhelper.databasent.table.AlertEntity;
import com.ghostchu.peerbanhelper.event.program.PBHServerStartedEvent;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import com.google.common.eventbus.Subscribe;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public final class AlertManagerImpl implements AlertManager {
    private final AlertService alertDao;
    private final NotificationManager notifications;

    public AlertManagerImpl(AlertService alertDao, NotificationManager notifications) {
        this.alertDao = alertDao;
        this.notifications = notifications;
        Main.getEventBus().register(this);
    }

    @Subscribe
    public void init(PBHServerStartedEvent event) {
        CommonUtil.getBgCleanupScheduler().scheduleWithFixedDelay(this::cleanup, 0, 1, TimeUnit.DAYS);
    }

    private void cleanup() {
        long removed = this.alertDao.deleteOldAlerts(OffsetDateTime.now().minusDays(30));
        log.info(tlUI(Lang.ALERT_MANAGER_CLEAN_UP, removed));
    }

    /**
     * 移除已发布的警报
     *
     * @param identifier 标识符
     */
    @Override
    public void markAlertAsRead(@NotNull String identifier) {
        try {
            if (!alertDao.identifierAlertExists(identifier)) {
                return;
            }
            alertDao.markAsRead(identifier);
        } catch (Exception e) {
            log.error(tlUI(Lang.UNABLE_READ_ALERT), e);
            Sentry.captureException(e);
        }
    }

    /**
     * 检查指定标识符的警报是否存在且处于未读状态
     *
     * @param identifier 标识符
     */
    @Override
    public boolean identifierAlertExists(@NotNull String identifier) {
        return alertDao.identifierAlertExists(identifier);
    }

    /**
     * 检查指定标识符的警报是否存在，无论是否已读
     *
     * @param identifier 标识符
     */
    @Override
    public boolean identifierAlertExistsIncludeRead(@NotNull String identifier) {
        return alertDao.identifierAlertExistsIncludeRead(identifier);
    }

    /**
     * 发布警报
     *
     * @param categoryId 注册的通知类别 ID
     * @param level      事件等级
     * @param identifier 事件标识符，同一个表示符在同一时间只能有一个未读的存在，如果已经有一个未读的相同标识符的警报存在，新的警报将被忽略; null 则随机生成一个
     * @param title      警报标题
     * @param content    警报内容
     */
    @Override
    public void publishAlert(@NotNull String categoryId, @NotNull AlertLevel level, @Nullable String identifier, @NotNull TranslationComponent title, @NotNull TranslationComponent content) {
        try {
            var category = notifications.requireCategory(categoryId);
            if (category.keepHistory() && !recordAlert(level, identifier, title, content)) {
                return;
            }
            notifications.deliver(category, level, title, content);
        } catch (Exception e) {
            log.error(tlUI(Lang.UNABLE_TO_PUSH_ALERT), e);
            Sentry.captureException(e);
        }

    }

    private synchronized boolean recordAlert(AlertLevel level, String identifier,
                                             TranslationComponent title, TranslationComponent content) {
        if (identifier != null && alertDao.identifierAlertExists(identifier)) {
            return false;
        }
        AlertEntity alert = new AlertEntity();
        alert.setLevel(level);
        alert.setIdentifier(Objects.requireNonNullElseGet(identifier, () -> UUID.randomUUID().toString()));
        alert.setTitle(title);
        alert.setContent(content);
        alert.setCreateAt(OffsetDateTime.now());
        alertDao.saveOrUpdate(alert);
        return true;
    }

    @Override
    public @Nullable AlertLevel getHighestUnreadAlertLevel() {
        AlertLevel alertLevel = null;
        var unreadAlerts = alertDao.getUnreadAlerts();
        for (AlertEntity unreadAlert : unreadAlerts) {
            if (alertLevel == null) {
                alertLevel = unreadAlert.getLevel();
                continue;
            }
            if (unreadAlert.getLevel().ordinal() > alertLevel.ordinal()) {
                alertLevel = unreadAlert.getLevel();
            }
        }
        return alertLevel;
    }

    @Override
    public @NotNull List<AlertEntity> getUnreadAlerts() {
        return alertDao.getUnreadAlerts();
    }

}