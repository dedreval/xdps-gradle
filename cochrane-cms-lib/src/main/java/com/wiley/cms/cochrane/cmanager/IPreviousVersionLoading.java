package com.wiley.cms.cochrane.cmanager;

import java.util.Collection;
import java.util.Map;

import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.res.BaseType;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public interface IPreviousVersionLoading {
    Map<String, String> handlePreviousVersions(BaseType baseType, Collection<? extends IRecord> records,
                                               boolean dashBoard);

    void handlePreviousVersions(BaseType baseType, IRecord record, boolean dashboard);
}
