package io.ipfs.api;

import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

/*
Example usage:
        AddArgs args = AddArgs.Builder.newInstance()
                .setInline()
                .setCidVersion(1)
                .build();
 */
public final class AddArgs {

    private final Map<String, String> args = new HashMap<>();

    public AddArgs(Builder builder)
    {
        args.putAll(builder.args);
    }
    @Override
    public String toString()
    {
        List<String> asList = args.entrySet()
                .stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> e.getKey() + " = " + e.getValue()).collect(Collectors.toList());
        return Arrays.toString(asList.toArray());
    }
    public String toQueryString()
    {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry: args.entrySet()) {
            sb.append("&").append(entry.getKey())
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue()));
        }
        return sb.length() > 0 ? sb.toString().substring(1) : sb.toString();
    }
    public static class Builder {
        private static final String TRUE = "true";
        private final Map<String, String> args = new HashMap<>();
        private Builder() {}
        public static Builder newInstance()
        {
            return new Builder();
        }
        public Builder setQuiet() {
            args.put("quiet", TRUE);
            return this;
        }
        public Builder setQuieter() {
            args.put("quieter", TRUE);
            return this;
        }
        public Builder setSilent() {
            args.put("silent", TRUE);
            return this;
        }
        public Builder setTrickle() {
            args.put("trickle", TRUE);
            return this;
        }
        public Builder setOnlyHash() {
            args.put("only-hash", TRUE);
            return this;
        }
        public Builder setWrapWithDirectory() {
            args.put("wrap-with-directory", TRUE);
            return this;
        }
        public Builder setChunker(String chunker) {
            args.put("chunker", chunker);
            return this;
        }
        public Builder setRawLeaves() {
            args.put("raw-leaves", TRUE);
            return this;
        }
        public Builder setNocopy() {
            args.put("nocopy", TRUE);
            return this;
        }
        public Builder setFscache() {
            args.put("fscache", TRUE);
            return this;
        }
        public Builder setCidVersion(int version) {
            args.put("cid-version", String.valueOf(version));
            return this;
        }
        public Builder setHash(String hashFunction) {
            args.put("hash", hashFunction);
            return this;
        }
        public Builder setInline() {
            args.put("inline", TRUE);
            return this;
        }
        public Builder setInlineLimit(int maxBlockSize) {
            args.put("inline-limit", String.valueOf(maxBlockSize));
            return this;
        }
        public Builder setPin() {
            args.put("pin", TRUE);
            return this;
        }
        public Builder setToFiles(String path) {
            args.put("to-files", path);
            return this;
        }
        public AddArgs build()
        {
            return new AddArgs(this);
        }
    }
}
