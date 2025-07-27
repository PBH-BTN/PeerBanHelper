package com.ghostchu.peerbanhelper.util.traversal;

import java.net.InetSocketAddress;

public record MappingResult(InetSocketAddress interAddress, InetSocketAddress outerAddress) {
}
