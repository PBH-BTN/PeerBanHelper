package com.ghostchu.peerbanhelper.module;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** A scan snapshot. A retired plugin registration cannot run again through this snapshot. */
public record ModuleInvocation<T extends FeatureModule>(T module, String name, @Nullable PluginModuleScope scope) {
    public void invoke(Consumer<T> operation) {
        if (scope == null) {
            operation.accept(module);
        } else {
            scope.invoke(() -> operation.accept(module));
        }
    }
}
