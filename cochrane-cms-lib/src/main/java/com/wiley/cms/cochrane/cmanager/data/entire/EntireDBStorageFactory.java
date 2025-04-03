package com.wiley.cms.cochrane.cmanager.data.entire;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 13.11.2009
 */
public class EntireDBStorageFactory extends AbstractBeanFactory<IEntireDBStorage> {

    private static final EntireDBStorageFactory INSTANCE = new EntireDBStorageFactory();

    private EntireDBStorageFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("EntireDBStorage", IEntireDBStorage.class));
    }

    public static EntireDBStorageFactory getFactory() {
        return INSTANCE;
    }
}
