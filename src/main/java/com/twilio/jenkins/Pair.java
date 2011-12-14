package com.twilio.jenkins;

/**
 * A simple {@link Pair} implementation using generics.
 * 
 * @author Christer Fahlgren
 * 
 * @param <L> the type of the left
 * @param <R> the type of the right
 */
public class Pair<L, R> {

    private final L left;
    private final R right;

    /**
     * Construct an immutable {@link Pair}, passing in the left and right attributes
     * @param left the left attribute
     * @param right the right attribute
     */
    public Pair(final L left, final R right) {
        this.left = left;
        this.right = right;
    }

    /**
     * Returns the left attribute.
     * 
     * @return the left attribute
     */
    public L getLeft() {
        return this.left;
    }

    /**
     * Returns the right attribute.
     * 
     * @return the right attribute
     */
    public R getRight() {
        return this.right;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return this.left.hashCode() ^ this.right.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (o == null) {
            return false;
        }
        if (!(o instanceof Pair)) {
            return false;
        }
        final Pair<L,R> pairo = (Pair<L,R>) o;
        return this.left.equals(pairo.getLeft()) && this.right.equals(pairo.getRight());
    }

}