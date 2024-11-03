package com.ghostchu.peerbanhelper.module.impl.runner;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRunner;
import com.ghostchu.peerbanhelper.module.PeerAction;
import com.ghostchu.peerbanhelper.module.RunnerAction;
import com.ghostchu.peerbanhelper.module.impl.registry.RunnerRegistryManager;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.rule.Rule;
import com.ghostchu.peerbanhelper.util.rule.RuleMatchResult;
import com.ghostchu.peerbanhelper.util.rule.RuleParser;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class PeerIdRunner extends AbstractRunner {
    private final RunnerRegistryManager runnerRegistryManager;

    public PeerIdRunner(RunnerRegistryManager runnerRegistryManager) {
        super();
        this.runnerRegistryManager = runnerRegistryManager;
    }

    @Override
    public @NotNull RunnerAction shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
        List<Rule> ruleList = runnerRegistryManager.get(getClass(), Rule.class);
        RuleMatchResult matchResult = RuleParser.matchRule(ruleList, peer.getPeerId());
        if (matchResult.hit()) {
            return RunnerAction.HIT;
        }
        return RunnerAction.MISS;
    }
}
