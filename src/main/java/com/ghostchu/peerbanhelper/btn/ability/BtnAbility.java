package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.text.TranslationComponent;

public interface BtnAbility {
    String getName();

    TranslationComponent getDisplayName();

    TranslationComponent getDescription();

    void load();

    void unload();

    boolean lastStatus();

    TranslationComponent lastMessage();

    long lastStatusAt();
}