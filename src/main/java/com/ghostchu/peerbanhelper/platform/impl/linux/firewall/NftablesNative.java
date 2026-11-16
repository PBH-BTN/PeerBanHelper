package com.ghostchu.peerbanhelper.platform.impl.linux.firewall;

import com.ghostchu.peerbanhelper.platform.firewall.FirewallException;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Minimal FFM binding for the libnftables command API.
 */
final class NftablesNative implements AutoCloseable {
    private static final String[] LIBRARY_NAMES = {
            "libnftables.so.1",
            "libnftables.so",
            "nftables.so.1"
    };

    private final Arena arena;
    private final MemorySegment context;
    private final MethodHandle runCommand;
    private final MethodHandle bufferOutput;
    private final MethodHandle getOutputBuffer;
    private final MethodHandle unbufferOutput;
    private final MethodHandle bufferError;
    private final MethodHandle getErrorBuffer;
    private final MethodHandle unbufferError;
    private final MethodHandle freeContext;
    private boolean closed;

    NftablesNative() {
        this.arena = Arena.ofConfined();
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup library = loadLibrary(arena);

            MethodHandle newContext = linker.downcallHandle(
                    required(library, "nft_ctx_new"),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
            );
            this.context = (MemorySegment) newContext.invokeExact(0);
            if (context.address() == 0) {
                throw new FirewallException("libnftables returned a null context");
            }

            this.runCommand = linker.downcallHandle(
                    required(library, "nft_run_cmd_from_buffer"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
            this.bufferOutput = linker.downcallHandle(
                    required(library, "nft_ctx_buffer_output"),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );
            this.getOutputBuffer = linker.downcallHandle(
                    required(library, "nft_ctx_get_output_buffer"),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
            this.unbufferOutput = linker.downcallHandle(
                    required(library, "nft_ctx_unbuffer_output"),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );
            this.bufferError = linker.downcallHandle(
                    required(library, "nft_ctx_buffer_error"),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );
            this.getErrorBuffer = linker.downcallHandle(
                    required(library, "nft_ctx_get_error_buffer"),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
            );
            this.unbufferError = linker.downcallHandle(
                    required(library, "nft_ctx_unbuffer_error"),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );
            this.freeContext = linker.downcallHandle(
                    required(library, "nft_ctx_free"),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
            );
        } catch (Throwable throwable) {
            arena.close();
            if (throwable instanceof FirewallException firewallException) {
                throw firewallException;
            }
            throw new FirewallException("Unable to initialize libnftables through FFM", throwable);
        }
    }

    String run(String command) {
        if (closed) {
            throw new IllegalStateException("libnftables binding is closed");
        }
        try {
            MemorySegment commandSegment = arena.allocateFrom(command);
            bufferOutput.invokeExact(context);
            bufferError.invokeExact(context);
            try {
                int result = (int) runCommand.invokeExact(context, commandSegment);
                String output = readBuffer(getOutputBuffer);
                String error = readBuffer(getErrorBuffer);
                if (result != 0) {
                    String detail = error.isBlank() ? output : error;
                    throw new FirewallException(
                            "libnftables rejected the command (code " + result + ")"
                                    + (detail.isBlank() ? "" : ": " + detail.strip())
                    );
                }
                return output;
            } finally {
                unbufferOutput.invokeExact(context);
                unbufferError.invokeExact(context);
            }
        } catch (FirewallException firewallException) {
            throw firewallException;
        } catch (Throwable throwable) {
            throw new FirewallException("Unable to execute a libnftables command", throwable);
        }
    }

    private String readBuffer(MethodHandle getter) throws Throwable {
        MemorySegment buffer = (MemorySegment) getter.invokeExact(context);
        if (buffer.address() == 0) {
            return "";
        }
        return buffer.reinterpret(Long.MAX_VALUE).getString(0);
    }

    private static SymbolLookup loadLibrary(Arena arena) {
        Throwable lastFailure = null;
        for (String libraryName : LIBRARY_NAMES) {
            try {
                return SymbolLookup.libraryLookup(libraryName, arena);
            } catch (IllegalArgumentException exception) {
                lastFailure = exception;
            }
        }
        throw new FirewallException("libnftables is not installed or cannot be loaded", lastFailure);
    }

    private static MemorySegment required(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol)
                .orElseThrow(() -> new FirewallException("libnftables is missing symbol: " + symbol));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        FirewallException failure = null;
        try {
            freeContext.invokeExact(context);
        } catch (Throwable throwable) {
            failure = new FirewallException("Unable to free the libnftables context", throwable);
        } finally {
            arena.close();
        }
        if (failure != null) {
            throw failure;
        }
    }
}
