package com.ghostchu.peerbanhelper.text;

import lombok.Getter;

import java.util.Arrays;

@Getter
public class TranslationComponent {
    private final String key;
    private final Object[] params;

    public TranslationComponent(String key) {
        this.key = key;
        this.params = new String[0];
    }

    public TranslationComponent(String key, Object... params) {
        this.key = key;
        this.params = params;
    }

    public TranslationComponent(Lang key) {
        this(key.getKey());
    }

    public TranslationComponent(Lang key, String... params) {
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
