package com.ghostchu.peerbanhelper.module;

import com.alibaba.cola.statemachine.StateMachine;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import org.bspfsystems.yamlconfiguration.file.YamlConfiguration;

public abstract class AbstractRuleBlocker extends AbstractFeatureModule implements RuleBlocker {

    public StateMachine<PeerState, MatchEvents, PeerMatchRecord> stateMachine;

    public AbstractRuleBlocker(PeerBanHelperServer server, YamlConfiguration profile) {
        super(server, profile);
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void onEnable() {
        stateMachine = ruleSmBuilder().build(getConfigName());
    }

    @Override
    public void onDisable() {
    }

    @Override
    public StateMachine<PeerState, MatchEvents, PeerMatchRecord> getStateMachine() {
        return stateMachine;
    }
}
