package com.ghostchu.peerbanhelper.util.dns;

import com.ghostchu.peerbanhelper.util.dns.impl.PTRLookup;
import okhttp3.Dns;

public interface DNSLookup {
    Dns getDns(String route);
    PTRLookup getPTRLookup(String route);
}
