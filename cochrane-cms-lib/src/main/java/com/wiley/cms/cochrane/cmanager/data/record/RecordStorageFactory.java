package com.wiley.cms.cochrane.cmanager.data.record;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class RecordStorageFactory extends AbstractBeanFactory<IRecordStorage> {

    private static final RecordStorageFactory INSTANCE = new RecordStorageFactory();

    private RecordStorageFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("RecordStorage", IRecordStorage.class));
    }

    public static RecordStorageFactory getFactory() {
        return INSTANCE;
    }
}
