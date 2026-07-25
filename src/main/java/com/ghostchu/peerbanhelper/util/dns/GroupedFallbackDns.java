package com.ghostchu.peerbanhelper.util.dns;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Dns;
import org.jspecify.annotations.NonNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class GroupedFallbackDns implements Dns {
    private final List<Dns> globalGroup;
    private final List<Dns> chinaGroup;
    private final long timeoutSeconds;
    private final ExecutorService executor;

    public GroupedFallbackDns(List<Dns> globalGroup, List<Dns> chinaGroup, long timeoutSeconds) {
        this.globalGroup = globalGroup;
        this.chinaGroup = chinaGroup;
        this.timeoutSeconds = timeoutSeconds;
        // 如果是 Java 21+，强烈建议使用 Executors.newVirtualThreadPerTaskExecutor()
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public @NonNull List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        // 1. 优先尝试国际组（带 10 秒超时）
        if (!globalGroup.isEmpty()) {
            try {
                List<InetAddress> result = queryGroupConcurrently(globalGroup, hostname)
                        .get(timeoutSeconds, TimeUnit.SECONDS);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            } catch (TimeoutException e) {
                // 10秒超时，忽略并回退到中国大陆组
                log.debug("[DNS] [Global] Group all timed out.");
            } catch (Exception e) {
                // 国际组全部失败，忽略并回退到中国大陆组
                log.debug("[DNS] [Global] Group all failed.");
            }
        }
        if (!chinaGroup.isEmpty()) {
            try {
                List<InetAddress> result = queryGroupConcurrently(chinaGroup, hostname).get();
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                // 中国组也失败了
            }
        }

        if (globalGroup.isEmpty() && chinaGroup.isEmpty()) { // ????
            return Dns.SYSTEM.lookup(hostname);
        }

        throw new UnknownHostException("All DoH servers failed for: " + hostname);
    }

    /**
     * 组内并发查询：只要有一个 DNS 成功返回，就立刻完成 CompletableFuture
     */
    private CompletableFuture<List<InetAddress>> queryGroupConcurrently(List<Dns> dnsGroup, String hostname) {
        CompletableFuture<List<InetAddress>> overallFuture = new CompletableFuture<>();

        List<CompletableFuture<Void>> tasks = dnsGroup.stream().map(dns ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return dns.lookup(hostname);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, executor).thenAccept(addresses -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        // 谁先成功，谁就完成总体的 Future
                        overallFuture.complete(addresses);
                    }
                })
        ).toList();

        // 如果组内所有 DoH 都抛异常失败了，标记总体 Future 为失败
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
                .whenComplete((v, throwable) -> {
                    if (!overallFuture.isDone()) {
                        overallFuture.completeExceptionally(
                                new UnknownHostException("Group resolution failed for " + hostname)
                        );
                    }
                });

        return overallFuture;
    }
}