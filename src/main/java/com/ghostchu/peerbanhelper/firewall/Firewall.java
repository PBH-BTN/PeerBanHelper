package com.ghostchu.peerbanhelper.firewall;

import inet.ipaddr.IPAddress;

public interface Firewall {
    String getName();

    boolean isApplicable();

    boolean ban(IPAddress address) throws Exception;

    boolean unban(IPAddress address) throws Exception;

    boolean reset() throws Exception;

    boolean load() throws Exception;

    boolean unload() throws Exception;
}
