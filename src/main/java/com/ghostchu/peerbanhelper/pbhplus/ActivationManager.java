package com.ghostchu.peerbanhelper.pbhplus;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.PBHLookAndFeelNeedReloadEvent;
import com.ghostchu.peerbanhelper.exchange.ExchangeMap;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * 捐赠密钥管理器
 */
@Component
@Slf4j
public final class ActivationManager implements Reloadable {
    private final ActivationKeyManager activationKeyManager;
    @Getter
    private String keyText;
    @Nullable
    private ActivationKeyManager.KeyData keyData = null;

    public ActivationManager(ActivationKeyManager activationKeyManager) {
        this.activationKeyManager = activationKeyManager;
        load();
        Main.getReloadManager().register(this);
    }

    public void load() {
        this.keyText = Main.getMainConfig().getString("pbh-plus-key");
        if (keyText == null || keyText.isBlank()) {
            return;
        }
        keyText = keyText.trim();
        this.keyData = activationKeyManager.fromKey(ActivationKeyManager.OFFICIAL_PUBLIC_KEY, this.keyText);
        if (this.keyData == null) {
            this.keyData = activationKeyManager.fromKey(Base64.getEncoder().encodeToString(activationKeyManager.getLocalKeyPair().getValue().getEncoded()), this.keyText);
        }
        if (keyData != null) {
            if (this.isActivated() && !this.isLocalLicense()) {
                ExchangeMap.PBH_PLUS_ACTIVATED = true;
                ExchangeMap.GUI_DISPLAY_FLAGS.add(new ExchangeMap.DisplayFlag("PBH Plus", 10, tlUI(Lang.PBH_PLUS_THANKS_FOR_DONATION_GUI_TITLE)));
                Main.getEventBus().post(new PBHLookAndFeelNeedReloadEvent());
                log.info(tlUI(Lang.DONATION_KEY_VERIFICATION_SUCCESSFUL, keyData.getLicenseTo(), keyData.getSource(), MsgUtil.getDateFormatter().format(new Date(keyData.getExpireAt()))));
            }
        }
    }

    @Override
    public ReloadResult reloadModule() throws Exception {
        load();
        return Reloadable.super.reloadModule();
    }

    /**
     * 获取激活信息，可能为 null
     *
     * @return 激活信息
     */
    public @Nullable ActivationKeyManager.KeyData getKeyData() {
        return keyData;
    }

    /**
     * 检查是否已激活
     *
     * @return 是否已激活，返回 true 时应启用捐赠功能
     */
    public boolean isActivated() {
        return this.keyData != null && System.currentTimeMillis() < this.keyData.getExpireAt();
    }

    public boolean isLocalLicense() {
        return this.keyData != null && "local".equals(this.keyData.getType());
    }
}
