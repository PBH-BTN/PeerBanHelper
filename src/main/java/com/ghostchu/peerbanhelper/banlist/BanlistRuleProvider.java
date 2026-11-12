package com.ghostchu.peerbanhelper.banlist;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.event.banlist.BanlistRuleProviderUpdateEvent;
import inet.ipaddr.IPAddress;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A source of IP addresses that can be loaded into the prefilled ban list.
 * The map value is the rule comment or description shown in the WebUI.
 */
public interface BanlistRuleProvider {
    @NotNull String getBanlistProviderName();

    @NotNull Map<IPAddress, String> getBanlist();

    default void notifyBanlistUpdated() {
        Main.getEventBus().post(new BanlistRuleProviderUpdateEvent());
    }
}
