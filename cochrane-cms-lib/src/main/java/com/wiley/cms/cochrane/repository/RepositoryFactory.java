package com.wiley.cms.cochrane.repository;

import com.wiley.tes.util.Logger;

/**
 * Repository factory.
 *
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @version 1.0
 */
public class RepositoryFactory {
    private static final Logger LOG = Logger.getLogger(RepositoryFactory.class);

    private static IRepository instance = null;

    private RepositoryFactory() {

    }

    public static IRepository getRepository() {
        synchronized (RepositoryFactory.class) {
            if (instance == null) {
                refresh();
            }
        }
        return instance;
    }

    public static void refresh() {
        LOG.debug("Repository initiated");
        instance = new RepositoryImpl();
    }
}
