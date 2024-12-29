package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.TranslationComponent;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public abstract class AbstractRuleFeatureModule extends AbstractFeatureModule implements RuleFeatureModule {
    @Getter
    private boolean register;
    public static final CheckResult TEAPOT_CHECK_RESULT = new CheckResult(AbstractRuleFeatureModule.class, PeerAction.NO_ACTION, 0, new TranslationComponent("N/A"), new TranslationComponent("I'm a teapot"));
    public static final CheckResult OK_CHECK_RESULT = new CheckResult(AbstractRuleFeatureModule.class, PeerAction.NO_ACTION, 0, new TranslationComponent("N/A"), new TranslationComponent("Check passed"));
    public static final CheckResult HANDSHAKING_CHECK_RESULT = new CheckResult(AbstractRuleFeatureModule.class, PeerAction.NO_ACTION, 0, new TranslationComponent("N/A"), new TranslationComponent("Peer handshaking"));
    @Autowired
    @Getter
    private ModuleMatchCache cache;

    public boolean isHandShaking(Peer peer) {
        // 跳过此 Peer，速度都是0，可能是没有完成握手
        return peer.isHandshaking();
    }

    /**
     * 标记此模块不处理 Peer 封禁
     *
     * @return 占位 BanResult
     */
    @NotNull
    public CheckResult teapot() {
        return TEAPOT_CHECK_RESULT;
    }

    @NotNull
    public CheckResult pass() {
        return OK_CHECK_RESULT;
    }

    @NotNull
    public CheckResult handshaking() {
        return HANDSHAKING_CHECK_RESULT;
    }

}
