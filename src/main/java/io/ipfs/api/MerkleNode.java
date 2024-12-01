package io.ipfs.api;

import io.ipfs.cid.*;
import io.ipfs.multihash.Multihash;

import java.util.*;
import java.util.stream.*;

public class MerkleNode {

    public final Multihash hash;
    public final Optional<String> name;
    public final Optional<Integer> size;
    public final Optional<String> largeSize;
    public final Optional<Integer> type;
    public final List<MerkleNode> links;
    public final Optional<byte[]> data;

    public MerkleNode(String hash,
                      Optional<String> name,
                      Optional<Integer> size,
                      Optional<String> largeSize,
                      Optional<Integer> type,
                      List<MerkleNode> links,
                      Optional<byte[]> data) {
        this.name = name;
        this.hash = Cid.decode(hash);
        this.size = size;
        this.largeSize = largeSize;
        this.type = type;
        this.links = links;
        this.data = data;
    }

    public MerkleNode(String hash) {
        this(hash, Optional.empty());
    }

    public MerkleNode(String hash, Optional<String> name) {
        this(hash, name, Optional.empty(), Optional.empty(), Optional.empty(), Arrays.asList(), Optional.empty());
    }

    @Override
    public boolean equals(Object b) {
        if (!(b instanceof MerkleNode))
            return false;
        MerkleNode other = (MerkleNode) b;
        return hash.equals(other.hash); // ignore name hash says it all
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    public static MerkleNode fromJSON(Object rawjson) {
        if (rawjson instanceof String)
            return new MerkleNode((String)rawjson);
        Map json = (Map)rawjson;
        if ("error".equals(json.get("Type")))
            throw new IllegalStateException("Remote IPFS error: " + json.get("Message"));
        String hash = (String)json.get("Hash");
        if (hash == null)
            hash = (String)json.get("Key");
        if (hash == null && json.containsKey("Cid"))
            hash = (String) (((Map) json.get("Cid")).get("/"));
        Optional<String> name = json.containsKey("Name") ?
                Optional.of((String) json.get("Name")) :
                Optional.empty();
        Object rawSize = json.get("Size");
        Optional<Integer> size = rawSize instanceof Integer ?
                Optional.of((Integer) rawSize) :
                Optional.empty();
        Optional<String> largeSize = rawSize instanceof String ?
                Optional.of((String) json.get("Size")) :
                Optional.empty();
        Optional<Integer> type = json.containsKey("Type") ?
                Optional.of((Integer) json.get("Type")) :
                Optional.empty();
        List<Object> linksRaw = (List<Object>) json.get("Links");
        List<MerkleNode> links = linksRaw == null ?
                Collections.emptyList() :
                linksRaw.stream().map(x -> MerkleNode.fromJSON(x)).collect(Collectors.toList());
        Optional<byte[]> data = json.containsKey("Data") ? Optional.of(((String)json.get("Data")).getBytes()): Optional.empty();
        return new MerkleNode(hash, name, size, largeSize, type, links, data);
    }

    public Object toJSON() {
        Map<String, Object> res = new TreeMap<>();
        res.put("Links", links.stream().map(x -> x.hash).collect(Collectors.toList()));
        data.ifPresent(bytes -> res.put("Data", bytes));
        return res;
    }

    public String toJSONString() {
        return JSONParser.toString(toJSON());
    }

    @Override
    public String toString() {
        return hash + "-" + name.orElse("");
    }
}
