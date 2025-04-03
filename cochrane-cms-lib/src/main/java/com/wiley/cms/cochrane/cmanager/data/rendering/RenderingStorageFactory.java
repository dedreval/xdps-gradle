package com.wiley.cms.cochrane.cmanager.data.rendering;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class RenderingStorageFactory extends AbstractBeanFactory<IRenderingStorage> {

    private static final RenderingStorageFactory INSTANCE = new RenderingStorageFactory();

    private RenderingStorageFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("RenderingStorage", IRenderingStorage.class));
    }

    public static RenderingStorageFactory getFactory() {
        return INSTANCE;
    }
}
