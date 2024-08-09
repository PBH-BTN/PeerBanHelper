package com.ghostchu.peerbanhelper.pbhplus;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.MsgUtil;
import com.ghostchu.peerbanhelper.util.encrypt.ActivationKeyUtil;
import com.ghostchu.simplereloadlib.ReloadResult;
import com.ghostchu.simplereloadlib.Reloadable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Date;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

/**
 * 捐赠密钥管理器
 */
@Component
@Slf4j
public class ActivationManager implements Reloadable {
    @Getter
    private String keyText;
    @Nullable
    private ActivationKeyUtil.KeyData keyData = null;

    public ActivationManager() {
        load();
        Main.getReloadManager().register(this);
    }

    public void load() {
        this.keyText = Main.getMainConfig().getString("pbh-plus-key");
        if (keyText == null || keyText.isBlank()) {
            return;
        }
        this.keyData = ActivationKeyUtil.fromKey(this.keyText);
        if (this.isActivated()) {
            log.info(tlUI(Lang.DONATION_KEY_VERIFICATION_SUCCESSFUL, keyData.licenseTo(), keyData.source(), MsgUtil.getDateFormatter().format(new Date(keyData.expireAt()))));
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
    public @Nullable ActivationKeyUtil.KeyData getKeyData() {
        return keyData;
    }

    /**
     * 检查是否已激活，如果它为 true，那么就是激活了，就是这样
     * 简单吗？是的，很简单！确实是这样
     * 你说很容易被破解？这就对了！
     *
     * @return 是否已激活，返回 true 时应启用捐赠功能
     */
    public boolean isActivated() {
        return this.keyData != null && System.currentTimeMillis() < this.keyData.expireAt();
    }
}
