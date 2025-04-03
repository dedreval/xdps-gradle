package com.wiley.cms.cochrane.cmanager.data.df;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class DfStorageFactory extends AbstractBeanFactory<IDfStorage> {

    private static final DfStorageFactory INSTANCE = new DfStorageFactory();

    private DfStorageFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("DfStorage", IDfStorage.class));
    }

    public static DfStorageFactory getFactory() {
        return INSTANCE;
    }

}
