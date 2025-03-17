package com.wiley.cms.cochrane.cmanager.data.db;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class DbStorageFactory extends AbstractBeanFactory<IDbStorage> {

    private static final DbStorageFactory INSTANCE = new DbStorageFactory();

    private DbStorageFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("DbStorage", IDbStorage.class));
    }

    public static DbStorageFactory getFactory() {
        return INSTANCE;
    }
}
