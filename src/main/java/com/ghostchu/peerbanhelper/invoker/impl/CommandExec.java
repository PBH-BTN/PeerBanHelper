package com.ghostchu.peerbanhelper.invoker.impl;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.invoker.BanListInvoker;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.ghostchu.peerbanhelper.Main.DEF_LOCALE;
import static com.ghostchu.peerbanhelper.text.TextManager.tl;
import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class CommandExec implements BanListInvoker {
    private List<String> resetCommands;
    private List<String> banCommands;
    private List<String> unbanCommands;
    private boolean enabled = true;

    public CommandExec(PeerBanHelperServer server) {
        if (!Main.getMainConfig().getBoolean("banlist-invoker.command-exec.enabled", false)) {
            this.enabled = false;
            return;
        }
        this.resetCommands = Main.getMainConfig().getStringList("banlist-invoker.command-exec.reset");
        this.banCommands = Main.getMainConfig().getStringList("banlist-invoker.command-exec.ban");
        this.unbanCommands = Main.getMainConfig().getStringList("banlist-invoker.command-exec.unban");
        log.info(tlUI(Lang.BANLIST_INVOKER_REGISTERED, getClass().getName()));
    }


    @Override
    public void reset() {
        if (!enabled) {
            return;
        }
        for (String c : this.resetCommands) {
            try {
                invokeCommand(c, Collections.emptyMap());
            } catch (IOException | ExecutionException | InterruptedException | TimeoutException e) {
                log.error(tlUI(Lang.COMMAND_EXECUTOR_FAILED), e);
            }
        }
    }

    @Override
    public void add(@NotNull PeerAddress peer, @NotNull BanMetadata banMetadata) {
        if (!enabled) {
            return;
        }
        Map<String, String> map = makeMap(peer, banMetadata);
        for (String c : this.banCommands) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                c = c.replace("{%" + e.getKey() + "%}", e.getValue());
            }
            try {
                invokeCommand(c, map);
            } catch (IOException | ExecutionException | InterruptedException | TimeoutException e) {
                log.error(tlUI(Lang.COMMAND_EXECUTOR_FAILED), e);
            }
        }
    }

    @Override
    public void remove(@NotNull PeerAddress peer, @NotNull BanMetadata banMetadata) {
        if (!enabled) {
            return;
        }
        Map<String, String> map = makeMap(peer, banMetadata);
        for (String c : this.unbanCommands) {
            for (Map.Entry<String, String> e : map.entrySet()) {
                c = c.replace("{%" + e.getKey() + "%}", e.getValue());
            }
            try {
                invokeCommand(c, map);
            } catch (IOException | ExecutionException | InterruptedException | TimeoutException e) {
                log.error(tlUI(Lang.COMMAND_EXECUTOR_FAILED), e);
            }
        }
    }

    public int invokeCommand(String command, Map<String, String> env) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        StringTokenizer st = new StringTokenizer(command);
        String[] cmdarray = new String[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++) {
            cmdarray[i] = st.nextToken();
        }
        ProcessBuilder builder = new ProcessBuilder(cmdarray)
                .inheritIO();
        Map<String, String> liveEnv = builder.environment();
        liveEnv.putAll(env);
        Process p = builder.start();
        Process process = p.onExit().get(10, TimeUnit.SECONDS);
        if (process.isAlive()) {
            process.destroy();
            log.error(tlUI(Lang.COMMAND_EXECUTOR_FAILED_TIMEOUT), command);
            return -9999;
        }
        return process.exitValue();
    }

    private Map<String, String> makeMap(@NotNull PeerAddress peer, @NotNull BanMetadata banMetadata) {
        Map<String, String> argMap = new HashMap<>();
        argMap.put("peer.ip", peer.getIp());
        argMap.put("peer.port", String.valueOf(peer.getPort()));
        argMap.put("meta.context", banMetadata.getContext());
        argMap.put("meta.description", tl(DEF_LOCALE, banMetadata.getDescription()));
        argMap.put("meta.banAt", String.valueOf(banMetadata.getBanAt()));
        argMap.put("meta.unbanAt", String.valueOf(banMetadata.getUnbanAt()));
        argMap.put("meta.peer.id", banMetadata.getPeer().getId());
        argMap.put("meta.peer.clientName", banMetadata.getPeer().getClientName());
        argMap.put("meta.peer.uploaded", String.valueOf(banMetadata.getPeer().getUploaded()));
        argMap.put("meta.peer.downloaded", String.valueOf(banMetadata.getPeer().getDownloaded()));
        argMap.put("meta.peer.progress", String.valueOf(banMetadata.getPeer().getProgress()));
        argMap.put("meta.torrent.id", String.valueOf(banMetadata.getTorrent().getId()));
        argMap.put("meta.torrent.name", String.valueOf(banMetadata.getTorrent().getName()));
        argMap.put("meta.torrent.hash", String.valueOf(banMetadata.getTorrent().getHash()));
        argMap.put("meta.torrent.size", String.valueOf(banMetadata.getTorrent().getSize()));
        return argMap;

    }

}
