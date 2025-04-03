package com.wiley.cms.cochrane.cmanager.export.data;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.process.AbstractBeanFactory;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class ExportStorageFactory extends AbstractBeanFactory<IExportStorage> {

    private static final ExportStorageFactory INSTANCE = new ExportStorageFactory();

    private ExportStorageFactory() {
        super(CochraneCMSPropertyNames.buildLookupName("ExportStorage", IExportStorage.class));
    }

    public static ExportStorageFactory getFactory() {
        return INSTANCE;
    }
}
