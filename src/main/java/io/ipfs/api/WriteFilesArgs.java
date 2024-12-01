package io.ipfs.api;

import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

/*
Example usage:
        WriteFilesArgs args = WriteFilesArgs.Builder.newInstance()
                .setCreate()
                .setParents()
                .build();
 */
final public class WriteFilesArgs {

    private final Map<String, String> args = new HashMap<>();

    public WriteFilesArgs(Builder builder)
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

        public Builder setOffset(int offset) {
            args.put("offset", String.valueOf(offset));
            return this;
        }
        public Builder setCreate() {
            args.put("create", TRUE);
            return this;
        }
        public Builder setParents() {
            args.put("parents", TRUE);
            return this;
        }
        public Builder setTruncate() {
            args.put("truncate", TRUE);
            return this;
        }
        public Builder setCount(int count) {
            args.put("count", String.valueOf(count));
            return this;
        }
        public Builder setRawLeaves() {
            args.put("raw-leaves", TRUE);
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
        public WriteFilesArgs build()
        {
            return new WriteFilesArgs(this);
        }
    }
}
