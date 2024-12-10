package io.ipfs.api;

import io.ipfs.cid.*;
import io.ipfs.multibase.*;
import io.ipfs.multihash.Multihash;
import io.ipfs.multiaddr.MultiAddress;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;

public class IPFS {

    public static final Version MIN_VERSION = Version.parse("0.4.11");
    public enum PinType {all, direct, indirect, recursive}
    public enum PinStatus {queued, pinning, pinned, failed}
    public List<String> ObjectTemplates = Arrays.asList("unixfs-dir");
    public List<String> ObjectPatchTypes = Arrays.asList("add-link", "rm-link", "set-data", "append-data");
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 60_000;

    public final String host;
    public final int port;
    public final String protocol;
    private final String apiVersion;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    public final Key key = new Key();
    public final Log log = new Log();
    public final MultibaseAPI multibase = new MultibaseAPI();
    public final Pin pin = new Pin();
    public final Repo repo = new Repo();
    public final IPFSObject object = new IPFSObject();
    public final Swarm swarm = new Swarm();
    public final Bootstrap bootstrap = new Bootstrap();
    public final Bitswap bitswap = new Bitswap();
    public final Block block = new Block();
    public final CidAPI cid = new CidAPI();
    public final Dag dag = new Dag();
    public final Diag diag = new Diag();
    public final Config config = new Config();
    public final Refs refs = new Refs();
    public final Update update = new Update();
    public final DHT dht = new DHT();
    public final File file = new File();
    public final Files files = new Files();
    public final FileStore fileStore = new FileStore();
    public final Stats stats = new Stats();
    public final Name name = new Name();
    public final Pubsub pubsub = new Pubsub();
    public final VersionAPI version = new VersionAPI();

    public IPFS(String host, int port) {
        this(host, port, "/api/v0/", false);
    }

    public IPFS(String multiaddr) {
        this(new MultiAddress(multiaddr));
    }

    public IPFS(MultiAddress addr) {
        this(addr.getHost(), addr.getPort(), "/api/v0/", detectSSL(addr));
    }

    public IPFS(String host, int port, String version, boolean ssl) {
        this(host, port, version, true, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, ssl);
    }

    public IPFS(String host, int port, String version, boolean enforceMinVersion, boolean ssl) {
        this(host, port, version, enforceMinVersion, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, ssl);
    }

    public IPFS(String host, int port, String version, int connectTimeoutMillis, int readTimeoutMillis, boolean ssl) {
        this(host, port, version, true, connectTimeoutMillis, readTimeoutMillis, ssl);
    }

