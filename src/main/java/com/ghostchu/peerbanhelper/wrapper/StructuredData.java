package com.ghostchu.peerbanhelper.wrapper;

import java.util.LinkedHashMap;

public class StructuredData<K,V> extends LinkedHashMap<K,V> {
    public static StructuredData<String, Object> create(){
        return new StructuredData<>();
    }

    public StructuredData<K,V> add(K key,V value){
        put(key, value);
        return this;
    }
}
