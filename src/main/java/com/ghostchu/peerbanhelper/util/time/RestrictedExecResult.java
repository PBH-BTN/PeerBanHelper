package com.ghostchu.peerbanhelper.util.time;

public record RestrictedExecResult<T>(boolean timeout, T result) {

}
