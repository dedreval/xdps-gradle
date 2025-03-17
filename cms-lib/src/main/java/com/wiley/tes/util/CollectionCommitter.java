package com.wiley.tes.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/26/2018
 *
 * @param <T> Object to commit
 */
public abstract class CollectionCommitter<T> implements ICollectionCommitter<T> {

    protected final Collection<T> toCommit;
    private final int maxCommitSize;

    public CollectionCommitter() {
        this(DbConstants.DB_QUERY_VARIABLE_COUNT, new ArrayList<>());
    }

    protected CollectionCommitter(int maxCommitSize, Collection<T> toCommit) {
        this.maxCommitSize = maxCommitSize;
        this.toCommit = toCommit;
    }

    public static <O> Collection<O> addToNullableCollection(O obj, Collection<O> collection,
                                                            Supplier<Collection<O>> supplier) {
        Collection<O> ret = collection == null ? supplier.get() : collection;
        ret.add(obj);
        return ret;
    }

    public final boolean commit(T obj) {

        toCommit.add(obj);
        if (toCommit.size() == maxCommitSize()) {
            commit();
            return true;
        }
        return false;
    }

    public void commitCollection(Collection<T> collection) {
        collection.forEach(this::commit);
    }

    public void commitAll(Collection<T> collection) {
        commitCollection(collection);
        commitLast();
    }

    public void commitLast() {
        if (toCommit.isEmpty()) {
            return;
        }
        commit();
    }
    
    protected final int maxCommitSize() {
        return maxCommitSize;
    }

    protected void commit() {
        commit(toCommit);
        toCommit.clear();
    }
}
