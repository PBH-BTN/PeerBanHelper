package com.ghostchu.peerbanhelper.alert;

import com.ghostchu.peerbanhelper.database.dao.impl.AlertDao;
import com.ghostchu.peerbanhelper.database.table.AlertEntity;
import com.ghostchu.peerbanhelper.push.PushManager;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class AlertManager {
    private final AlertDao alertDao;
    private final PushManager pushManager;

    public AlertManager(AlertDao alertDao, PushManager pushManager) {
        this.alertDao = alertDao;
        this.pushManager = pushManager;
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