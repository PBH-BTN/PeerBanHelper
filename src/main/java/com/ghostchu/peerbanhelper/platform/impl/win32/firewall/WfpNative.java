package com.ghostchu.peerbanhelper.platform.impl.win32.firewall;

import com.ghostchu.peerbanhelper.platform.firewall.FirewallException;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.net.InetAddress;
import java.util.UUID;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * FFM binding for the WFP management APIs used by PeerBanHelper.
 */
final class WfpNative implements AutoCloseable {
    private static final String[] LIBRARY_NAMES = {"fwpuclnt.dll", "fwpuclnt"};

    private final Arena arena;
    private final MethodHandle engineClose;
    private final MethodHandle transactionBegin;
    private final MethodHandle transactionCommit;
    private final MethodHandle transactionAbort;
    private final MethodHandle providerAdd;
    private final MethodHandle providerDelete;
    private final MethodHandle subLayerAdd;
    private final MethodHandle subLayerDelete;
    private final MethodHandle filterAdd;
    private final MethodHandle filterDeleteById;
    private final MemorySegment engineHandle;
    private boolean closed;

    WfpNative(String sessionName) {
        this.arena = Arena.ofConfined();
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup library = loadLibrary(arena);

            MethodHandle engineOpen = linker.downcallHandle(
                    required(library, "FwpmEngineOpen0"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
            );
            this.engineClose = linker.downcallHandle(
                    required(library, "FwpmEngineClose0"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS)
            );
            this.transactionBegin = linker.downcallHandle(
                    required(library, "FwpmTransactionBegin0"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT)
            );
            this.transactionCommit = linker.downcallHandle(
                    required(library, "FwpmTransactionCommit0"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS)
            );
            this.transactionAbort = linker.downcallHandle(
                    required(library, "FwpmTransactionAbort0"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS)
            );
            this.providerAdd = linker.downcallHandle(
                    required(library, "FwpmProviderAdd0"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
            );
            this.providerDelete = linker.downcallHandle(
                    required(library, "FwpmProviderDeleteByKey0"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)
            );
            this.subLayerAdd = linker.downcallHandle(
                    required(library, "FwpmSubLayerAdd0"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)
            );
            this.subLayerDelete = linker.downcallHandle(
                    required(library, "FwpmSubLayerDeleteByKey0"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)
            );
            this.filterAdd = linker.downcallHandle(
                    required(library, "FwpmFilterAdd0"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS)
            );
            this.filterDeleteById = linker.downcallHandle(
                    required(library, "FwpmFilterDeleteById0"),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG)
            );

            try (Arena local = Arena.ofConfined()) {
                MemorySegment session = local.allocate(WfpStructures.SESSION);
                MemorySegment sessionNameSegment = WfpStructures.wideString(local, sessionName);
                session.set(ADDRESS, WfpStructures.SESSION_NAME, sessionNameSegment);
                session.set(JAVA_INT, WfpStructures.SESSION_FLAGS, WfpStructures.FWPM_SESSION_FLAG_DYNAMIC);
                session.set(JAVA_INT, WfpStructures.SESSION_TXN_WAIT, 10_000);
                MemorySegment handleOut = local.allocate(ADDRESS);
                int result = (int) engineOpen.invokeExact(
                        MemorySegment.NULL,
                        WfpStructures.RPC_C_AUTHN_WINNT,
                        MemorySegment.NULL,
                        session,
                        handleOut
                );
                checkResult(result, "FwpmEngineOpen0");
                MemorySegment handle = handleOut.get(ADDRESS, 0);
                if (handle.address() == 0) {
                    throw new FirewallException("FwpmEngineOpen0 returned a null engine handle");
                }
                this.engineHandle = handle;
            }
        } catch (Throwable throwable) {
            arena.close();
            if (throwable instanceof FirewallException firewallException) {
                throw firewallException;
            }
            throw new FirewallException("Unable to open a dynamic Windows Filtering Platform session", throwable);
        }
    }

    void addProviderAndSublayer(UUID providerKey, UUID subLayerKey, String providerName, String subLayerName) {
        beginTransaction();
        try (Arena local = Arena.ofConfined()) {
            MemorySegment provider = local.allocate(WfpStructures.PROVIDER);
            WfpStructures.writeGuid(provider, 0, providerKey);
            provider.set(ADDRESS, WfpStructures.PROVIDER_DISPLAY_NAME,
                    WfpStructures.wideString(local, providerName));

            MemorySegment providerKeySegment = local.allocate(16);
            WfpStructures.writeGuid(providerKeySegment, 0, providerKey);
            MemorySegment subLayer = local.allocate(WfpStructures.SUBLAYER);
            WfpStructures.writeGuid(subLayer, 0, subLayerKey);
            subLayer.set(ADDRESS, WfpStructures.SUBLAYER_DISPLAY_NAME,
                    WfpStructures.wideString(local, subLayerName));
            subLayer.set(ADDRESS, WfpStructures.SUBLAYER_PROVIDER_KEY, providerKeySegment);
            subLayer.set(ValueLayout.JAVA_SHORT, WfpStructures.SUBLAYER_WEIGHT, (short) 0x8000);

            checkResult((int) providerAdd.invokeExact(engineHandle, provider, MemorySegment.NULL),
                    "FwpmProviderAdd0");
            checkResult((int) subLayerAdd.invokeExact(engineHandle, subLayer, MemorySegment.NULL),
                    "FwpmSubLayerAdd0");
            commitTransaction();
        } catch (Throwable throwable) {
            abortTransactionQuietly();
            rethrow("Unable to create the PeerBanHelper WFP provider", throwable);
        }
    }

