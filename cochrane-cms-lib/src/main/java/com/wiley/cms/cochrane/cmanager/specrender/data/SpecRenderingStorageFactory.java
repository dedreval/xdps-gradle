package com.wiley.cms.cochrane.cmanager.specrender.data;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class SpecRenderingStorageFactory extends AbstractBeanFactory<ISpecRenderingStorage> {

    private static final SpecRenderingStorageFactory INSTANCE = new SpecRenderingStorageFactory();

    private SpecRenderingStorageFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("SpecRenderingStorage", ISpecRenderingStorage.class));
    }

    public static SpecRenderingStorageFactory getFactory() {
        return INSTANCE;
    }

}
