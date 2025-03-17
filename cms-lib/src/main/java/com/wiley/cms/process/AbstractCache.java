package com.wiley.cms.process;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.process.jmx.JMXHolder;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 09.07.13
 */
public abstract class AbstractCache extends JMXHolder {
    protected static final Logger LOG = Logger.getLogger(AbstractCache.class);

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public String printState() {
        String ret = toString();
        LOG.info(ret);
        return ret;
    }

    /**
     * Set cache of T
     * @param <T>  any cached object
     */
    public static class TSetCache<T> {

        private Set<T> keys = Collections.synchronizedSet(new HashSet<T>());

        public Set<T> getObjects() {
            return keys;
        }

        public boolean contains(T key) {
            return keys.contains(key);
        }

        public boolean addObject(T key) {
            return keys.add(key);
        }

        public void removeObject(T key) {
            keys.remove(key);
        }

        public void clear() {
            keys.clear();
        }
    }

    /**
     * Tree cache of T with keys K and I
     * @param <K> first key
     * @param <I> second key
     * @param <T> cached object
     */
    public static class TTreeCache<K, I, T> {

        private Map<K, Map<I, T>> objects = new HashMap<K, Map<I, T>>();

        public Map<K, Map<I, T>> getObjects() {
            return objects;
        }

        public synchronized T getObject(K firstKey, I secondKey) {

            Map<I, T> map = objects.get(firstKey);
            if (map == null) {
                return null;
            }

            return map.get(secondKey);
        }

        public synchronized void addObject(K firstKey, I secondKey, T obj) {

            Map<I, T> map = objects.get(firstKey);
            if (map == null) {

                map = new HashMap<I, T>();
                objects.put(firstKey, map);
            }

            map.put(secondKey, obj);
        }

        public synchronized T removeObject(K firstKey, I secondKey) {
            Map<I, T> map = objects.get(firstKey);
            if (map != null) {
                return map.remove(secondKey);
            }
            return null;
        }

        public synchronized void clear() {
            objects.clear();
        }
    }

    /**
     * Map cache of T
     * @param <K>  key of T
     * @param <T>  cached object
     */
    public static class TCache<K, T> {

        private Map<K, T> objects = Collections.synchronizedMap(new HashMap<K, T>());

        public Map<K, T> getObjects() {
            return objects;
        }

        public synchronized T getObject(K key, T obj) {

            T ret = objects.get(key);
            if (ret == null) {
                addObject(key, obj);
                return obj;
            }
            return ret;
        }

        public synchronized T getObject(K key, Class<T> cl) {

            T ret = objects.get(key);
            if (ret == null) {
                try {
                    ret = cl.newInstance();
                    objects.put(key, ret);
                } catch (Exception e) {
                    LOG.error(e);
                }
            }
            return ret;
        }

        public T getObject(K key) {
            return objects.get(key);
        }

        public void addObject(K key, T obj) {
            objects.put(key, obj);
        }

        public synchronized boolean addKey(K key) {
            if (objects.containsKey(key)) {
                return false;
            }
            objects.put(key, null);
            return true;
        }

        public synchronized boolean addObject(K key, Class<T> cl) {
            if (objects.containsKey(key)) {
                return false;
            }
            getObject(key, cl);
            return true;
        }

        public T removeObject(K key) {
            return objects.remove(key);
        }

        public void clear() {
            objects.clear();
        }
    }
}

