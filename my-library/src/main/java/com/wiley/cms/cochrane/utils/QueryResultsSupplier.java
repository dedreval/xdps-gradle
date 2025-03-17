package com.wiley.cms.cochrane.utils;

import java.util.List;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 07.02.2019
 *
 * @param <T> the type of results supplied by this supplier
 */
@FunctionalInterface
public interface QueryResultsSupplier<T> {
    List<T> get(int offset, int limit);
}
