package io.ipfs.api;

import io.ipfs.api.cbor.*;
import io.ipfs.cid.*;
import io.ipfs.multihash.*;

import java.security.*;
import java.util.*;
import java.util.stream.*;

public interface IpldNode extends Cborable {

    Pair<IpldNode, List<String>> resolve(List<String> path);

    /** Lists all paths within the object under 'path', and up to the given depth.
     *  To list the entire object (similar to `find .`) pass "" and -1
     * @param path
     * @param depth
     * @return
     */
    List<String> tree(String path, int depth);

    /**
     *
     * @return calculate this objects Cid
     */
    default Cid cid() {
        byte[] raw = rawData();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(raw);
            byte[] digest = md.digest();
            return new Cid(1, Cid.Codec.DagCbor, Multihash.Type.sha2_256, digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     *
     * @return size of this object when encoded
     */
    long size();

    /**
     *
     * @return this object's serialization
     */
    byte[] rawData();

    /**
     *
     * @return the merkle links from this object
     */
    List<Link> getLinks();

    static IpldNode fromCBOR(CborObject cbor) {
        return new CborIpldNode(cbor);
    }

    static IpldNode fromJSON(Object json) {
        return new JsonIpldNode(json);
    }

    class CborIpldNode implements IpldNode {
        private final CborObject base;

        public CborIpldNode(CborObject base) {
            this.base = base;
        }

        @Override
        public CborObject toCbor() {
            return base;
        }

        @Override
        public Pair<IpldNode, List<String>> resolve(List<String> path) {
            throw new IllegalStateException("Unimplemented!");
        }

        @Override
        public List<String> tree(String path, int depth) {
            return tree(base, path, depth);
        }

        private List<String> tree(CborObject base, String rawPath, int depth) {
            String path = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;

            if (depth == 0 || (path.equals("") && depth != -1))
                return Collections.singletonList("");

            if (base instanceof CborObject.CborMap) {
                return ((CborObject.CborMap) base).values.entrySet()
                        .stream()
                        .flatMap(e -> {
                            String name = ((CborObject.CborString) e.getKey()).value;
                            if (path.startsWith(name) || depth == -1)
                                return tree(e.getValue(), path.length() > 0 ? path.substring(name.length()) : path,
                                        depth == -1 ? -1 : depth - 1)
                                        .stream()
                                        .map(p -> "/" + name + p);
                            return Stream.empty();
                        }).collect(Collectors.toList());
            }
            if (depth == -1)
                return Collections.singletonList("");
            return Collections.emptyList();
        }

        @Override
        public long size() {
            return rawData().length;
        }

        @Override
        public byte[] rawData() {
            return base.toByteArray();
        }

        @Override
        public List<Link> getLinks() {
            return getLinks(base);
        }

        private static List<Link> getLinks(CborObject base) {
            if (base instanceof CborObject.CborMerkleLink)
                return Collections.singletonList(new Link("", 0, ((CborObject.CborMerkleLink) base).target));
            if (base instanceof CborObject.CborMap) {
                return ((CborObject.CborMap) base).values.values()
                        .stream()
                        .flatMap(cbor -> getLinks(cbor).stream())
                        .collect(Collectors.toList());
            }
            if (base instanceof CborObject.CborList) {
                return ((CborObject.CborList) base).value
                        .stream()
                        .flatMap(cbor -> getLinks(cbor).stream())
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
    }

    class JsonIpldNode implements IpldNode {
        private final Object json;

        public JsonIpldNode(Object json) {
            this.json = json;
        }

        @Override
        public CborObject toCbor() {
            throw new IllegalStateException("Unimplemented!");
        }

        @Override
        public Pair<IpldNode, List<String>> resolve(List<String> path) {
            throw new IllegalStateException("Unimplemented!");
        }

        @Override
        public List<String> tree(String path, int depth) {
            throw new IllegalStateException("Unimplemented!");
        }

        @Override
        public long size() {
            return rawData().length;
        }

        @Override
        public byte[] rawData() {
            return JSONParser.toString(json).getBytes();
        }

        @Override
        public List<Link> getLinks() {
            throw new IllegalStateException("Unimplemented!");
        }
    }

    class Link {
        public final String name;
        // Cumulative size of target
        public final long size;
        public final Multihash target;

        public Link(String name, long size, Multihash target) {
            this.name = name;
            this.size = size;
            this.target = target;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Link link = (Link) o;

            if (size != link.size) return false;
            if (name != null ? !name.equals(link.name) : link.name != null) return false;
            return target != null ? target.equals(link.target) : link.target == null;

        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (int) (size ^ (size >>> 32));
            result = 31 * result + (target != null ? target.hashCode() : 0);
            return result;
        }
    }
}
