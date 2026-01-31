package com.ghostchu.peerbanhelper.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class Pair<L, R> {
    public L left;
    public R right;

    public Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public L getKey() {
        return left;
    }

    public R getValue() {
        return right;
    }

    public void setKey(L left) {
        this.left = left;
    }

    public void setValue(R right) {
        this.right = right;
    }

    public static  <L,R> Pair<L,R> of(L left, R right) {
        return new Pair<>(left, right);
    }
}
