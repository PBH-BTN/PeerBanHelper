package com.ghostchu.peerbanhelper.alert;

import com.ghostchu.peerbanhelper.database.dao.impl.AlertDao;
import com.ghostchu.peerbanhelper.database.table.AlertEntity;
import com.ghostchu.peerbanhelper.push.PushManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public final class AlertManager {
    private final AlertDao alertDao;
    private final PushManager pushManager;

    public AlertManager(AlertDao alertDao, PushManager pushManager) {
        this.alertDao = alertDao;
        this.pushManager = pushManager;
        CommonUtil.getScheduler().scheduleWithFixedDelay(this::cleanup, 0, 1, TimeUnit.DAYS);

    }

    private void cleanup() {
        try {
            int removed = this.alertDao.deleteOldAlerts(new Timestamp(System.currentTimeMillis() - 1209600000));
            log.info(tlUI(Lang.ALERT_MANAGER_CLEAN_UP, removed));
        } catch (SQLException e) {
            log.warn("Unable to cleanup expired history alerts", e);
        }
    }

    /**
     * 移除已发布的警报
     * @param identifier 标识符
     */
    public void markAlertAsRead(String identifier) {
        try {
            if (identifier == null || !alertDao.identifierAlertExists(identifier)) {
                return;
            }
            alertDao.markAsRead(identifier);
        } catch (Exception e) {
            log.error(tlUI(Lang.UNABLE_READ_ALERT), e);
        }
    }

    /**
     * 检查指定标识符的警报是否存在且处于未读状态
     * @param identifier 标识符
     */
    public boolean identifierAlertExists(String identifier) {
        try {
            return alertDao.identifierAlertExists(identifier);
        } catch (SQLException e) {
            log.warn("Unable query alert for identifier {}", identifier, e);
            return false;
        }
    }

    /**
     * 检查指定标识符的警报是否存在，无论是否已读
     * @param identifier 标识符
     */
    public boolean identifierAlertExistsIncludeRead(String identifier) {
        try {
            return alertDao.identifierAlertExistsIncludeRead(identifier);
        } catch (SQLException e) {
            log.warn("Unable query alert for identifier {}", identifier, e);
            return false;
        }
    }

    /**
     * 发布警报
     *
     * @param push       是否使用推送渠道进行推送？否则只有 WebUI 能看到
     * @param level      事件等级
     * @param identifier 事件标识符，同一个表示符在同一时间只能有一个未读的存在，如果已经有一个未读的相同标识符的警报存在，新的警报将被忽略; null 则随机生成一个
     * @param title      警报标题
     * @param content    警报内容
     */
    public void publishAlert(boolean push, AlertLevel level, String identifier, TranslationComponent title, TranslationComponent content) {
        try {
            // 当前如果已有相同的 identifier 的未读警报，则不重复发送
            if (identifier != null && alertDao.identifierAlertExists(identifier)) {
                return;
            }
            AlertEntity alertEntity = new AlertEntity();
            alertEntity.setLevel(level);
            alertEntity.setIdentifier(Objects.requireNonNullElseGet(identifier, () -> UUID.randomUUID().toString() + System.currentTimeMillis()));
            alertEntity.setTitle(title);
            alertEntity.setContent(content);
            alertEntity.setCreateAt(new Timestamp(System.currentTimeMillis()));
            alertDao.create(alertEntity);
            if (push) {
                if (!pushManager.pushMessage("[PeerBanHelper/" + level.name() + "] " + tlUI(title), tlUI(content))) {
                    log.error(tlUI(Lang.UNABLE_TO_PUSH_ALERT_VIA_PROVIDERS));
                }
            }
        } catch (Exception e) {
            log.error(tlUI(Lang.UNABLE_TO_PUSH_ALERT), e);
        }

    }

}