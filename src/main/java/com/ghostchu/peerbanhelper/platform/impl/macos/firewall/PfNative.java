package com.ghostchu.peerbanhelper.platform.impl.macos.firewall;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Minimal FFM probe for Darwin's PF control device.
 *
 * <p>The state-changing PF ioctl ABI embeds several version-specific,
 * pointer-bearing rule structures. This probe deliberately stops before
 * advertising support unless a non-disruptive anchor lifecycle can be
 * guaranteed.</p>
 */
final class PfNative {
    private static final int O_RDWR = 0x0002;
    private static final String PF_DEVICE = "/dev/pf";

    private PfNative() {
    }

    static ProbeResult probe() {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            MethodHandle open = linker.downcallHandle(
                    linker.defaultLookup().find("open")
                            .orElseThrow(() -> new IllegalStateException("libc is missing open")),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
            MethodHandle close = linker.downcallHandle(
                    linker.defaultLookup().find("close")
                            .orElseThrow(() -> new IllegalStateException("libc is missing close")),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
            );
            MethodHandle getEuid = linker.downcallHandle(
                    linker.defaultLookup().find("geteuid")
                            .orElseThrow(() -> new IllegalStateException("libc is missing geteuid")),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT)
            );

            int effectiveUid = (int) getEuid.invokeExact();
            MemorySegment path = arena.allocateFrom(PF_DEVICE);
            int descriptor = (int) open.invokeExact(path, O_RDWR);
            if (descriptor < 0) {
                return new ProbeResult(
                        false,
                        effectiveUid,
                        effectiveUid == 0
                                ? "macOS PF device could not be opened"
                                : "macOS PF requires root privileges to open /dev/pf"
                );
            }
            try {
                return new ProbeResult(
                        true,
                        effectiveUid,
                        "macOS PF is present, but no non-disruptive PeerBanHelper anchor is installed"
                );
            } finally {
                close.invokeExact(descriptor);
            }
        } catch (Throwable throwable) {
            return new ProbeResult(
                    false,
                    -1,
                    "macOS PF probe failed: " + throwable.getMessage()
            );
        }
    }

    record ProbeResult(boolean deviceAvailable, int effectiveUid, String detail) {
    }
}
