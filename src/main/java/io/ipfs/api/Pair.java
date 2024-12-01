package io.ipfs.api;

import java.util.function.Function;

public class Pair<L,R> {
    public final L left;
    public final R right;

    public Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public Pair<R, L> swapped() {
        return new Pair<>(right, left);
    }

    public <B,D> Pair<B, D> apply(Function<L, B> applyLeft, Function<R, D> applyRight) {
        return new Pair<>(
                applyLeft.apply(left),
                applyRight.apply(right));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Pair<?, ?> pair = (Pair<?, ?>) o;

        if (left != null ? !left.equals(pair.left) : pair.left != null) return false;
        return right != null ? right.equals(pair.right) : pair.right == null;

    }

    @Override
    public int hashCode() {
        int result = left != null ? left.hashCode() : 0;
        result = 31 * result + (right != null ? right.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return String.format("(%s, %s)", left.toString(), right.toString());
    }
}