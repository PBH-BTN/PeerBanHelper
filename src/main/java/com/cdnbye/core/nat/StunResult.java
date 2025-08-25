package com.cdnbye.core.nat;

import java.net.InetSocketAddress;

public record StunResult(NatType natType, InetSocketAddress ipAddr) {


}
