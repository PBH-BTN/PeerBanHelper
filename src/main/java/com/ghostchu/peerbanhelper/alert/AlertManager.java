package com.ghostchu.peerbanhelper.alert;

import com.ghostchu.peerbanhelper.database.dao.impl.AlertDao;
import com.ghostchu.peerbanhelper.database.table.AlertEntity;
import com.ghostchu.peerbanhelper.push.PushManager;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
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

    public void publishAlert(boolean push, AlertLevel level, String identifier, TranslationComponent title, TranslationComponent content) {
        try {
            if (identifier != null && alertDao.identifierAlertExists(identifier)) {
                return;
            }
            AlertEntity alertEntity = new AlertEntity();
            alertEntity.setLevel(level);
            if (identifier != null) {
                alertEntity.setIdentifier(identifier);
            } else {
                alertEntity.setIdentifier(UUID.randomUUID().toString() + System.currentTimeMillis());
            }
            alertEntity.setTitle(title);
            alertEntity.setContent(content);
            alertEntity.setCreateAt(new Timestamp(System.currentTimeMillis()));
            alertDao.create(alertEntity);
            if (push) {
                if (!pushManager.pushMessage(tlUI(title), tlUI(content))) {
                    log.error("Unable to push alert via push providers");
                }
            }
        } catch (Exception e) {
            log.error("Unable to publish alert", e);
        }

    }

}
