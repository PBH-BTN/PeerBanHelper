package io.ipfs.api;

import io.ipfs.cid.*;
import io.ipfs.multiaddr.*;
import io.ipfs.multihash.*;

import java.util.*;
import java.util.function.*;

public class Peer {
    public final MultiAddress address;
    public final Multihash id;
    public final long latency;
    public final String muxer;
    public final Object streams;

    public Peer(MultiAddress address, Multihash id, long latency, String muxer, Object streams) {
        this.address = address;
        this.id = id;
        this.latency = latency;
        this.muxer = muxer;
        this.streams = streams;
    }

    public static Peer fromJSON(Object json) {
        if (! (json instanceof Map))
            throw new IllegalStateException("Incorrect json for Peer: " + JSONParser.toString(json));
        Map m = (Map) json;
        Function<String, String> val = key -> (String) m.get(key);
        long latency = val.apply("Latency").length() > 0 ? Long.parseLong(val.apply("Latency")) : -1;
        return new Peer(new MultiAddress(val.apply("Addr")), Cid.decode(val.apply("Peer")), latency, val.apply("Muxer"), val.apply("Streams"));
    }

    @Override
    public String toString() {
        return id + "@" + address;
    }
}
