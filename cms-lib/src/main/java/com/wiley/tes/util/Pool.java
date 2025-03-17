// $Id: Pool.java,v 1.2 2011-11-25 14:05:50 sgulin Exp $
// Created: 17.11.2005 T 15:03:56
// Copyright (C) 2005 by John Wiley & Sons Inc. All Rights Reserved.
package com.wiley.tes.util;

/**
 * An object pool. Usage:
 * <pre>
 *
 * Pool&lt;PooledObject&gt; pool = new Pool&lt;PooledObject&gt;(PooledObject.class);
 * ...
 *
 * PooledObject temp = pool.get();
 * ...<i>do something with the object</i>
 * pool.release(temp);
 * </pre>
 * By defaults the implementation is <b>not thread-safe</b>,
 * nevertheless synchronized version of a pool can be created by
 * {@link #synchronize(Pool)} static method. For example,
 * <pre>
 *
 * // Non-synchronized pool
 * Pool&lt;PooledObject&gt; pool = new Pool&lt;PooledObject&gt;(PooledObject.class);
 *
 * // Synchronized pool
 * Pool&lt;PooledObject&gt; pool = Pool.synchronize(new Pool&lt;PooledObject&gt;(PooledObject.class));
 * </pre>
 *
 * @author <a href='mailto:azhukov@wiley.ru'>Alexey Zhukov</a>
 * @version $Revision: 1.2 $
 */
public class Pool<T> {
    /**
     * Interface to a factory for new object creation.
     */
    public static abstract class Factory<T> {
        /**
         * Creates new object. The object must be allocated with operator
         * <code>new</code>.
         *
         * @return new object or null if the object cannot be instantiated.
         */
        public abstract T create();

        /**
         * Cleans internal state of the object before it will be put into
         * the pool. This operation is optional. Default implementation does
         * nothing.
         *
         * @param object the object.
         */
        public void cleanup(T object) {
        }
    }

    /**
     * The object factory.
     */
    private Factory<T> factory;

    /**
     * Pool of used entries.
     */
    private Entry<T> usedEntries;

    /**
     * Pool of free entries.
     */
    private Entry<T> freeEntries;

    /**
     * Default constructor.
     */
    protected Pool() {
    }

    /**
     * Creates new pool.
     *
     * @param clazz the class, which will be used to create new objects.
     * @throws IllegalArgumentException if clazz is null.
     */
    public Pool(final Class<T> clazz) {
        this(new DefaultFactory<T>(clazz));
    }

    /**
     * Creates new pool.
     *
     * @param factory the factory, which will be used to create new objects.
     * @throws IllegalArgumentException if <code>factory</code> is null.
     */
    public Pool(final Factory<T> factory) {
        if (factory == null)
            throw new IllegalArgumentException("The factory cannot be null");
        this.factory = factory;
    }

    /**
     * Returns an object from this pool or newly created one.
     *
     * @return object or null if this pool is empty and object cannot be
     *         instantiated.
     */
    public T get() {
        final T object = getFromPool();
        return object != null ? object : createNew();
    }

    /**
     * Cleans up internal state of the given object and puts it into this pool.
     *
     * @param object the object.
     * @throws IllegalArgumentException if <code>object</code> is null.
     */
    public void release(final T object) {
        if (object == null)
            throw new IllegalArgumentException("The object cannot be null.");
        cleanup(object);
        releaseToPool(object);
    }

    /**
     * Clears this pool.
     */
    public void clear() {
        usedEntries = null;
        freeEntries = null;
    }

    /**
     * Creates synchronized version of the given pool.
     *
     * @param pool the pool to synchronize.
     * @return synchronized version of the given pool.
     * @throws IllegalArgumentException if <code>pool</code> is null.
     */
    public static <V> Pool<V> synchronize(final Pool<V> pool) {
        if (pool == null)
            throw new IllegalArgumentException("Pool cannot be null.");
        return new Pool<V>() {
            @Override
            public final V get() {
                synchronized (pool) {
                    final V object = pool.getFromPool();
                    if (object != null)
                        return object;
                }
                return pool.createNew();
            }

            @Override
            public final void release(final V object) {
                if (object == null)
                    throw new IllegalArgumentException("The object cannot be null.");
                pool.cleanup(object);
                synchronized (pool) {
                    pool.releaseToPool(object);
                }
            }

            @Override
            public final void clear() {
                synchronized (pool) {
                    pool.clear();
                }
            }
        };
    }

    /**
     * Returns object from this pool.
     *
     * @return object or null if this pool is empty.
     */
    protected final T getFromPool() {
        final Entry<T> entry = usedEntries;
        if (entry != null) {
            usedEntries = entry.next;
            final T object = entry.object;
            releaseEntry(entry);
            return object;
        } else
            return null;
    }

    /**
     * Releases the given object into the pool.
     *
     * @param object the object.
     */
    protected final void releaseToPool(final T object) {
        final Entry<T> entry = allocEntry();
        entry.object = object;
        entry.next = usedEntries;
        usedEntries = entry;
    }

    /**
     * Creates new object.
     *
     * @return new object or null if object cannot be instantiated.
     */
    protected final T createNew() {
        return factory.create();
    }

    /**
     * Clears internal state of the object.
     *
     * @param object the object.
     */
    protected final void cleanup(final T object) {
        factory.cleanup(object);
    }

    /**
     * Allocates new entry from the pool of free entries.
     *
     * @return entry from the pool or new entry.
     */
    private Entry<T> allocEntry() {
        final Entry<T> entry = freeEntries;
        if (entry == null)
            return new Entry<T>();
        else {
            freeEntries = entry.next;
            return entry;
        }
    }

    /**
     * Releases the given entry into the pool of free entries and clears the
     * object reference within the entry.
     *
     * @param entry the entry.
     */
    private void releaseEntry(final Entry<T> entry) {
        entry.next = freeEntries;
        entry.object = null;
        freeEntries = entry;
    }

    /**
     * Represents a pool entry.
     */
    private final static class Entry<T> {
        /**
         * The object.
         */
        private T object;

        /**
         * Next entry in a list.
         */
        private Entry<T> next;
    }

    /**
     * Default factory, calling <code>newInstance()</code> method of the given
     * <code>Class</code>.
     */
    private static final class DefaultFactory<T> extends Factory<T> {
        /**
         * The class.
         */
        private final Class<T> clazz;

        /**
         * Creates new factory.
         *
         * @param clazz the class to instantiate objects from.
         * @throws IllegalArgumentException if <code>clazz</code> is null.
         */
        private DefaultFactory(final Class<T> clazz) {
            if (clazz == null)
                throw new IllegalArgumentException("Class cannot be null");
            this.clazz = clazz;
        }

        /**
         * Creates new object. The object must be allocated with operator
         * <code>new</code>.
         *
         * @return new object or null if the object cannot be instantiated.
         */
        public T create() {
            try {
                return clazz.newInstance();
            } catch (Exception e) {
                return null;
            }
        }
    }
}

