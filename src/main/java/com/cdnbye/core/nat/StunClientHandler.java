package com.cdnbye.core.nat;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class StunClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final int UDP_SEND_COUNT = 3;

    private final Map<ByteArrayWrapper, PendingTransaction> pendingTransactions = new ConcurrentHashMap<>();
    private final int transactionTimeoutMs;

    public StunClientHandler(int transactionTimeoutMs) {
        this.transactionTimeoutMs = transactionTimeoutMs;
    }

    /**
     * Executes a single STUN transaction asynchronously.
     *
     * @param request        The STUN message to send.
     * @param remoteAddress  The destination address.
     * @param channel        The channel to send the message on.
     * @return A CompletableFuture that will be completed with the response message or an exception.
     */
    public CompletableFuture<StunMessage> doTransaction(StunMessage request, InetSocketAddress remoteAddress, Channel channel) {
        CompletableFuture<StunMessage> future = new CompletableFuture<>();
        ByteArrayWrapper transactionId = new ByteArrayWrapper(request.getTransactionId());
        PendingTransaction pending = new PendingTransaction(request, remoteAddress, future, channel);
        pendingTransactions.put(transactionId, pending);

        sendAndScheduleTimeout(pending, 0);

        return future;
    }

    private void sendAndScheduleTimeout(PendingTransaction transaction, int attempt) {
        if (attempt >= UDP_SEND_COUNT) {
            // All retries failed
            ByteArrayWrapper transactionId = new ByteArrayWrapper(transaction.request.getTransactionId());
            pendingTransactions.remove(transactionId);
            transaction.future.completeExceptionally(new SocketTimeoutException("STUN transaction timed out after " + UDP_SEND_COUNT + " attempts."));
            return;
        }

        log.debug("Sending STUN request (attempt {}/{}) to {}", attempt + 1, UDP_SEND_COUNT, transaction.remoteAddress);
        byte[] requestBytes = transaction.request.toByteData();
        DatagramPacket packet = new DatagramPacket(io.netty.buffer.Unpooled.copiedBuffer(requestBytes), transaction.remoteAddress);
        transaction.channel.writeAndFlush(packet);

        // Schedule a timeout check for this attempt
        ScheduledFuture<?> timeoutFuture = transaction.channel.eventLoop().schedule(() -> {
            sendAndScheduleTimeout(transaction, attempt + 1);
        }, transactionTimeoutMs, TimeUnit.MILLISECONDS);

        // Associate the scheduled timeout so we can cancel it if a response arrives
        transaction.setTimeoutFuture(timeoutFuture);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        byte[] bytes = new byte[packet.content().readableBytes()];
        packet.content().readBytes(bytes);

        StunMessage response = new StunMessage();
        response.parse(bytes);

        ByteArrayWrapper transactionId = new ByteArrayWrapper(response.getTransactionId());
        PendingTransaction pending = pendingTransactions.remove(transactionId);

        if (pending != null) {
            log.debug("Received STUN response for transaction ID: {}", transactionId);
            // Cancel the scheduled timeout/retry task
            if (pending.timeoutFuture != null) {
                pending.timeoutFuture.cancel(false);
            }
            // Complete the future successfully
            pending.future.complete(response);
        } else {
            log.warn("Received STUN response with unknown transaction ID: {}", transactionId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in StunClientHandler", cause);
        ctx.close();
    }

    /**
     * Helper record to store state for a pending transaction.
     */
    private static class PendingTransaction {
        final StunMessage request;
        final InetSocketAddress remoteAddress;
        final CompletableFuture<StunMessage> future;
        final Channel channel;
        ScheduledFuture<?> timeoutFuture;

        PendingTransaction(StunMessage request, InetSocketAddress remoteAddress, CompletableFuture<StunMessage> future, Channel channel) {
            this.request = request;
            this.remoteAddress = remoteAddress;
            this.future = future;
            this.channel = channel;
        }

        void setTimeoutFuture(ScheduledFuture<?> timeoutFuture) {
            this.timeoutFuture = timeoutFuture;
        }
    }
}