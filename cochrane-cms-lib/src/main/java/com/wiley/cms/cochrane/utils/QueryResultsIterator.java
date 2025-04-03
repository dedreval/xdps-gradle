package com.wiley.cms.cochrane.utils;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 07.02.2019
 *
 * @param <E> the type of elements returned by this iterator
 */
public class QueryResultsIterator<E> implements Iterator<E> {
    private final QueryResultsSupplier<E> supplier;
    private Iterator<E> lastObtained = Collections.emptyIterator();
    private int offset;
    private final int limit;

    public QueryResultsIterator(QueryResultsSupplier<E> supplier) {
        this.supplier = supplier;
        this.limit = CochraneCMSPropertyNames.getDbRecordBatchSize();
    }

    @Override
    public boolean hasNext() {
        if (!lastObtained.hasNext()) {
            List<E> results = supplier.get(offset, limit);
            offset += results.size();
            lastObtained = results.iterator();
        }
        return lastObtained.hasNext();
    }

    @Override
    public E next() {
        return lastObtained.next();
    }
}
