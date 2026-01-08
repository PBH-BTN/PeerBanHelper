package com.ghostchu.peerbanhelper.platform.impl.win32.amsi;

import com.ghostchu.peerbanhelper.platform.types.MalwareScanner;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

@Slf4j
public class AmsiScanner implements MalwareScanner {
    private boolean available;
    private final Pointer amsiContext;
    private final Pointer amsiSession;

    public AmsiScanner(String appName) {
        PointerByReference ctxRef = new PointerByReference();
        int hr = AmsiLib.INSTANCE.AmsiInitialize(appName, ctxRef);
        if (hr != 0) {
            throw new IllegalStateException("Failed to initialize AMSI. HRESULT: " + hr);
        }
        amsiContext = ctxRef.getValue();
        PointerByReference sessRef = new PointerByReference();
        AmsiLib.INSTANCE.AmsiOpenSession(amsiContext, sessRef);
        amsiSession = sessRef.getValue();
        available = true;
    }

    @Override
    public boolean isMalicious(@NotNull File file) throws UnsupportedOperationException {
        if (!available) throw new UnsupportedOperationException("AMSI is not available.");
        var filePath = file.getAbsolutePath();
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r");
             FileChannel channel = raf.getChannel()) {
            long fileSize = channel.size();
            // 内存映射文件
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
            // 获取映射后的内存指针
            Pointer pointer = Native.getDirectBufferPointer(buffer);
            int[] result = new int[1];
            AmsiLib.INSTANCE.AmsiScanBuffer(
                    amsiContext,
                    pointer,
                    (int) fileSize,
                    new WString(filePath),
                    amsiSession,
                    result
            );
            return result[0] >= 32768;
        } catch (Exception e) {
            log.debug("AMSI scan file failed: {}", e.getMessage());
            return false;
        }
    }

    // 扫描字符串
    public boolean isMalicious(@NotNull String content) throws UnsupportedOperationException {
        if (!available) throw new UnsupportedOperationException("AMSI is not available.");
        int[] result = new int[1];
        AmsiLib.INSTANCE.AmsiScanString(amsiContext, new WString(content), new WString("StringScan"), amsiSession, result);
        return result[0] >= 32768;
    }

    @Override
    public void close() {
        AmsiLib.INSTANCE.AmsiCloseSession(amsiContext, amsiSession);
        AmsiLib.INSTANCE.AmsiUninitialize(amsiContext);
    }

//    public static void main(String[] args) throws Exception {
//        AmsiScanner scanner = new AmsiScanner("MyJavaApp");
//
//        // 测试 EICAR 标准反病毒测试字符串
//        String eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
//        var data = scanner.isMalicious(eicar);
//        System.out.println("isMalicious: " + data);
//
//        scanner.close();
//        Thread.sleep(4000);
//    }
}