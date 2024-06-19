package com.ghostchu.peerbanhelper.module;

import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.builder.StateMachineBuilder;
import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.text.Lang;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * 基于状态机模型的规则模块接口
 */
public interface RuleBlocker extends FeatureModule {

    /**
     * 规则状态机构造器
     *
     * @return 状态机构造器
     */
    default StateMachineBuilder<PeerState, MatchEvents, PeerMatchRecord> ruleSmBuilder() {
        StateMachineBuilder<PeerState, MatchEvents, PeerMatchRecord> fsmBuilder = StateMachineBuilderFactory.create();
        fsmBuilder.externalTransitions().fromAmong(PeerState.INIT, PeerState.ACTIVE).to(PeerState.MATCH).on(MatchEvents.MATCH)
                .perform((from, to, event, context) -> {
                    // 匹配
                    CheckResult result = shouldBanPeer(context);
                    if (result.hit()) {
                        context.setResult(new MatchResultDetail(this, to, result.rule(), result.reason(), System.currentTimeMillis() + getServer().getDisconnectTimeout()));
                        triggerEvent(MatchEvents.HIT, context);
                    } else {
                        context.setResult(new MatchResultDetail(this, to, "N/A", "No matches", System.currentTimeMillis() + getServer().getDisconnectTimeout()));
                        triggerEvent(MatchEvents.PASS, context);
                    }
                });
        fsmBuilder.externalTransition().from(PeerState.MATCH).to(PeerState.ACTIVE).on(MatchEvents.PASS)
                .perform((from, to, event, context) -> {
                    // 活跃
                    context.setResult(new MatchResultDetail(this, to, "N/A", "No matches", System.currentTimeMillis() + getServer().getDisconnectTimeout()));
                });
        fsmBuilder.externalTransition().from(PeerState.MATCH).to(PeerState.BAN).on(MatchEvents.HIT)
                .perform((from, to, event, context) -> {
                    // 封禁
                    getLogger().debug(Lang.RULE_MODULE_PEER_BAN, getName(), context.getPeer().getAddress());
                    context.setResult(new MatchResultDetail(this, to, context.getResult().rule(), context.getResult().reason(), System.currentTimeMillis() + getServer().getBanDuration()));
                });
        /*fsmBuilder.externalTransition().from(PeerState.ACTIVE).to(PeerState.END).on(PeerEvents.DISCONNECT)
                .perform((from, to, event, context) -> {
                    // 断开连接
                    getLogger().info(Lang.RULE_MODULE_PEER_DISCONNECT, context.getPeer().getPeerId(), context.getPeer().getAddress().getIp(), context.getPeer().getAddress().getPort());
                    context.setResult(new MatchResultDetail(this, to, null, null, 0));
                });
        fsmBuilder.externalTransition().from(PeerState.BAN).to(PeerState.END).on(PeerEvents.TIMEOUT)
                .perform((from, to, event, context) -> {
                    // 解除封禁
                    getLogger().info(Lang.RULE_MODULE_PEER_BAN_TIMEOUT, context.getPeer().getPeerId(), context.getPeer().getAddress().getIp(), context.getPeer().getAddress().getPort());
                    context.setResult(new MatchResultDetail(this, to, null, null, 0));
                });*/
        return fsmBuilder;
    }

    /**
     * 获取Server
     *
     * @return Server
     */
    PeerBanHelperServer getServer();

    /**
     * 获取Logger
     *
     * @return Logger
     */
    Logger getLogger();

    /**
     * 获取状态机
     *
     * @return 状态机
     */
    StateMachine<PeerState, MatchEvents, PeerMatchRecord> getStateMachine();

    /**
     * 是否应该封禁Peer
     *
     * @param ctx 匹配上下文
     * @return 是否封禁
     */
    CheckResult shouldBanPeer(PeerMatchRecord ctx);

    /**
     * 运行规则检查
     */
    default void runCheck() {
        // long t1 = System.currentTimeMillis();
        getServer().getLivePeersSnapshot().keySet().forEach(peerAddress -> Optional.ofNullable(getServer().getMatchRecords().get(peerAddress)).ifPresent(record -> triggerEvent(MatchEvents.MATCH, record)));
        // long t2 = System.currentTimeMillis();
        // getLogger().debug(Lang.RULE_MODULE_MATCH_TIME, getName(), t2 - t1);
    }

    /**
     * 触发事件
     *
     * @param event   事件
     * @param context 上下文
     */
    default void triggerEvent(MatchEvents event, PeerMatchRecord context) {
        getStateMachine().fireEvent(context.getResult().state(), event, context);
    }

    record CheckResult(boolean hit, String rule, String reason) {

    }

}
