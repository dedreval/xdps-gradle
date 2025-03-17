package com.wiley.tes.util.res;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 29.03.13
 *
 * @param <K>  Resource unique key
 * @param <R>  Resource
 */
public class DataTable<K extends Serializable, R extends Resource<K>> extends ResourceContainer<K, R> {
    private static final Logger LOG = Logger.getLogger(DataTable.class);

    private final Map<K, Res<R>> resources = new HashMap<>();
    private final String name;

    public DataTable(String name) {
        this.name = name;
    }

    public void add(K id, R res) {

        if (id == null) {
            LOG.warn("resource has no id: " + res.getClass());
            return;
        }

        Res<R> holder = resources.get(id);
        if (holder == null) {

            holder = new Res<>(null);
            resources.put(id, holder);
        }

        holder.resource = res;
    }

    @Override
    protected Res<R> find(K id) {
        return resources.get(id);
    }

    public Res<R> get(K id, R r) {

        Res<R> holder = resources.get(id);

        if (holder == null) {

            holder = new Res<>(r);
            resources.put(id, holder);
        }

        return holder;
    }

    public Res<R> get(K id) {

        Res<R> holder = resources.get(id);

        if (holder == null) {

            holder = new Res<>(null);
            resources.put(id, holder);
        }

        return holder;
    }

    public int size() {
        return resources.size();
    }

    public Collection<Res<R>> values() {
        return resources.values();
    }

    public Set<K> keys() {
        return resources.keySet();
    }

    public void publish(R res) {
        add(res.getId(), res);
    }

    public boolean validate() {

        LOG.debug(String.format("validating resource table: %s...", name));

        boolean ok = true;

        for (Map.Entry<K, Res<R>> entry: resources.entrySet()) {
            if (!entry.getValue().exist()) {
                ok = false;
                LOG.error(String.format("found null reference to resource id: %s !", entry.getKey()));
            } else {
                LOG.trace(String.format("found resource id: %s value: %s", entry.getKey(), entry.getValue()));
            }
        }

        LOG.info(String.format("validating resource table: %s done (%d)", name, resources.size()));

        return ok;
    }
}


