package com.ghostchu.peerbanhelper.platform.types;

public interface EcoQosAPI {
    default boolean supported(){
        return false;
    }
    default void apply() throws UnsupportedOperationException{
        throw new UnsupportedOperationException("EcoQosAPI is not supported on this platform.");
    }
}
