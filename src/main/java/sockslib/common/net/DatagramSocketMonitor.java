package sockslib.common.net;

import java.net.DatagramPacket;

/**
 * @author Youchao Feng
 * @version 1.0
 * @date Sep 23, 2015 11:37 AM
 */
public interface DatagramSocketMonitor {

    /**
     * This method will be called after {@link java.net.DatagramSocket#send(DatagramPacket)} is
     * called.
     *
     * @param datagramPacket Sent packet.
     */
    void onSend(final DatagramPacket datagramPacket);

    /**
     * This method will be called after {@link java.net.DatagramSocket#receive(DatagramPacket)} is
     * called.
     *
     * @param datagramPacket Received packet.
     */
    void onReceive(final DatagramPacket datagramPacket);
}
