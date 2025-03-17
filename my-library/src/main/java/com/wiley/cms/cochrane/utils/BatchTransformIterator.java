package com.wiley.cms.cochrane.utils;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 29.01.2019
 *
 * @param <I> the type of the elements returned by the source iterator
 * @param <R> the type of the elements returned by this iterator
 */
public class BatchTransformIterator<I, R> implements Iterator<R> {
    private final Iterator<I> srcDataIt;
    private final Function<List<I>, List<R>> transformer;
    private final int batchSize;
    private Iterator<R> resultDataIt;

    public BatchTransformIterator(Iterator<I> srcDataIt, Function<List<I>, List<R>> transformer) {
        this(srcDataIt, transformer, CochraneCMSPropertyNames.getDbRecordBatchSize());
    }

    public BatchTransformIterator(Iterator<I> srcDataIt, Function<List<I>, List<R>> transformer, int batchSize) {
        this.srcDataIt = srcDataIt;
        this.transformer = transformer;
        this.batchSize = batchSize;
        this.resultDataIt = Collections.emptyIterator();
    }

    @Override
    public boolean hasNext() {
        while (!resultDataIt.hasNext() && srcDataIt.hasNext()) {
            List<I> srcData = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize && srcDataIt.hasNext(); i++) {
                srcData.add(srcDataIt.next());
            }
            resultDataIt = transformer.apply(srcData).iterator();
        }
        return resultDataIt.hasNext();
    }

    @Override
    public R next() {
        return resultDataIt.next();
    }
}