    long addFilter(UUID providerKey, UUID subLayerKey, UUID layerKey, InetAddress address, String name) {
        try (Arena local = Arena.ofConfined()) {
            MemorySegment providerKeySegment = local.allocate(16);
            WfpStructures.writeGuid(providerKeySegment, 0, providerKey);

            MemorySegment condition = local.allocate(WfpStructures.FILTER_CONDITION);
            WfpStructures.writeAddressCondition(local, condition, address);

            MemorySegment filter = local.allocate(WfpStructures.FILTER);
            filter.set(ADDRESS, WfpStructures.FILTER_DISPLAY_NAME, WfpStructures.wideString(local, name));
            filter.set(ADDRESS, WfpStructures.FILTER_PROVIDER_KEY, providerKeySegment);
            WfpStructures.writeGuid(filter, WfpStructures.FILTER_LAYER_KEY, layerKey);
            WfpStructures.writeGuid(filter, WfpStructures.FILTER_SUBLAYER_KEY, subLayerKey);
            filter.set(JAVA_INT, WfpStructures.FILTER_NUM_CONDITIONS, 1);
            filter.set(ADDRESS, WfpStructures.FILTER_CONDITIONS, condition);
            filter.set(JAVA_INT, WfpStructures.FILTER_ACTION, WfpStructures.FWP_ACTION_BLOCK);

            MemorySegment filterIdOut = local.allocate(JAVA_LONG);
            checkResult((int) filterAdd.invokeExact(
                    engineHandle,
                    filter,
                    MemorySegment.NULL,
                    filterIdOut
            ), "FwpmFilterAdd0");
            return filterIdOut.get(JAVA_LONG, 0);
        } catch (FirewallException firewallException) {
            throw firewallException;
        } catch (Throwable throwable) {
            throw new FirewallException("Unable to add a Windows Filtering Platform filter", throwable);
        }
    }

    void deleteFilter(long filterId) {
        try {
            int result = (int) filterDeleteById.invokeExact(engineHandle, filterId);
            if (Integer.toUnsignedLong(result) == Integer.toUnsignedLong(WfpStructures.FWP_E_FILTER_NOT_FOUND)) {
                return;
            }
            checkResult(result, "FwpmFilterDeleteById0");
        } catch (FirewallException firewallException) {
            throw firewallException;
        } catch (Throwable throwable) {
            throw new FirewallException("Unable to delete WFP filter " + filterId, throwable);
        }
    }

    void beginTransaction() {
        try {
            checkResult((int) transactionBegin.invokeExact(engineHandle, 0), "FwpmTransactionBegin0");
        } catch (FirewallException firewallException) {
            throw firewallException;
        } catch (Throwable throwable) {
            throw new FirewallException("Unable to begin a WFP transaction", throwable);
        }
    }

    void commitTransaction() {
        try {
            checkResult((int) transactionCommit.invokeExact(engineHandle), "FwpmTransactionCommit0");
        } catch (FirewallException firewallException) {
            throw firewallException;
        } catch (Throwable throwable) {
            throw new FirewallException("Unable to commit a WFP transaction", throwable);
        }
    }

    void abortTransactionQuietly() {
        try {
            transactionAbort.invokeExact(engineHandle);
        } catch (Throwable ignored) {
            // The original failure is more useful.
        }
    }

    void deleteProviderAndSublayer(UUID providerKey, UUID subLayerKey) {
        try (Arena local = Arena.ofConfined()) {
            MemorySegment providerKeySegment = local.allocate(16);
            WfpStructures.writeGuid(providerKeySegment, 0, providerKey);
            MemorySegment subLayerKeySegment = local.allocate(16);
            WfpStructures.writeGuid(subLayerKeySegment, 0, subLayerKey);
            checkResult((int) subLayerDelete.invokeExact(engineHandle, subLayerKeySegment),
                    "FwpmSubLayerDeleteByKey0");
            checkResult((int) providerDelete.invokeExact(engineHandle, providerKeySegment),
                    "FwpmProviderDeleteByKey0");
        } catch (FirewallException firewallException) {
            throw firewallException;
        } catch (Throwable throwable) {
            throw new FirewallException("Unable to delete PeerBanHelper WFP provider objects", throwable);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        FirewallException failure = null;
        try {
            checkResult((int) engineClose.invokeExact(engineHandle), "FwpmEngineClose0");
        } catch (Throwable throwable) {
            failure = new FirewallException("Unable to close the Windows Filtering Platform session", throwable);
        } finally {
            arena.close();
        }
        if (failure != null) {
            throw failure;
        }
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
        throw new FirewallException("Windows Filtering Platform library fwpuclnt.dll is unavailable", lastFailure);
    }

    private static MemorySegment required(SymbolLookup lookup, String symbol) {
        return lookup.find(symbol)
                .orElseThrow(() -> new FirewallException("fwpuclnt.dll is missing symbol: " + symbol));
    }

    private static void checkResult(int result, String operation) {
        if (result != WfpStructures.ERROR_SUCCESS) {
            throw new FirewallException(
                    operation + " failed with code 0x"
                            + Integer.toHexString(result).toUpperCase()
            );
        }
    }

    private static void rethrow(String message, Throwable throwable) {
        if (throwable instanceof FirewallException firewallException) {
            throw new FirewallException(message, firewallException);
        }
        throw new FirewallException(message, throwable);
    }
}
