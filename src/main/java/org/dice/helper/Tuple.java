package org.dice.helper;

/**
 * Created by simon.hughes on 2/1/17.
 */
public class Tuple<A,B> {

    private final A a;
    private final B b;

    public Tuple(A a, B b)
    {
        this.a = a;
        this.b = b;
    }

    public A getA() {
        return a;
    }

    public B getB() {
        return b;
    }
}