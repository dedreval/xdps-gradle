package com.wiley.cms.cochrane.cmanager.data.issue;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class IssueStorageFactory extends AbstractBeanFactory<IIssueStorage> {

    private static final IssueStorageFactory INSTANCE = new IssueStorageFactory();

    private IssueStorageFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("IssueStorage", IIssueStorage.class));
    }

    public static IssueStorageFactory getFactory() {
        return INSTANCE;
    }
}
