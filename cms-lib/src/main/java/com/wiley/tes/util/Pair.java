package com.wiley.tes.util;

import java.io.Serializable;
import java.util.Objects;

/**
 * Date: 31.03.12
 * @author Olga Soletskaya
 *
 * @param <First>    the first member of pair
 * @param <Second>   the second member of pair
 * just to keep two objects
 */
public class Pair<First, Second> implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final int SEED = 37;

    public final First first;
    public final Second second;

    public Pair(First first, Second second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }
        if (!(o instanceof Pair)) {
            return false;
        }

        Pair pair = (Pair) o;
        return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
    }


    @Override
    public int hashCode() {
        int result;
        result = (first != null ? first.hashCode() : 0);
        result = SEED * result + (second != null ? second.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return first + " : " + second;
    }
}
