package com.wiley.cms.cochrane.cmanager.publish;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class PublishServiceFactory extends AbstractBeanFactory<IPublishService> {

    private static final PublishServiceFactory INSTANCE = new PublishServiceFactory();

    private PublishServiceFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("PublishService", IPublishService.class));
    }

    public static PublishServiceFactory getFactory() {
        return INSTANCE;
    }
}
