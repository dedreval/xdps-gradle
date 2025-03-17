package com.wiley.tes.util.res;

import java.io.Serializable;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 28.03.13
 *
 * @param <R> Resource entity
 */
public class Res<R extends Resource> implements Serializable {

    protected volatile R resource;

    public Res(R r) {
        resource = r;
    }

    public R get() {
        return resource;
    }

    public boolean exist() {
        return resource != null;
    }

    @Override
    public String toString() {
        return resource == null ? "empty" : resource.toString();
    }

    public static <R extends Resource> R get(Res<R> res) {

        if (res == null) {
            return null;
        }
        return res.resource;
    }

    public static boolean valid(Res r) {
        return (r != null) && r.exist();
    }
}

