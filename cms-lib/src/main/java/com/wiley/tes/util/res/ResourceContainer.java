package com.wiley.tes.util.res;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 29.03.13
  *
 * @param <K>  Resource key to identify
 * @param <R>  Resource
 */
public abstract class ResourceContainer<K, R extends Resource> implements IResourceContainer<R> {

    protected abstract Res<R> find(K id);

    public final Res<R> findResource(K id) {

        //ResourceManager.instance();  // it's guarantees initialization
        return find(id);
    }
}