    public IPFS(String host, int port, String version, boolean enforceMinVersion, int connectTimeoutMillis, int readTimeoutMillis, boolean ssl) {
        if (connectTimeoutMillis < 0) throw new IllegalArgumentException("connect timeout must be zero or positive");
        if (readTimeoutMillis < 0) throw new IllegalArgumentException("read timeout must be zero or positive");
        this.host = host;
        this.port = port;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;

        if (ssl) {
            this.protocol = "https";
        } else {
            this.protocol = "http";
        }

        this.apiVersion = version;
        // Check IPFS is sufficiently recent
        if (enforceMinVersion) {
            try {
                Version detected = Version.parse(version());
                if (detected.isBefore(MIN_VERSION))
                    throw new IllegalStateException("You need to use a more recent version of IPFS! >= " + MIN_VERSION);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Configure a HTTP client timeout
     * @param timeout (default 0: infinite timeout)
     * @return current IPFS object with configured timeout
     */
    public IPFS timeout(int timeout) {
        return new IPFS(host, port, apiVersion, timeout, timeout, protocol.equals("https"));
    }

    public String shutdown() throws IOException {
        return retrieveString("shutdown");
    }

    public List<MerkleNode> add(NamedStreamable file) throws IOException {
        return add(file, false);
    }

    public List<MerkleNode> add(NamedStreamable file, boolean wrap) throws IOException {
        return add(file, wrap, false);
    }

    public List<MerkleNode> add(NamedStreamable file, boolean wrap, boolean hashOnly) throws IOException {
        return add(Collections.singletonList(file), wrap, hashOnly);
    }

    public List<MerkleNode> add(List<NamedStreamable> files, boolean wrap, boolean hashOnly) throws IOException {
        Multipart m = new Multipart(protocol + "://" + host + ":" + port + apiVersion + "add?stream-channels=true&w="+wrap + "&n="+hashOnly, "UTF-8");
        for (NamedStreamable file: files) {
            if (file.isDirectory()) {
                m.addSubtree(Paths.get(""), file);
            } else
                m.addFilePart("file", Paths.get(""), file);
        }
        String res = m.finish();
        return JSONParser.parseStream(res).stream()
                .map(x -> MerkleNode.fromJSON((Map<String, Object>) x))
                .collect(Collectors.toList());
    }

    public List<MerkleNode> add(NamedStreamable file, AddArgs args) throws IOException {
        return add(Collections.singletonList(file), args);
    }

    public List<MerkleNode> add(List<NamedStreamable> files, AddArgs args) throws IOException {
        Multipart m = new Multipart(protocol + "://" + host + ":" + port + apiVersion + "add?stream-channels=true&"+ args.toQueryString(), "UTF-8");
        for (NamedStreamable file: files) {
            if (file.isDirectory()) {
                m.addSubtree(Paths.get(""), file);
            } else
                m.addFilePart("file", Paths.get(""), file);
        }
        String res = m.finish();
        return JSONParser.parseStream(res).stream()
                .map(x -> MerkleNode.fromJSON((Map<String, Object>) x))
                .collect(Collectors.toList());
    }

    public List<MerkleNode> ls(Multihash hash) throws IOException {
        Map reply = retrieveMap("ls?arg=" + hash);
        return ((List<Object>) reply.get("Objects"))
                .stream()
                .flatMap(x -> ((List<Object>)((Map) x).get("Links"))
                        .stream()
                        .map(MerkleNode::fromJSON))
                .collect(Collectors.toList());
    }

    public byte[] cat(Multihash hash) throws IOException {
        return retrieve("cat?arg=" + hash);
    }

    public byte[] cat(Multihash hash, String subPath) throws IOException {
        return retrieve("cat?arg=" + hash + URLEncoder.encode(subPath, "UTF-8"));
    }

    public byte[] get(Multihash hash) throws IOException {
        return retrieve("get?arg=" + hash);
    }

    public InputStream catStream(Multihash hash) throws IOException {
        return retrieveStream("cat?arg=" + hash);
    }

    public List<Multihash> refs(Multihash hash, boolean recursive) throws IOException {
        String jsonStream = new String(retrieve("refs?arg=" + hash + "&r=" + recursive));
        return JSONParser.parseStream(jsonStream).stream()
                .map(m -> (String) (((Map) m).get("Ref")))
                .map(Cid::decode)
                .collect(Collectors.toList());
    }

    public Map resolve(String scheme, Multihash hash, boolean recursive) throws IOException {
        return retrieveMap("resolve?arg=/" + scheme+"/"+hash +"&r="+recursive);
    }

    @Deprecated
    public String dns(String domain, boolean recursive) throws IOException {
        Map res = retrieveMap("dns?arg=" + domain + "&r=" + recursive);
        return (String)res.get("Path");
    }

    public Map mount(java.io.File ipfsRoot, java.io.File ipnsRoot) throws IOException {
        if (ipfsRoot != null && !ipfsRoot.exists())
            ipfsRoot.mkdirs();
        if (ipnsRoot != null && !ipnsRoot.exists())
            ipnsRoot.mkdirs();
        return (Map)retrieveAndParse("mount?arg=" + (ipfsRoot != null ? ipfsRoot.getPath() : "/ipfs" ) + "&arg=" +
                (ipnsRoot != null ? ipnsRoot.getPath() : "/ipns" ));
    }

    // level 2 commands
    public class Refs {
        public List<Multihash> local() throws IOException {
            String jsonStream = new String(retrieve("refs/local"));
            return JSONParser.parseStream(jsonStream).stream()
                    .map(m -> (String) (((Map) m).get("Ref")))
                    .map(Cid::decode)
                    .collect(Collectors.toList());
        }
    }

    /* Pinning an object ensures a local copy of it is kept.
     */
    public class Pin {
        public final Remote remote = new Remote();

        public class Remote {
            public Map add(String service, Multihash hash, Optional<String> name, boolean background) throws IOException {
                String nameArg = name.isPresent() ? "&name=" + name.get() : "";
                return retrieveMap("pin/remote/add?arg=" + hash + "&service=" + service + nameArg + "&background=" + background);
            }
            public Map ls(String service, Optional<String> name, Optional<List<PinStatus>> statusList) throws IOException {
                String nameArg = name.isPresent() ? "&name=" + name.get() : "";
                String statusArg = statusList.isPresent() ? statusList.get().stream().
                        map(p -> "&status=" + p).collect(Collectors.joining()) : "";
                return retrieveMap("pin/remote/ls?service=" + service + nameArg + statusArg);
            }
            public String rm(String service, Optional<String> name, Optional<List<PinStatus>> statusList, Optional<List<Multihash>> cidList) throws IOException {
                String nameArg = name.isPresent() ? "&name=" + name.get() : "";
                String statusArg = statusList.isPresent() ? statusList.get().stream().
                        map(p -> "&status=" + p).collect(Collectors.joining()) : "";
                String cidArg = cidList.isPresent() ? cidList.get().stream().
                        map(p -> "&cid=" + p.toBase58()).collect(Collectors.joining()) : "";
                return retrieveString("pin/remote/rm?service=" + service + nameArg + statusArg + cidArg);
            }
            public String addService(String service, String endPoint, String key) throws IOException {
                return retrieveString("pin/remote/service/add?arg=" + service + "&arg=" + endPoint + "&arg=" + key);
            }

            public List<Map> lsService(boolean stat) throws IOException {
                return (List<Map>) retrieveMap("pin/remote/service/ls?stat=" + stat).get("RemoteServices");
            }

            public String rmService(String service) throws IOException {
                return retrieveString("pin/remote/service/rm?arg=" + service);
            }
        }
        public List<Multihash> add(Multihash hash) throws IOException {
            return ((List<Object>)((Map)retrieveAndParse("pin/add?stream-channels=true&arg=" + hash)).get("Pins"))
                    .stream()
                    .map(x -> Cid.decode((String) x))
                    .collect(Collectors.toList());
        }

        public Map<Multihash, Object> ls() throws IOException {
            return ls(PinType.direct);
        }

        public Map<Multihash, Object> ls(PinType type) throws IOException {
            return ((Map<String, Object>)(((Map)retrieveAndParse("pin/ls?stream-channels=true&t="+type.name())).get("Keys"))).entrySet()
                    .stream()
                    .collect(Collectors.toMap(x -> Cid.decode(x.getKey()), x-> x.getValue()));
        }

        public List<Multihash> rm(Multihash hash) throws IOException {
            return rm(hash, true);
        }

        public List<Multihash> rm(Multihash hash, boolean recursive) throws IOException {
            Map json = retrieveMap("pin/rm?stream-channels=true&r=" + recursive + "&arg=" + hash);
            return ((List<Object>) json.get("Pins")).stream().map(x -> Cid.decode((String) x)).collect(Collectors.toList());
        }

        public List<Multihash> update(Multihash existing, Multihash modified, boolean unpin) throws IOException {
            return ((List<Object>)((Map)retrieveAndParse("pin/update?stream-channels=true&arg=" + existing + "&arg=" + modified + "&unpin=" + unpin)).get("Pins"))
                    .stream()
                    .map(x -> Cid.decode((String) x))
                    .collect(Collectors.toList());
        }
        public Map verify(boolean verbose, boolean quite) throws IOException {
            return retrieveMap("pin/verify?verbose=" + verbose + "&quite=" + quite);
        }
    }

    /* 'ipfs key' is a command for dealing with IPNS keys.
     */
    public class Key {
        public KeyInfo gen(String name, Optional<String> type, Optional<String> size) throws IOException {
            return KeyInfo.fromJson(retrieveAndParse("key/gen?arg=" + name + type.map(t -> "&type=" + t).orElse("") + size.map(s -> "&size=" + s).orElse("")));
        }

        public List<KeyInfo> list() throws IOException {
            return ((List<Object>)((Map)retrieveAndParse("key/list")).get("Keys"))
                    .stream()
                    .map(KeyInfo::fromJson)
                    .collect(Collectors.toList());
        }

        public Object rename(String name, String newName) throws IOException {
            return retrieveAndParse("key/rename?arg="+name + "&arg=" + newName);
        }

        public List<KeyInfo> rm(String name) throws IOException {
            return ((List<Object>)((Map)retrieveAndParse("key/rm?arg=" + name)).get("Keys"))
                    .stream()
                    .map(KeyInfo::fromJson)
                    .collect(Collectors.toList());
        }
    }

    public class Log {
        public Map level(String subsystem, String logLevel) throws IOException {
            return retrieveMap("log/level?arg=" + subsystem + "&arg=" + logLevel);
        }
        public Map ls() throws IOException {
            return retrieveMap("log/ls");
        }
    }

    public class MultibaseAPI {
        public String decode(NamedStreamable encoded_file) {
            Multipart m = new Multipart(protocol + "://" + host + ":" + port + apiVersion +
                    "multibase/decode", "UTF-8");
            try {
                if (encoded_file.isDirectory()) {
                    throw new IllegalArgumentException("encoded_file must be a file");
                } else {
                    m.addFilePart("file", Paths.get(""), encoded_file);
                    return m.finish();
                }
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        public String encode(Optional<String> encoding, NamedStreamable file) {
            String b = encoding.map(f -> "?b=" + f).orElse("?b=base64url");
            Multipart m = new Multipart(protocol + "://" + host + ":" + port + apiVersion +
                    "multibase/encode" + b, "UTF-8");
            try {
                if (file.isDirectory()) {
                    throw new IllegalArgumentException("Input must be a file");
                } else {
                    m.addFilePart("file", Paths.get(""), file);
                    return m.finish();
                }
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        public List<Map> list(boolean prefix, boolean  numeric) throws IOException {
            return (List)retrieveAndParse("multibase/list?prefix=" + prefix + "&numeric=" + numeric);
        }
        public String transcode(Optional<String> encoding, NamedStreamable file) {
            String b = encoding.map(f -> "?b=" + f).orElse("?b=base64url");
            Multipart m = new Multipart(protocol + "://" + host + ":" + port + apiVersion +
                    "multibase/transcode" + b, "UTF-8");
            try {
                if (file.isDirectory()) {
                    throw new IllegalArgumentException("Input must be a file");
                } else {
                    m.addFilePart("file", Paths.get(""), file);
                    return m.finish();
                }
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    /* 'ipfs repo' is a plumbing command used to manipulate the repo.
     */
    public class Repo {
        public Map gc() throws IOException {
            return retrieveMap("repo/gc");
        }
        public Multihash ls() throws IOException {
            Map res = retrieveMap("repo/ls");
            return Cid.decode((String)res.get("Ref"));
        }
        /*public String migrate(boolean allowDowngrade) throws IOException {
            return retrieveString("repo/migrate?allow-downgrade=" + allowDowngrade);
        }*/
        public RepoStat stat(boolean sizeOnly) throws IOException {
            return RepoStat.fromJson(retrieveAndParse("repo/stat?size-only=" + sizeOnly));
        }
        public Map verify() throws IOException {
            return retrieveMap("repo/verify");
        }
        public Map version() throws IOException {
            return retrieveMap("repo/version");
        }
    }


    public class VersionAPI {
        public Map versionDeps() throws IOException {
            return retrieveMap("version/deps");
        }
    }

    public class Pubsub {
        public Object ls() throws IOException {
            return retrieveAndParse("pubsub/ls");
        }

        public Object peers() throws IOException {
            return retrieveAndParse("pubsub/peers");
        }

        public Object peers(String topic) throws IOException {
            return retrieveAndParse("pubsub/peers?arg="+topic);
        }

        /**
         *
         * @param topic topic to publish to
         * @param data url encoded data to be published
         */
        public void pub(String topic, String data) {
            String encodedTopic = Multibase.encode(Multibase.Base.Base64Url, topic.getBytes());
            Multipart m = new Multipart(protocol +"://" + host + ":" + port + apiVersion+"pubsub/pub?arg=" + encodedTopic, "UTF-8");
            try {
                m.addFilePart("file", Paths.get(""), new NamedStreamable.ByteArrayWrapper(data.getBytes()));
                String res = m.finish();
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        public Stream<Map<String, Object>> sub(String topic) throws Exception {
            return sub(topic, ForkJoinPool.commonPool());
        }

        public Stream<Map<String, Object>> sub(String topic, ForkJoinPool threadSupplier) throws Exception {
            String encodedTopic = Multibase.encode(Multibase.Base.Base64Url, topic.getBytes());
            return retrieveAndParseStream("pubsub/sub?arg=" + encodedTopic, threadSupplier).map(obj -> (Map)obj);
        }

        /**
         * A synchronous method to subscribe which consumes the calling thread
         * @param topic
         * @param results
         * @throws IOException
         */
        public void sub(String topic, Consumer<Map<String, Object>> results, Consumer<IOException> error) throws IOException {
            String encodedTopic = Multibase.encode(Multibase.Base.Base64Url, topic.getBytes());
            retrieveAndParseStream("pubsub/sub?arg="+encodedTopic, res -> results.accept((Map)res), error);
        }
    }

    public class CidAPI {
        public Map base32(Cid hash) throws IOException {
            return (Map)retrieveAndParse("cid/base32?arg=" + hash);
        }

        public List<Map> bases(boolean prefix, boolean  numeric) throws IOException {
            return (List)retrieveAndParse("cid/bases?prefix=" + prefix + "&numeric=" + numeric);
        }

        public List<Map> codecs(boolean numeric, boolean  supported) throws IOException {
            return (List)retrieveAndParse("cid/codecs?numeric=" + numeric + "&supported=" + supported);
        }

        public Map format(Cid hash, Optional<String> f, Optional<String> v, Optional<String> mc, Optional<String> b) throws IOException {
            String fArg = f.isPresent() ? "&f=" + URLEncoder.encode(f.get(), "UTF-8") : "";
            String vArg = v.isPresent() ? "&v=" + v.get() : "";
            String mcArg = mc.isPresent() ? "&mc=" + mc.get() : "";
            String bArg = b.isPresent() ? "&b=" + b.get() : "";
            return (Map)retrieveAndParse("cid/format?arg=" + hash + fArg + vArg + mcArg + bArg);
        }

        public List<Map> hashes(boolean numeric, boolean  supported) throws IOException {
            return (List)retrieveAndParse("cid/hashes?numeric=" + numeric + "&supported=" + supported);
        }

    }
    /* 'ipfs block' is a plumbing command used to manipulate raw ipfs blocks.
     */
    public class Block {
        public byte[] get(Multihash hash) throws IOException {
            return retrieve("block/get?stream-channels=true&arg=" + hash);
        }

        public byte[] rm(Multihash hash) throws IOException {
            return retrieve("block/rm?stream-channels=true&arg=" + hash);
        }

        public List<MerkleNode> put(List<byte[]> data) throws IOException {
            return put(data, Optional.empty());
        }

        public List<MerkleNode> put(List<byte[]> data, Optional<String> format) throws IOException {
            // N.B. Once IPFS implements a bulk put this can become a single multipart call with multiple 'files'
            List<MerkleNode> res = new ArrayList<>();
            for (byte[] value : data) {
                res.add(put(value, format));
            }
            return res;
        }

        public MerkleNode put(byte[] data, Optional<String> format) throws IOException {
            String fmt = format.map(f -> "&format=" + f).orElse("");
            Multipart m = new Multipart(protocol +"://" + host + ":" + port + apiVersion+"block/put?stream-channels=true" + fmt, "UTF-8");
            try {
                m.addFilePart("file", Paths.get(""), new NamedStreamable.ByteArrayWrapper(data));
                String res = m.finish();
                return JSONParser.parseStream(res).stream().map(x -> MerkleNode.fromJSON((Map<String, Object>) x)).findFirst().get();
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        public Map stat(Multihash hash) throws IOException {
            return retrieveMap("block/stat?stream-channels=true&arg=" + hash);
        }
    }

    /* 'ipfs object' is a plumbing command used to manipulate DAG objects directly. {Object} is a subset of {Block}
     */
    public class IPFSObject {
        @Deprecated
        public List<MerkleNode> put(List<byte[]> data) throws IOException {
            Multipart m = new Multipart(protocol +"://" + host + ":" + port + apiVersion+"object/put?stream-channels=true", "UTF-8");
            for (byte[] f : data)
                m.addFilePart("file", Paths.get(""), new NamedStreamable.ByteArrayWrapper(f));
            String res = m.finish();
            return JSONParser.parseStream(res).stream().map(x -> MerkleNode.fromJSON((Map<String, Object>) x)).collect(Collectors.toList());
        }
        @Deprecated
        public List<MerkleNode> put(String encoding, List<byte[]> data) throws IOException {
            if (!"json".equals(encoding) && !"protobuf".equals(encoding))
                throw new IllegalArgumentException("Encoding must be json or protobuf");
            Multipart m = new Multipart(protocol +"://" + host + ":" + port + apiVersion+"object/put?stream-channels=true&encoding="+encoding, "UTF-8");
            for (byte[] f : data)
                m.addFilePart("file", Paths.get(""), new NamedStreamable.ByteArrayWrapper(f));
            String res = m.finish();
            return JSONParser.parseStream(res).stream().map(x -> MerkleNode.fromJSON((Map<String, Object>) x)).collect(Collectors.toList());
        }
        @Deprecated
        public MerkleNode get(Multihash hash) throws IOException {
            Map json = retrieveMap("object/get?stream-channels=true&arg=" + hash);
            json.put("Hash", hash.toBase58());
            return MerkleNode.fromJSON(json);
        }
        @Deprecated
        public MerkleNode links(Multihash hash) throws IOException {
            Map json = retrieveMap("object/links?stream-channels=true&arg=" + hash);
            return MerkleNode.fromJSON(json);
        }
        @Deprecated
        public Map<String, Object> stat(Multihash hash) throws IOException {
            return retrieveMap("object/stat?stream-channels=true&arg=" + hash);
        }
        @Deprecated
        public byte[] data(Multihash hash) throws IOException {
            return retrieve("object/data?stream-channels=true&arg=" + hash);
        }
        @Deprecated
        public MerkleNode _new(Optional<String> template) throws IOException {
            if (template.isPresent() && !ObjectTemplates.contains(template.get()))
                throw new IllegalStateException("Unrecognised template: "+template.get());
            Map json = retrieveMap("object/new?stream-channels=true"+(template.isPresent() ? "&arg=" + template.get() : ""));
            return MerkleNode.fromJSON(json);
        }
        @Deprecated
        public MerkleNode patch(Multihash base, String command, Optional<byte[]> data, Optional<String> name, Optional<Multihash> target) throws IOException {
            if (!ObjectPatchTypes.contains(command))
                throw new IllegalStateException("Illegal Object.patch command type: "+command);
            String targetPath = "object/patch/"+command+"?arg=" + base.toBase58();
            if (name.isPresent())
                targetPath += "&arg=" + name.get();
            if (target.isPresent())
                targetPath += "&arg=" + target.get().toBase58();

            switch (command) {
                case "add-link":
                    if (!target.isPresent())
                        throw new IllegalStateException("add-link requires name and target!");
                case "rm-link":
                    if (!name.isPresent())
                        throw new IllegalStateException("link name is required!");
                    return MerkleNode.fromJSON(retrieveMap(targetPath));
                case "set-data":
                case "append-data":
                    if (!data.isPresent())
                        throw new IllegalStateException("set-data requires data!");
                    Multipart m = new Multipart(protocol +"://" + host + ":" + port + apiVersion+"object/patch/"+command+"?arg="+base.toBase58()+"&stream-channels=true", "UTF-8");
                    m.addFilePart("file", Paths.get(""), new NamedStreamable.ByteArrayWrapper(data.get()));
                    String res = m.finish();
                    return MerkleNode.fromJSON(JSONParser.parse(res));

                default:
                    throw new IllegalStateException("Unimplemented");
            }
        }
    }

    public class Name {
        public Map publish(Multihash hash) throws IOException {
            return publish(hash, Optional.empty());
        }

        public Map publish(Multihash hash, Optional<String> id) throws IOException {
            return retrieveMap("name/publish?arg=/ipfs/" + hash + id.map(name -> "&key=" + name).orElse(""));
        }

        public String resolve(Multihash hash, boolean noCache) throws IOException {
            Map res = (Map) retrieveAndParse("name/resolve?arg=" + hash+ "&nocache="+noCache);
            return (String)res.get("Path");
        }
    }

    public class DHT {
        @Deprecated
        public List<Map<String, Object>> findprovs(Multihash hash) throws IOException {
            return getAndParseStream("dht/findprovs?arg=" + hash).stream()
                    .map(x -> (Map<String, Object>) x)
                    .collect(Collectors.toList());
        }

        public Map query(Multihash peerId) throws IOException {
            return retrieveMap("dht/query?arg=" + peerId.toString());
        }
        @Deprecated
        public Map findpeer(Multihash id) throws IOException {
            return retrieveMap("dht/findpeer?arg=" + id.toString());
        }
        @Deprecated
        public Map get(Multihash hash) throws IOException {
            return retrieveMap("dht/get?arg=" + hash);
        }
        @Deprecated
        public Map put(String key, String value) throws IOException {
            return retrieveMap("dht/put?arg=" + key + "&arg="+value);
        }
    }

    public class File {
        @Deprecated
        public Map ls(Multihash path) throws IOException {
            return retrieveMap("file/ls?arg=" + path);
        }
    }

    public class Files {

        public String chcid() throws IOException {
            return retrieveString("files/chcid");
        }

        public String chcid(String path) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            return retrieveString("files/chcid?args=" + arg);
        }

        public String chcid(String path, Optional<Integer> cidVersion, Optional<String> hash) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            String cid = cidVersion.isPresent() ? "&cid-version=" + cidVersion.get() : "";
            String hashFunc = hash.isPresent() ? "&hash=" + hash.get() : "";
            return retrieveString("files/chcid?args=" + arg + cid + hashFunc);
        }

        public String cp(String source, String dest, boolean parents) throws IOException {
            return retrieveString("files/cp?arg=" + URLEncoder.encode(source, "UTF-8") + "&arg=" +
                    URLEncoder.encode(dest, "UTF-8") + "&parents=" + parents);
        }

        public Map flush() throws IOException {
            return retrieveMap("files/flush");
        }

        public Map flush(String path) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            return retrieveMap("files/flush?arg=" + arg);
        }

        public List<Map> ls() throws IOException {
            return (List<Map>)retrieveMap("files/ls").get("Entries");
        }

        public List<Map> ls(String path) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            return (List<Map>)retrieveMap("files/ls?arg=" + arg).get("Entries");
        }

        public List<Map> ls(String path, boolean longListing, boolean u) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            return (List<Map>)retrieveMap("files/ls?arg=" + arg + "&long=" + longListing + "&U=" + u).get("Entries");
        }

        public String mkdir(String path, boolean parents) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            return retrieveString("files/mkdir?arg=" + arg + "&parents=" + parents);
        }

        public String mkdir(String path, boolean parents, Optional<Integer> cidVersion, Optional<String> hash) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            String cid = cidVersion.isPresent() ? "&cid-version=" + cidVersion.get() : "";
            String hashFunc = hash.isPresent() ? "&hash=" + hash.get() : "";
            return retrieveString("files/mkdir?arg=" + arg + "&parents=" + parents + cid + hashFunc);
        }

        public String mv(String source, String dest) throws IOException {
            return retrieveString("files/mv?arg=" + URLEncoder.encode(source, "UTF-8") + "&arg=" +
                    URLEncoder.encode(dest, "UTF-8"));
        }

        public byte[] read(String path) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            return retrieve("files/read?arg=" + arg);
        }

        public byte[] read(String path, int offset, int count) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            return retrieve("files/read?arg=" + arg + "&offset=" + offset + "&count=" + count);
        }

        public String rm(String path, boolean recursive, boolean force) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            return retrieveString("files/rm?arg=" + arg + "&recursive=" + recursive + "&force=" + force);
        }

        public Map stat(String path) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            return retrieveMap("files/stat?arg=" + arg);
        }
        public Map stat(String path, Optional<String> format, boolean withLocal) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            String formatStr = format.isPresent() ? "&format=" + format.get() : "";
            return retrieveMap("files/stat?arg=" + arg + formatStr + "&with-local=" + withLocal);
        }
        public String write(String path, NamedStreamable uploadFile, boolean create, boolean parents) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            String rpcParams = "files/write?arg=" + arg + "&create=" + create + "&parents=" + parents;
            URL target = new URL(protocol,host,port,apiVersion + rpcParams);
            Multipart m = new Multipart(target.toString(),"UTF-8");
            if (uploadFile.isDirectory()) {
                throw new IllegalArgumentException("Input must be a file");
            } else {
                m.addFilePart("file", Paths.get(""), uploadFile);
            }
            return m.finish();
        }

        public String write(String path, NamedStreamable uploadFile, WriteFilesArgs args) throws IOException {
            String arg = URLEncoder.encode(path, "UTF-8");
            String rpcParams = "files/write?arg=" + arg + "&" + args.toQueryString();
            URL target = new URL(protocol,host,port,apiVersion + rpcParams);
            Multipart m = new Multipart(target.toString(),"UTF-8");
            if (uploadFile.isDirectory()) {
                throw new IllegalArgumentException("Input must be a file");
            } else {
                m.addFilePart("file", Paths.get(""), uploadFile);
            }
            return m.finish();
        }
    }

    public class FileStore {

        public Map dups() throws IOException {
            return retrieveMap("filestore/dups");
        }

        public Map ls(boolean fileOrder) throws IOException {
            return retrieveMap("filestore/ls?file-order=" + fileOrder);
        }

        public Map verify(boolean fileOrder) throws IOException {
            return retrieveMap("filestore/verify?file-order=" + fileOrder);
        }
    }

    // Network commands
    public List<MultiAddress> bootstrap() throws IOException {
        return ((List<String>)retrieveMap("bootstrap/").get("Peers"))
                .stream()
                .flatMap(x -> {
                    try {
                        return Stream.of(new MultiAddress(x));
                    } catch (Exception e) {
                        return Stream.empty();
                    }
                }).collect(Collectors.toList());
    }

    public class Bitswap {
        public Map ledger(Multihash peerId) throws IOException {
            return retrieveMap("bitswap/ledger?arg="+peerId);
        }

        public String reprovide() throws IOException {
            return retrieveString("bitswap/reprovide");
        }
        public Map stat() throws IOException {
            return retrieveMap("bitswap/stat");
        }
        public Map stat(boolean verbose) throws IOException {
            return retrieveMap("bitswap/stat?verbose=" + verbose);
        }
        public Map wantlist(Multihash peerId) throws IOException {
            return retrieveMap("bitswap/wantlist?peer=" + peerId);
        }
    }
    public class Bootstrap {

        public List<MultiAddress> add(MultiAddress addr) throws IOException {
            return ((List<String>)retrieveMap("bootstrap/add?arg="+addr).get("Peers"))
                    .stream().map(x -> new MultiAddress(x)).collect(Collectors.toList());
        }

        public List<MultiAddress> add() throws IOException {
            return ((List<String>)retrieveMap("bootstrap/add/default").get("Peers"))
                    .stream().map(x -> new MultiAddress(x)).collect(Collectors.toList());
        }

        public List<MultiAddress> list() throws IOException {
            return ((List<String>)retrieveMap("bootstrap/list").get("Peers"))
                    .stream().map(x -> new MultiAddress(x)).collect(Collectors.toList());
        }

        public List<MultiAddress> rm(MultiAddress addr) throws IOException {
            return rm(addr, false);
        }

        public List<MultiAddress> rm(MultiAddress addr, boolean all) throws IOException {
            return ((List<String>)retrieveMap("bootstrap/rm?"+(all ? "all=true&":"")+"arg="+addr).get("Peers")).stream().map(x -> new MultiAddress(x)).collect(Collectors.toList());
        }

        public List<MultiAddress> rmAll() throws IOException {
            return ((List<String>)retrieveMap("bootstrap/rm/all").get("Peers")).stream().map(x -> new MultiAddress(x)).collect(Collectors.toList());
        }
    }

    /*  ipfs swarm is a tool to manipulate the network swarm. The swarm is the
        component that opens, listens for, and maintains connections to other
        ipfs peers in the internet.
     */
    public class Swarm {
        public List<Peer> peers() throws IOException {
            Map m = retrieveMap("swarm/peers?stream-channels=true");
            if (m.get("Peers") == null) {
                return Collections.emptyList();
            }
            return ((List<Object>)m.get("Peers")).stream()
                    .flatMap(json -> {
                        try {
                            return Stream.of(Peer.fromJSON(json));
                        } catch (Exception e) {
                            return Stream.empty();
                        }
                    }).collect(Collectors.toList());
        }

        public Map<Multihash, List<MultiAddress>> addrs() throws IOException {
            Map m = retrieveMap("swarm/addrs?stream-channels=true");
            return ((Map<String, Object>)m.get("Addrs")).entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            e -> Multihash.fromBase58(e.getKey()),
                            e -> ((List<String>)e.getValue())
                                    .stream()
                                    .map(MultiAddress::new)
                                    .collect(Collectors.toList())));
        }
        public Map listenAddrs() throws IOException {
            return retrieveMap("swarm/addrs/listen");
        }
        public Map localAddrs(boolean showPeerId) throws IOException {
            return retrieveMap("swarm/addrs/local?id=" + showPeerId);
        }
        public Map connect(MultiAddress multiAddr) throws IOException {
            Map m = retrieveMap("swarm/connect?arg="+multiAddr);
            return m;
        }

