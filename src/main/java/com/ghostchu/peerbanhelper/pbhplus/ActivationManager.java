package com.ghostchu.peerbanhelper.pbhplus;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.util.encrypt.ActivationKeyUtil;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 捐赠密钥管理器
 */
@Component
public class ActivationManager {
    @Getter
    private final String keyText;
    @Nullable
    private ActivationKeyUtil.KeyData keyData = null;

    public ActivationManager() {
        this.keyText = Main.getMainConfig().getString("pbh-plus-key");
        if (keyText == null || keyText.isBlank()) {
            return;
        }
        this.keyData = ActivationKeyUtil.fromKey(this.keyText);
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
