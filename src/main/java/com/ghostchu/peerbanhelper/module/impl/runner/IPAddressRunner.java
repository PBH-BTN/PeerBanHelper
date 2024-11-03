package com.ghostchu.peerbanhelper.module.impl.runner;

import com.ghostchu.peerbanhelper.downloader.Downloader;
import com.ghostchu.peerbanhelper.module.AbstractRunner;
import com.ghostchu.peerbanhelper.module.RunnerAction;
import com.ghostchu.peerbanhelper.module.impl.registry.RunnerRegistryManager;
import com.ghostchu.peerbanhelper.peer.Peer;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.torrent.Torrent;
import com.ghostchu.peerbanhelper.util.rule.MatchResult;
import com.ghostchu.peerbanhelper.util.rule.ModuleMatchCache;
import com.ghostchu.peerbanhelper.util.rule.matcher.IPMatcher;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
@Slf4j
public class IPAddressRunner extends AbstractRunner {
    private final RunnerRegistryManager runnerRegistryManager;
    public IPAddressRunner(RunnerRegistryManager runnerRegistryManager) {
        super();
        this.runnerRegistryManager = runnerRegistryManager;
    }

    @Override
    public @NotNull RunnerAction shouldBanPeer(@NotNull Torrent torrent, @NotNull Peer peer, @NotNull Downloader downloader) {
        List<IPMatcher> ipBanMatchers = runnerRegistryManager.get(getClass(), IPMatcher.class);
        String ip = peer.getPeerAddress().getIp();
        List<IPBanResult> results = new ArrayList<>();
        try (var service = Executors.newVirtualThreadPerTaskExecutor()) {
            ipBanMatchers.forEach(rule -> service.submit(() -> {
                results.add(new IPBanResult(rule.getRuleName(), rule.match(ip)));
            }));
        }
        boolean mr = results.stream().anyMatch(ipBanResult -> {
            try {
                if (ipBanResult == null) return false;
                return ipBanResult.matchResult() == MatchResult.TRUE;
            } catch (Exception e) {
                log.error(tlUI(Lang.IP_BAN_RULE_MATCH_ERROR), e);
                return false;
            }
        });
        if (mr) {
            return RunnerAction.HIT;
        }
        return RunnerAction.MISS;
    }

    record IPBanResult(String ruleName, MatchResult matchResult) {
    }
}