        public Map disconnect(MultiAddress multiAddr) throws IOException {
            Map m = retrieveMap("swarm/disconnect?arg="+multiAddr);
            return m;
        }
        public Map filters() throws IOException {
            return retrieveMap("swarm/filters");
        }
        public Map addFilter(String multiAddrFilter) throws IOException {
            return retrieveMap("swarm/filters/add?arg="+multiAddrFilter);
        }
        public Map rmFilter(String multiAddrFilter) throws IOException {
            return retrieveMap("swarm/filters/rm?arg="+multiAddrFilter);
        }
        public Map lsPeering() throws IOException {
            return retrieveMap("swarm/peering/ls");
        }
        public Map addPeering(MultiAddress multiAddr) throws IOException {
            return retrieveMap("swarm/peering/add?arg="+multiAddr);
        }
        public Map rmPeering(Multihash multiAddr) throws IOException {
            return retrieveMap("swarm/peering/rm?arg="+multiAddr);
        }
    }

    public class Dag {
        public byte[] get(Cid cid) throws IOException {
            return retrieve("dag/get?stream-channels=true&arg=" + cid);
        }

        public MerkleNode put(byte[] object) throws IOException {
            return put("dag-json", object, "dag-cbor");
        }

        public MerkleNode put(String inputFormat, byte[] object) throws IOException {
            return put(inputFormat, object, "dag-cbor");
        }

        public MerkleNode put(byte[] object, String outputFormat) throws IOException {
            return put("dag-json", object, outputFormat);
        }

        public MerkleNode put(String inputFormat, byte[] object, String outputFormat) throws IOException {
            String prefix = protocol + "://" + host + ":" + port + apiVersion;
            Multipart m = new Multipart(prefix + "dag/put/?stream-channels=true&input-codec=" + inputFormat + "&store-codec=" + outputFormat, "UTF-8");
            m.addFilePart("file", Paths.get(""), new NamedStreamable.ByteArrayWrapper(object));
            String res = m.finish();
            return MerkleNode.fromJSON(JSONParser.parse(res));
        }

        public Map resolve(String path) throws IOException {
            return retrieveMap("dag/resolve?&arg=" + path);
        }

        public Map stat(Cid cid) throws IOException {
            return retrieveMap("dag/stat?&arg=" + cid);
        }
    }

    public class Diag {
        public List<Map> cmds() throws IOException {
            return (List)retrieveAndParse("diag/cmds");
        }

        public List<Map> cmds(boolean verbose) throws IOException {
            return (List)retrieveAndParse("diag/cmds?verbose=" + verbose);
        }

        public String clearCmds() throws IOException {
            return retrieveString("diag/cmds/clear");
        }

        public String profile() throws IOException {
            return retrieveString("diag/profile");
        }

        public Map sys() throws IOException {
            return retrieveMap("diag/sys?stream-channels=true");
        }
    }

    public Map ping(Multihash target) throws IOException {
        return retrieveMap("ping/" + target.toBase58());
    }

    public Map id(Multihash target) throws IOException {
        return retrieveMap("id/" + target.toBase58());
    }

    public Map id() throws IOException {
        return retrieveMap("id");
    }

    public class Stats {
        public Map bitswap(boolean verbose) throws IOException {
            return retrieveMap("stats/bitswap?verbose=" + verbose);
        }
        public Map bw() throws IOException {
            return retrieveMap("stats/bw");
        }
        public Map dht() throws IOException {
            return retrieveMap("stats/dht");
        }
        public Map provide() throws IOException {
            return retrieveMap("stats/provide");
        }
        public RepoStat repo(boolean sizeOnly) throws IOException {
            return RepoStat.fromJson(retrieveAndParse("stats/repo?size-only=" + sizeOnly));
        }
    }

    // Tools
    public String version() throws IOException {
        Map m = (Map)retrieveAndParse("version");
        return (String)m.get("Version");
    }

    public Map commands() throws IOException {
        return retrieveMap("commands");
    }

    public Map log() throws IOException {
        return retrieveMap("log/tail");
    }

    public Map config(String entry, Optional<String> value, Optional<Boolean> setBool) throws IOException {
        String valArg = value.isPresent() ? "&arg=" + value.get() : "";
        String setBoolArg = setBool.isPresent() ? "&arg=" + setBool.get() : "";
        return retrieveMap("config?arg=" + entry + valArg + setBoolArg);
    }

    public class Config {
        public Map show() throws IOException {
            return (Map)retrieveAndParse("config/show");
        }

        public Map profileApply(String profile, boolean dryRun) throws IOException {
            return (Map)retrieveAndParse("config/profile/apply?arg="+profile + "&dry-run" + dryRun);
        }

        public void replace(NamedStreamable file) throws IOException {
            Multipart m = new Multipart(protocol +"://" + host + ":" + port + apiVersion+"config/replace?stream-channels=true", "UTF-8");
            m.addFilePart("file", Paths.get(""), file);
            String res = m.finish();
        }

        public Object get(String key) throws IOException {
            Map m = (Map)retrieveAndParse("config?arg="+key);
            return m.get("Value");
        }

        public Map set(String key, Object value) throws IOException {
            return retrieveMap("config?arg=" + key + "&arg=" + value);
        }
    }

    public Object update() throws IOException {
        return retrieveAndParse("update");
    }

    public class Update {
        public Object check() throws IOException {
            return retrieveAndParse("update/check");
        }

        public Object log() throws IOException {
            return retrieveAndParse("update/log");
        }
    }

    private Map retrieveMap(String path) throws IOException {
        return (Map)retrieveAndParse(path);
    }

    private Object retrieveAndParse(String path) throws IOException {
        byte[] res = retrieve(path);
        return JSONParser.parse(new String(res));
    }

    private Stream<Object> retrieveAndParseStream(String path, ForkJoinPool executor) throws IOException {
        BlockingQueue<CompletableFuture<byte[]>> results = new LinkedBlockingQueue<>();
        InputStream in = retrieveStream(path);
        executor.submit(() -> getObjectStream(in,
                res -> {
                    results.add(CompletableFuture.completedFuture(res));
                },
                err -> {
                    CompletableFuture<byte[]> fut = new CompletableFuture<>();
                    fut.completeExceptionally(err);
                    results.add(fut);
                })
        );
        return Stream.generate(() -> {
            try {
                return JSONParser.parse(new String(results.take().get()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * A synchronous stream retriever that consumes the calling thread
     * @param path
     * @param results
     * @throws IOException
     */
    private void retrieveAndParseStream(String path, Consumer<Object> results, Consumer<IOException> err) throws IOException {
        getObjectStream(retrieveStream(path), d -> results.accept(JSONParser.parse(new String(d))), err);
    }

    private String retrieveString(String path) throws IOException {
        URL target = new URL(protocol, host, port, apiVersion + path);
        return new String(IPFS.get(target, connectTimeoutMillis, readTimeoutMillis));
    }

    private byte[] retrieve(String path) throws IOException {
        URL target = new URL(protocol, host, port, apiVersion + path);
        return IPFS.get(target, connectTimeoutMillis, readTimeoutMillis);
    }

    private static byte[] get(URL target, int connectTimeoutMillis, int readTimeoutMillis) throws IOException {
        HttpURLConnection conn = configureConnection(target, "POST", connectTimeoutMillis, readTimeoutMillis);
        conn.setDoOutput(true);
        /* See IPFS commit for why this is a POST and not a GET https://github.com/ipfs/go-ipfs/pull/7097
           This commit upgrades go-ipfs-cmds and configures the commands HTTP API Handler
           to only allow POST/OPTIONS, disallowing GET and others in the handling of
           command requests in the IPFS HTTP API (where before every type of request
           method was handled, with GET/POST/PUT/PATCH being equivalent).

           The Read-Only commands that the HTTP API attaches to the gateway endpoint will
           additional handled GET as they did before (but stop handling PUT,DELETEs).

           By limiting the request types we address the possibility that a website
           accessed by a browser abuses the IPFS API by issuing GET requests to it which
           have no Origin or Referrer set, and are thus bypass CORS and CSRF protections.

           This is a breaking change for clients that relay on GET requests against the
           HTTP endpoint (usually :5001). Applications integrating on top of the
           gateway-read-only API should still work (including cross-domain access).
        */
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");

        try {
            OutputStream out = conn.getOutputStream();
            out.write(new byte[0]);
            out.flush();
            out.close();
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream resp = new ByteArrayOutputStream();

            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) >= 0)
                resp.write(buf, 0, r);
            return resp.toByteArray();
        } catch (ConnectException e) {
            throw new RuntimeException("Couldn't connect to IPFS daemon at "+target+"\n Is IPFS running?");
        } catch (IOException e) {
            throw extractError(e, conn);
        }
    }

    public static RuntimeException extractError(IOException e, HttpURLConnection conn) {
        InputStream errorStream = conn.getErrorStream();
        String err = errorStream == null ? e.getMessage() : new String(readFully(errorStream));
        return new RuntimeException("IOException contacting IPFS daemon.\n"+err+"\nTrailer: " + conn.getHeaderFields().get("Trailer"), e);
    }

    private void getObjectStream(InputStream in, Consumer<byte[]> processor, Consumer<IOException> error) {
        byte LINE_FEED = (byte)10;

        try {
            ByteArrayOutputStream resp = new ByteArrayOutputStream();

            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) >= 0) {
                resp.write(buf, 0, r);
                if (buf[r - 1] == LINE_FEED) {
                    processor.accept(resp.toByteArray());
                    resp.reset();
                }
            }
        } catch (IOException e) {
            error.accept(e);
        }
    }

    private List<Object> getAndParseStream(String path) throws IOException {
        InputStream in = retrieveStream(path);
        byte LINE_FEED = (byte)10;

        ByteArrayOutputStream resp = new ByteArrayOutputStream();

        byte[] buf = new byte[4096];
        int r;
        List<Object> res = new ArrayList<>();
        while ((r = in.read(buf)) >= 0) {
            resp.write(buf, 0, r);
            if (buf[r - 1] == LINE_FEED) {
                res.add(JSONParser.parse(new String(resp.toByteArray())));
                resp.reset();
            }
        }
        return res;
    }

    private InputStream retrieveStream(String path) throws IOException {
        URL target = new URL(protocol, host, port, apiVersion + path);
        return IPFS.getStream(target, connectTimeoutMillis, readTimeoutMillis);
    }

    private static InputStream getStream(URL target, int connectTimeoutMillis, int readTimeoutMillis) throws IOException {
        HttpURLConnection conn = configureConnection(target, "POST", connectTimeoutMillis, readTimeoutMillis);
        try {
            return conn.getInputStream();
        } catch (IOException e) {
            throw extractError(e, conn);
        }
    }

    private Map postMap(String path, byte[] body, Map<String, String> headers) throws IOException {
        URL target = new URL(protocol, host, port, apiVersion + path);
        return (Map) JSONParser.parse(new String(post(target, body, headers, connectTimeoutMillis, readTimeoutMillis)));
    }

    private static byte[] post(URL target, byte[] body, Map<String, String> headers, int connectTimeoutMillis, int readTimeoutMillis) throws IOException {
        HttpURLConnection conn = configureConnection(target, "POST", connectTimeoutMillis, readTimeoutMillis);
        for (String key: headers.keySet())
            conn.setRequestProperty(key, headers.get(key));
        conn.setDoOutput(true);
        OutputStream out = conn.getOutputStream();
        out.write(body);
        out.flush();
        out.close();

        try {
            InputStream in = conn.getInputStream();
            return readFully(in);
        } catch (IOException e) {
            throw extractError(e, conn);
        }
    }

    private static final byte[] readFully(InputStream in) {
        try {
            ByteArrayOutputStream resp = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r=in.read(buf)) >= 0)
                resp.write(buf, 0, r);
            return resp.toByteArray();
            
        } catch(IOException ex) {
            throw new RuntimeException("Error reading InputStrean", ex);
        }
    }

    private static boolean detectSSL(MultiAddress multiaddress) {
        return multiaddress.toString().contains("/https");
    }
    
    private static HttpURLConnection configureConnection(URL target, String method, int connectTimeoutMillis, int readTimeoutMillis) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) target.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(connectTimeoutMillis);
        conn.setReadTimeout(readTimeoutMillis);
        return conn;
    }
}
