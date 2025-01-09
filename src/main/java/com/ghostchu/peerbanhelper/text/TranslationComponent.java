package com.ghostchu.peerbanhelper.text;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

@Getter
public final class TranslationComponent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String key;
    private final Object[] params;

    public TranslationComponent(String key) {
        this.key = key;
        this.params = new String[0];
    }

    /**
     * 注意：Params 的所有对象必须都可以被 Gson 序列化/反序列化！
     *
     * @param key
     * @param params
     */
    public TranslationComponent(String key, Object... params) {
        this.key = key;
        this.params = params;
    }

    public TranslationComponent(Lang key) {
        this(key.getKey());
    }

    /**
     * 注意：Params 的所有对象必须都可以被 Gson 序列化/反序列化！
     *
     * @param key
     * @param params
     */
    public TranslationComponent(Lang key, Object... params) {
        this(key.getKey(), params);
    }

    @Override
    public String toString() {
        return "TranslationComponent{" +
               "key='" + key + '\'' +
               ", params=" + Arrays.toString(params) +
               '}';
    }
}
