package com.wiley.cms.cochrane.cmanager.data.meshterm;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 03.11.2009
 */
public class MeshtermStorageFactory extends AbstractBeanFactory<IMeshtermStorage>{

    private static final MeshtermStorageFactory INSTANCE = new MeshtermStorageFactory();

    private MeshtermStorageFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("MeshtermStorage", IMeshtermStorage.class));
    }

    public static MeshtermStorageFactory getFactory() {
        return INSTANCE;
    }

}
