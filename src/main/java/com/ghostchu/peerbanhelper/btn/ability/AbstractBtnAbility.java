package com.ghostchu.peerbanhelper.btn.ability;

import com.ghostchu.peerbanhelper.text.TranslationComponent;

public abstract class AbstractBtnAbility implements BtnAbility {
    private boolean lastStatus;
    private long lastStatusAt;
    private TranslationComponent lastMessage;


    public void setLastStatus(boolean success, TranslationComponent lastMessage) {
        this.lastStatus = success;
        this.lastMessage = lastMessage;
        this.lastStatusAt = System.currentTimeMillis();
    }

    @Override
    public boolean lastStatus() {
        return lastStatus;
    }

    @Override
    public TranslationComponent lastMessage() {
        return lastMessage;
    }

    @Override
    public long lastStatusAt() {
        return lastStatusAt;
    }
}
