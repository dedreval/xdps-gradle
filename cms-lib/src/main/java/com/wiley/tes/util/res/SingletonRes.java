package com.wiley.tes.util.res;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 29.03.13
 *
 * @param <R> Resource
 */
public class SingletonRes<R extends Resource> extends Res<R> implements IResourceContainer<R> {
    private static final Logger LOG = Logger.getLogger(SingletonRes.class);

    private final String name;

    public SingletonRes(String sName, R pR) {

        super(pR);
        name = sName;
    }

    public final String getName() {
        return name;
    }

    public final Res<R> getResource() {
        //ResourceManager.instance();  // it's guarantees initialization
        return this;
    }

    public boolean validate() {

        if (!exist()) {

            LOG.warn("[res:check] error: found null reference to resource: " + name);
            return false;
        }

        return true;
    }

    public void publish(R res) {
        resource = res;
    }

    public int size() {
        return exist() ? 1 : 0;
    }

    @Override
    public String toString() {
        return name + ": " + (resource == null ? "null" : resource.toString());
    }
}

