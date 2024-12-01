package io.ipfs.api;

import io.ipfs.cid.*;
import io.ipfs.multihash.*;

import java.util.*;

public class KeyInfo {

    public final String name;
    public final Multihash id;

    public KeyInfo(String name, Multihash id) {
        this.name = name;
        this.id = id;
    }

    public String toString() {
        return name + ": " + id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        KeyInfo keyInfo = (KeyInfo) o;

        if (name != null ? !name.equals(keyInfo.name) : keyInfo.name != null) return false;
        return id != null ? id.equals(keyInfo.id) : keyInfo.id == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        return result;
    }

    public static KeyInfo fromJson(Object json) {
        Map<String, String> m = (Map) json;
        return new KeyInfo(m.get("Name"), Cid.decode(m.get("Id")));
    }
}
