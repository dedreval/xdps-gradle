package com.wiley.tes.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 1/24/2018
 */
public final class DbUtils {
    private DbUtils() {
    }

    public static boolean exists(Number id) {
        return id != null && exists(id.longValue());
    }

    public static boolean exists(long id) {
        return id > DbEntity.NOT_EXIST_ID;
    }

    public static boolean isOneCommit(int size) {
        return size <= DbConstants.DB_QUERY_VARIABLE_COUNT;
    }

    public static void commitListByIds(List<Integer> ids, ICollectionCommitter<Integer> committer) {
        int size = ids.size();
        int commitSize = committer.commitSize() > 0 ? committer.commitSize() : DbConstants.DB_QUERY_VARIABLE_COUNT;
        int ind = 0;
        while (ind < size) {
            int start = ind;
            ind += commitSize;
            committer.commit(ids.subList(start, ind > size ? size : ind));
        }
    }

    public static void commitListByNames(List<String> ids, ICollectionCommitter<String> committer) {
        int size = ids.size();
        int commitSize = committer.commitSize() > 0 ? committer.commitSize() : DbConstants.DB_QUERY_VARIABLE_COUNT;
        int ind = 0;
        while (ind < size) {
            int start = ind;
            ind += commitSize;
            committer.commit(ids.subList(start, ind > size ? size : ind));
        }
    }

    /**
     * Simple accumulator to perform some update DB operations within collection
     */
    public static class DbCommitter extends CollectionCommitter<Integer>  {

        private final Consumer<Collection<Integer>> committer;
        private int allCommitted;

        public DbCommitter(Consumer<Collection<Integer>> committer) {
            this(committer, new HashSet<>());
        }

        private DbCommitter(Consumer<Collection<Integer>> committer, Collection<Integer> toCommit) {
            super(DbConstants.DB_QUERY_VARIABLE_COUNT, toCommit);
            this.committer = committer;
        }

        @Override
        public void commit(Collection<Integer> list) {
            committer.accept(list);
            allCommitted += list.size();
        }

        public final int allCommitted() {
            return allCommitted;
        }

        @Override
        public void commitLast() {

            if (toCommit.size() <= maxCommitSize()) {
                super.commitLast();
                return;
            }

            if (toCommit instanceof List) {
                commitListByIds(((List<Integer>) toCommit), this);
                toCommit.clear();
            } else {
                List<Integer> list = new ArrayList<>(toCommit);
                toCommit.clear();
                commitListByIds(list, this);
            }
        }
    }
}
