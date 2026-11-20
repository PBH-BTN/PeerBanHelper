package com.ghostchu.peerbanhelper.configuration.pf4j;

import com.ghostchu.peerbanhelper.text.TranslationComponent;
import org.pf4j.Plugin;

import java.util.List;

/** Capture optional UI contributions while the plugin is starting. Listing plugins only reads this snapshot. */
public record PluginDisplay(TranslationComponent name, TranslationComponent description, List<Menu> menus) {
    static PluginDisplay capture(Plugin entry) {
        if (!(entry instanceof PBHPlugin plugin)) return null;
        var menus = plugin.getPluginMenu().stream().map(menu -> new Menu(menu.getMenuId(),
                menu.getDisplayName(), menu.isDisabled(), menu.getRelativeEndpoint())).toList();
        return new PluginDisplay(plugin.getPluginDisplayName(), plugin.getPluginDescription(), menus);
    }

    public record Menu(String id, TranslationComponent name, boolean disabled, String endpoint) {}
}
