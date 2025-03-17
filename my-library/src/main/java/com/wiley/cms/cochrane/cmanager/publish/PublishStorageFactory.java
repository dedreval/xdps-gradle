package com.wiley.cms.cochrane.cmanager.publish;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 6/15/2016
 */
public class PublishStorageFactory extends AbstractBeanFactory<IPublishStorage> {

    private static final PublishStorageFactory INSTANCE = new PublishStorageFactory();

    private PublishStorageFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("PublishStorage", IPublishStorage.class));
    }

    public static PublishStorageFactory getFactory() {
        return INSTANCE;
    }
}
