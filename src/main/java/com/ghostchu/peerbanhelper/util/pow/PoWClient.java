package com.ghostchu.peerbanhelper.util.pow;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PoW 无交互验证码（客户端）
 *
 */
public class PoWClient {
    private final int threadCount = Runtime.getRuntime().availableProcessors();
    private final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    public byte[] solve(byte[] challenge, int difficultyBits, String algorithm) throws Exception {
        AtomicBoolean found = new AtomicBoolean(false);
        CompletableFuture<byte[]> resultFuture = new CompletableFuture<>();

        for (int t = 0; t < threadCount; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    MessageDigest digest = MessageDigest.getInstance(algorithm);
                    ByteBuffer buffer = ByteBuffer.allocate(8);
                    long nonce = threadId;
                    while (!found.get()) {
                        digest.reset();
                        digest.update(challenge);
                        buffer.clear();
                        buffer.putLong(nonce);
                        byte[] nonceBytes = buffer.array();
                        digest.update(nonceBytes);
                        byte[] hash = digest.digest();

                        if (hasLeadingZeroBits(hash, difficultyBits)) {
                            if (found.compareAndSet(false, true)) {
                                resultFuture.complete(nonceBytes.clone());
                            }
                            break;
                        }
                        nonce += threadCount;
                    }
                } catch (Exception e) {
                    resultFuture.completeExceptionally(e);
                }
            });
        }

        byte[] result = resultFuture.get(); // wait for one thread to finish
        executor.shutdownNow();
        return result;
    }

    private boolean hasLeadingZeroBits(byte[] hash, int bits) {
        int fullBytes = bits / 8;
        int remainingBits = bits % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (hash[i] != 0) return false;
        }
        if (remainingBits > 0) {
            int mask = 0xFF << (8 - remainingBits);
            return (hash[fullBytes] & mask) == 0;
        }
        return true;
    }
}