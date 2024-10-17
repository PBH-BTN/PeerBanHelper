package com.ghostchu.peerbanhelper.btn.ability;

public abstract class AbstractBtnAbility implements BtnAbility {
    private boolean lastStatus;
    private long lastStatusAt;
    private String lastMessage;


    public void setLastStatus(boolean success, String errorMsg) {
        this.lastStatus = success;
        this.lastMessage = errorMsg;
        this.lastStatusAt = System.currentTimeMillis();
    }

    @Override
    public boolean lastStatus() {
        return lastStatus;
    }

    @Override
    public String lastMessage() {
        return lastMessage;
    }

    @Override
    public long lastStatusAt() {
        return lastStatusAt;
    }
}
