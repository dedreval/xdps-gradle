package com.wiley.cms.cochrane.cmanager.contentworker;

import java.util.ArrayList;

import com.wiley.tes.util.Logger;

/**
 * @author Sergey Trofimov
 */
public class WorkerFactory {

    public static final ArrayList<Object[]> PACKAGE_REGEXP_LIST = new ArrayList<Object[]>();
    private static final Logger LOG = Logger.getLogger(WorkerFactory.class);

    static {
        PACKAGE_REGEXP_LIST.add(new Object[]{
            "cca_\\d{4}_\\d\\d?[a-zA-Z\\d\\._\\-\\(\\)]*\\.zip", CCAWorker.class
        });
        PACKAGE_REGEXP_LIST.add(new Object[]{
            "[^_]+_\\d+_\\d+.*\\.zip", IssueWorker.class
        });
        PACKAGE_REGEXP_LIST.add(new Object[]{
            "crgs[\\w-]+_aries_jats\\.zip", AriesWorker.class
        });
        PACKAGE_REGEXP_LIST.add(new Object[]{
            "(crgs|noncrgs|translations)[\\w-]+\\.zip", RevmanWorker.class
        });
    }

    private WorkerFactory() {
    }

    public static boolean matches(String name) {
        for (Object[] entry : PACKAGE_REGEXP_LIST) {
            if (name.matches((String) entry[0])) {
                return true;
            }
        }
        return false;
    }

    public static AbstractWorker createWorkerByPackageName(String name) {
        for (Object[] entry : PACKAGE_REGEXP_LIST) {
            if (name.matches((String) entry[0])) {
                try {
                    return (AbstractWorker) ((Class) entry[1]).newInstance();
                } catch (InstantiationException e) {
                    LOG.error(e, e);
                } catch (IllegalAccessException e) {
                    LOG.error(e, e);
                }
            }
        }
        return null;
    }
}