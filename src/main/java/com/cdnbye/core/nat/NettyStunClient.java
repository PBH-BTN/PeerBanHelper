package com.cdnbye.core.nat;

import com.ghostchu.peerbanhelper.util.traversal.forwarder.iohandler.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.uring.IoUring;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class NettyStunClient {

    private static final int TRANSACTION_TIMEOUT_MS = 1000;
    private static final int TOTAL_TIMEOUT_SECONDS = 5;
    private static final String DEFAULT_STUN_HOST = "stun.cdnbye.com";
    private static final int DEFAULT_STUN_PORT = 3478;

    public static StunResult query(String localIP) {
        return query(DEFAULT_STUN_HOST, DEFAULT_STUN_PORT, localIP);
    }

    /**
     * Gets NAT info from a STUN server using Netty.
     * This method blocks until the result is available or a timeout occurs.
     *
     * @param stunHost The STUN server hostname.
     * @param stunPort The STUN server port.
     * @param localIP  The local IP address of the interface to use.
     * @return The result of the STUN query.
     */
    public static StunResult query(String stunHost, int stunPort, String localIP) {
        if (stunHost == null || localIP == null) {
            throw new InvalidParameterException("Host and localIP cannot be null");
        }
        ForwarderIOHandler ioHandler;
        if (IoUring.isAvailable()) { // 性能最好
            ioHandler = new IOUringHandler();
        } else if (Epoll.isAvailable()) { // 性能很不错！
            ioHandler = new EpollHandler();
        } else if (KQueue.isAvailable()) { // FreeBSD/MacOS
            ioHandler = new KQueueHandler();
        } else { // oh shit
            ioHandler = new NioHandler();
        }
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(ioHandler.ioHandlerFactory());
        try {
            // A CompletableFuture to hold the final result of our multi-step STUN test
            CompletableFuture<StunResult> finalResultFuture = new CompletableFuture<>();

            StunClientHandler handler = new StunClientHandler(TRANSACTION_TIMEOUT_MS);
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(handler);

            Channel channel = b.bind(0).sync().channel();

            // Execute the logic using CompletableFuture chains for async operations
            executeStunLogic(handler, channel, stunHost, stunPort, localIP, finalResultFuture);

            // Block and wait for the final result, with an overall timeout
            return finalResultFuture.get(TOTAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.debug("STUN query failed or timed out.", e);
            return new StunResult(NatType.Unknown, null);
        } finally {
            group.shutdownGracefully();
        }
    }

    /**
     * Executes the STUN test sequence asynchronously.
     */
    private static void executeStunLogic(StunClientHandler handler, Channel channel, String stunHost, int stunPort, String localIP, CompletableFuture<StunResult> finalResultFuture) {
        InetSocketAddress remoteAddress = new InetSocketAddress(stunHost, stunPort);

        // Test I
        StunMessage test1Request = new StunMessage(StunMessageType.BindingRequest);
        handler.doTransaction(test1Request, remoteAddress, channel)
                .whenComplete((test1Response, ex) -> {
                    if (ex != null) {
                        // UDP Blocked if Test I fails
                        finalResultFuture.complete(new StunResult(NatType.UdpBlocked, null));
                        return;
                    }

                    InetSocketAddress mappedAddress = test1Response.getMappedAddress();
                    boolean isNat = !Arrays.equals(Utils.ipToBytes(localIP), mappedAddress.getAddress().getAddress());

                    if (!isNat) {
                        // Potentially Open Internet or Symmetric UDP Firewall
                        handleNoNatPath(handler, test1Response, channel, remoteAddress, finalResultFuture);
                    } else {
                        // Behind a NAT
                        handleNatPath(handler, test1Response, channel, remoteAddress, finalResultFuture);
                    }
                });
    }

    private static void handleNoNatPath(StunClientHandler handler, StunMessage test1Response, Channel channel, InetSocketAddress remoteAddress, CompletableFuture<StunResult> finalResultFuture) {
        // Test II: Send request with change IP and change port flags
        StunMessage test2Request = new StunMessage(StunMessageType.BindingRequest, new StunChangeRequest(true, true));
        handler.doTransaction(test2Request, remoteAddress, channel)
                .whenComplete((test2Response, ex) -> {
                    if (ex == null && test2Response != null) {
                        finalResultFuture.complete(new StunResult(NatType.OpenInternet, test1Response.getMappedAddress()));
                    } else {
                        finalResultFuture.complete(new StunResult(NatType.SymmetricUdpFirewall, test1Response.getMappedAddress()));
                    }
                });
    }

    private static void handleNatPath(StunClientHandler handler, StunMessage test1Response, Channel channel, InetSocketAddress remoteAddress, CompletableFuture<StunResult> finalResultFuture) {
        // Test II
        StunMessage test2Request = new StunMessage(StunMessageType.BindingRequest, new StunChangeRequest(true, true));
        handler.doTransaction(test2Request, remoteAddress, channel)
                .whenComplete((test2Response, ex) -> {
                    if (ex == null && test2Response != null) {
                        // Full Cone NAT if response received
                        finalResultFuture.complete(new StunResult(NatType.FullCone, test1Response.getMappedAddress()));
                    } else {
                        // Could be Symmetric, Restricted, or Port Restricted
                        handleRestrictedNatPath(handler, test1Response, channel, finalResultFuture);
                    }
                });
    }

    private static void handleRestrictedNatPath(StunClientHandler handler, StunMessage test1Response, Channel channel, CompletableFuture<StunResult> finalResultFuture) {
        // Test I(II): Send to the CHANGED-ADDRESS from the first response
        StunMessage test12Request = new StunMessage(StunMessageType.BindingRequest);
        InetSocketAddress changedAddress = test1Response.getChangedAddress();

        handler.doTransaction(test12Request, changedAddress, channel)
                .whenComplete((test12Response, ex) -> {
                    if (ex != null) {
                        log.debug("STUN Test I(II) failed to get a response.");
                        finalResultFuture.complete(new StunResult(NatType.Unknown, test1Response.getMappedAddress()));
                        return;
                    }

                    boolean mappedAddressIsSame = Arrays.equals(
                            test1Response.getMappedAddress().getAddress().getAddress(),
                            test12Response.getMappedAddress().getAddress().getAddress())
                            && test1Response.getMappedAddress().getPort() == test12Response.getMappedAddress().getPort();

                    if (!mappedAddressIsSame) {
                        finalResultFuture.complete(new StunResult(NatType.Symmetric, test1Response.getMappedAddress()));
                    } else {
                        // Test III: Send to CHANGED-ADDRESS with change port flag only
                        StunMessage test3Request = new StunMessage(StunMessageType.BindingRequest, new StunChangeRequest(false, true));
                        handler.doTransaction(test3Request, changedAddress, channel)
                                .whenComplete((test3Response, ex3) -> {
                                    if (ex3 == null && test3Response != null) {
                                        finalResultFuture.complete(new StunResult(NatType.RestrictedCone, test1Response.getMappedAddress()));
                                    } else {
                                        finalResultFuture.complete(new StunResult(NatType.PortRestrictedCone, test1Response.getMappedAddress()));
                                    }
                                });
                    }
                });
    }
}