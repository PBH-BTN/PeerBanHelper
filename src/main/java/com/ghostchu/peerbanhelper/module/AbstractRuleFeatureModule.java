package com.ghostchu.peerbanhelper.module;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

@Slf4j
public abstract class AbstractRuleFeatureModule extends AbstractFeatureModule implements RuleFeatureModule {
    @Getter
    private final PeerBanHelperServer server;
    @Getter
    private boolean register;

    public AbstractRuleFeatureModule(@NotNull PeerBanHelperServer server, @NotNull YamlConfiguration profile) {
        super(server, profile);
        this.server = server;
    }

    /**
     * 标记此模块不处理 Peer 封禁
     *
     * @return 占位 BanResult
     */
    @NotNull
    protected BanResult teapot() {
        return new BanResult(this, PeerAction.NO_ACTION, "N/A", "I'm a teapot");
    }
}
