package com.ghostchu.peerbanhelper.invoker.impl;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.PeerBanHelperServer;
import com.ghostchu.peerbanhelper.invoker.BanListInvoker;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.wrapper.BanMetadata;
import com.ghostchu.peerbanhelper.wrapper.PeerAddress;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Slf4j
public final class IPFilterInvoker implements BanListInvoker {
    private final PeerBanHelperServer server;
    private File ipFilterDat;
    private boolean enabled = true;

    @SneakyThrows
    public IPFilterInvoker(PeerBanHelperServer server) {
        this.server = server;
        if (!Main.getMainConfig().getBoolean("banlist-invoker.ipfilter-dat.enabled", false)) {
            this.enabled = false;
            return;
        }
        ipFilterDat = new File(Main.getDataDirectory(), "ipfilter.dat");
        if (!ipFilterDat.exists()) {
            if (!ipFilterDat.getParentFile().exists()) {
                ipFilterDat.getParentFile().mkdirs();
            }
            ipFilterDat.createNewFile();
        }
        log.info(tlUI(Lang.BANLIST_INVOKER_REGISTERED, getClass().getName()));
    }

    @Override
    public void reset() {
        if (!enabled) {
            return;
        }
        try (FileWriter fileWriter = new FileWriter(ipFilterDat)) {
            fileWriter.write("");
            fileWriter.flush();
        } catch (IOException e) {
            log.error(tlUI(Lang.BANLIST_INVOKER_IPFILTER_FAIL), e);
        }
    }

    @Override
    public void add(@NotNull PeerAddress peer, @NotNull BanMetadata banMetadata) {
        if (!enabled) {
            return;
        }
        try (FileWriter fileWriter = new FileWriter(ipFilterDat, true)) {
            fileWriter.write(generateIpFilterLine(peer) + "\n");
            fileWriter.flush();
        } catch (IOException e) {
            log.error(tlUI(Lang.BANLIST_INVOKER_IPFILTER_FAIL), e);
        }
    }

    @Override
    public void remove(@NotNull PeerAddress peer, @NotNull BanMetadata banMetadata) {
        if (!enabled) {
            return;
        }
        try (FileWriter fileWriter = new FileWriter(ipFilterDat)) {
            for (PeerAddress peerAddress : server.getBannedPeers().keySet()) {
                fileWriter.write(generateIpFilterLine(peerAddress) + "\n");
            }
            fileWriter.flush();
        } catch (IOException e) {
            log.error(tlUI(Lang.BANLIST_INVOKER_IPFILTER_FAIL), e);
        }
    }

    public String generateIpFilterLine(PeerAddress address) {
        return address.getAddress() + "-" + address.getAddress() + ",90,[L1]PBH Generated Rule-" + UUID.randomUUID();
    }
}
