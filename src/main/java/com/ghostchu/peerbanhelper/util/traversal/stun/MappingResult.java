package com.ghostchu.peerbanhelper.util.traversal.stun;

import java.net.InetSocketAddress;

public record MappingResult(InetSocketAddress interAddress, InetSocketAddress outerAddress) {
}
