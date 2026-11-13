package com.ghostchu.peerbanhelper.platform.impl.win32.firewall;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Lightweight elevation probe that avoids constructing WFP objects when the
 * process cannot change firewall state.
 */
final class WindowsPrivileges {
    private WindowsPrivileges() {
    }

    static boolean isElevated() {
        try (Arena arena = Arena.ofConfined()) {
            SymbolLookup shell32 = loadShell32(arena);
            MethodHandle isUserAnAdmin = Linker.nativeLinker().downcallHandle(
                    shell32.find("IsUserAnAdmin")
                            .orElseThrow(() -> new IllegalStateException("shell32 is missing IsUserAnAdmin")),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT)
            );
            return (int) isUserAnAdmin.invokeExact() != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static SymbolLookup loadShell32(Arena arena) {
        try {
            return SymbolLookup.libraryLookup("shell32", arena);
        } catch (IllegalArgumentException ignored) {
            return SymbolLookup.libraryLookup("shell32.dll", arena);
        }
    }
}
