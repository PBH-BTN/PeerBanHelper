package com.ghostchu.peerbanhelper.api.plugin.event;

import com.ghostchu.peerbanhelper.api.event.Event;
import com.ghostchu.peerbanhelper.api.plugin.Plugin;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class PluginDisableEvent implements Event {
    private final Plugin plugin;
}
