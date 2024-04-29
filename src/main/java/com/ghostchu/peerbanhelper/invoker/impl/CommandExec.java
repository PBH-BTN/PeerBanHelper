package com.ghostchu.peerbanhelper.invoker.impl;

import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.invoker.BanListInvoker;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CommandExec implements BanListInvoker {
    private List<String> resetCommands;
    private List<String> banCommands;
    private List<String> unbanCommands;
    private boolean enabled = true;

    public CommandExec(PeerBanHelperServer server) {
        if (!server.getMainConfig().getBoolean("banlist-invoker.command-exec.enabled", false)) {
            this.enabled = false;
            return;
        }
        this.resetCommands = server.getMainConfig().getStringList("banlist-invoker.command-exec.reset");
        this.banCommands = server.getMainConfig().getStringList("banlist-invoker.command-exec.ban");
        this.unbanCommands = server.getMainConfig().getStringList("banlist-invoker.command-exec.unban");
        log.info(Lang.BANLIST_INVOKER_REGISTERED, getClass().getName());
    }


    @Override
    public void reset() {
        if (!enabled) {
            return;
        }
        for (String c : this.resetCommands) {
            invokeCommand(c, new HashMap<>(0));
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
            invokeCommand(c, map);
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
            invokeCommand(c, map);
        }
    }

    private Map<String, String> makeMap(@NotNull PeerAddress peer, @NotNull BanMetadata banMetadata) {
        Map<String, String> argMap = new HashMap<>();
        argMap.put("peer.ip", peer.getIp());
        argMap.put("peer.port", String.valueOf(peer.getPort()));
        argMap.put("meta.context", banMetadata.getContext());
        argMap.put("meta.description", banMetadata.getDescription());
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

    private void invokeCommand(String command, Map<String, String> env) {
        StringTokenizer st = new StringTokenizer(command);
        String[] cmdarray = new String[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++)
            cmdarray[i] = st.nextToken();
        try {
            ProcessBuilder builder = new ProcessBuilder(cmdarray)
                    .inheritIO();
            Map<String, String> liveEnv = builder.environment();
            liveEnv.putAll(env);
            Process p = builder.start();
            p.waitFor(5, TimeUnit.SECONDS);
            if (p.isAlive()) {
                log.info(Lang.BANLIST_INVOKER_COMMAND_EXEC_TIMEOUT, command);
            }
            p.onExit().thenAccept(process -> {
                if (process.exitValue() != 0) {
                    log.warn(Lang.BANLIST_INVOKER_COMMAND_EXEC_FAILED, command, process.exitValue());
                }
            });
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
