package com.ghostchu.peerbanhelper.platform.impl.win32.amsi;

import com.ghostchu.peerbanhelper.platform.types.MalwareScanner;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

@Slf4j
public class AmsiScanner implements MalwareScanner {

    private boolean available;
    /**
     * 持久化的 Arena，保持 amsiContext / amsiSession 句柄的生命周期，
     * 直到 {@link #close()} 被调用。
     */
    private final Arena arena = Arena.ofShared();

    /** 句柄值（opaque pointer）存储在 Arena 分配的 segment 中 */
    private final MemorySegment amsiContext;
    private final MemorySegment amsiSession;

    public AmsiScanner(String appName) {
        MemorySegment ctxHandle;
        MemorySegment sessHandle;
        try {
            // 分配 out-pointer（一个指针大小的 segment）
            MemorySegment ctxOut = arena.allocate(ValueLayout.ADDRESS);

            // 将 appName 编码为 UTF-16LE（Windows LPCWSTR），末尾加两字节 null
            byte[] nameBytes = appName.getBytes(StandardCharsets.UTF_16LE);
            MemorySegment nameWStr = arena.allocate(nameBytes.length + 2L);
            MemorySegment.copy(nameBytes, 0, nameWStr, ValueLayout.JAVA_BYTE, 0, nameBytes.length);

            int hr = AmsiLib.amsiInitialize(nameWStr, ctxOut);
            if (hr != 0) {
                throw new IllegalStateException("Failed to initialize AMSI. HRESULT: 0x" + Integer.toHexString(hr));
            }
            ctxHandle = ctxOut.get(ValueLayout.ADDRESS, 0);

            MemorySegment sessOut = arena.allocate(ValueLayout.ADDRESS);
            hr = AmsiLib.amsiOpenSession(ctxHandle, sessOut);
            if (hr != 0) {
                throw new IllegalStateException("Failed to open AMSI session. HRESULT: 0x" + Integer.toHexString(hr));
            }
            sessHandle = sessOut.get(ValueLayout.ADDRESS, 0);
            available = true;
        } catch (Exception e) {
            available = false;
            log.debug("Unable to initialize AMSI: {}", e.getMessage());
            arena.close();
            throw e;
        }
        this.amsiContext = ctxHandle;
        this.amsiSession = sessHandle;
    }

    @Override
    public boolean isMalicious(@NotNull File file) throws UnsupportedOperationException {
        if (!available) throw new UnsupportedOperationException("AMSI is not available.");
        var filePath = file.getAbsolutePath();
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r");
             FileChannel channel = raf.getChannel()) {
            long fileSize = channel.size();
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            // 零拷贝：直接将 MappedByteBuffer 包装为 MemorySegment
            MemorySegment bufSegment = MemorySegment.ofBuffer(buffer);

            try (Arena callArena = Arena.ofConfined()) {
                MemorySegment resultOut = callArena.allocate(ValueLayout.JAVA_INT);
                MemorySegment contentNameWStr = toWStr(callArena, filePath);

                AmsiLib.amsiScanBuffer(
                        amsiContext,
                        bufSegment,
                        (int) fileSize,
                        contentNameWStr,
                        amsiSession,
                        resultOut
                );
                return resultOut.get(ValueLayout.JAVA_INT, 0) >= 32768;
            }
        } catch (Exception e) {
            log.debug("AMSI scan file failed: {}", e.getMessage());
            Sentry.captureException(e);
            return false;
        }
    }

    @Override
    public boolean isMalicious(@NotNull String content) throws UnsupportedOperationException {
        if (!available) throw new UnsupportedOperationException("AMSI is not available.");
        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment resultOut = callArena.allocate(ValueLayout.JAVA_INT);
            MemorySegment stringWStr = toWStr(callArena, content);
            MemorySegment labelWStr  = toWStr(callArena, "StringScan");

            AmsiLib.amsiScanString(amsiContext, stringWStr, labelWStr, amsiSession, resultOut);
            return resultOut.get(ValueLayout.JAVA_INT, 0) >= 32768;
        }
    }

    @Override
    public void close() {
        if (!available) return;
        available = false;
        try {
            AmsiLib.amsiCloseSession(amsiContext, amsiSession);
            AmsiLib.amsiUninitialize(amsiContext);
        } finally {
            arena.close();
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * 将 Java String 编码为以 null 结尾的 UTF-16LE（Windows LPCWSTR）segment
     */
    private static MemorySegment toWStr(Arena arena, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_16LE);
        MemorySegment seg = arena.allocate(bytes.length + 2L);
        MemorySegment.copy(bytes, 0, seg, ValueLayout.JAVA_BYTE, 0, bytes.length);
        return seg;
    }
}