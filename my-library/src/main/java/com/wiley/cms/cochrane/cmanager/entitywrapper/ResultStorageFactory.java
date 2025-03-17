package com.wiley.cms.cochrane.cmanager.entitywrapper;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class ResultStorageFactory extends AbstractBeanFactory<IResultsStorage> {

    private static final ResultStorageFactory INSTANCE = new ResultStorageFactory();

    private ResultStorageFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("ResultsStorage", IResultsStorage.class));
    }

    public static ResultStorageFactory getFactory() {
        return INSTANCE;
    }
}
