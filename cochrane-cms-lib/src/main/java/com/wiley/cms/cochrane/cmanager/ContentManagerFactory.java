package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.services.IContentManager;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 8/9/2016
 */
public class ContentManagerFactory extends AbstractBeanFactory<IContentManager> {
    private static final ContentManagerFactory INSTANCE = new ContentManagerFactory();

    private ContentManagerFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("ContentManager", IContentManager.class));
    }

    public static ContentManagerFactory getFactory() {
        return INSTANCE;
    }
}
