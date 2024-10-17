package com.ghostchu.peerbanhelper.btn.ability;

public interface BtnAbility {
    void load();

    void unload();

    boolean lastStatus();

    String lastMessage();

    long lastStatusAt();
}