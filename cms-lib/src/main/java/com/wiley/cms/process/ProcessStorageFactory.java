package com.wiley.cms.process;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class ProcessStorageFactory extends AbstractBeanFactory<IProcessStorage> {

    private static final ProcessStorageFactory INSTANCE = new ProcessStorageFactory();

    private ProcessStorageFactory() {
        super("CMS", "ProcessStorage", IProcessStorage.class);
    }

    public static ProcessStorageFactory getFactory() {
        return INSTANCE;
    }
}
