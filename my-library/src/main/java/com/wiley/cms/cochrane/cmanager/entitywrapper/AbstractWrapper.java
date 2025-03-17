package com.wiley.cms.cochrane.cmanager.entitywrapper;

import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.df.DfStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.df.IDfStorage;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.IPublishService;
import com.wiley.cms.cochrane.cmanager.publish.PublishServiceFactory;
import com.wiley.cms.cochrane.process.IOperationManager;
import com.wiley.cms.cochrane.process.OperationManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.process.IProcessStorage;
import com.wiley.cms.process.ProcessStorageFactory;

import java.io.IOException;
import java.util.List;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

public abstract class AbstractWrapper {

    public static IResultsStorage getResultStorage() {
        return ResultStorageFactory.getFactory().getInstance();
    }

    public static IEntireDBStorage getEntireDbStorage() {
        return EntireDBStorageFactory.getFactory().getInstance();
    }

    public static IRecordStorage getRecordResultStorage() {
        return RecordStorageFactory.getFactory().getInstance();
    }

    public static IProcessStorage getProcessStorage() {
        return ProcessStorageFactory.getFactory().getInstance();
    }

    protected static IDfStorage getDfStorage() {
        return DfStorageFactory.getFactory().getInstance();
    }

    protected static IDbStorage getDbStorage() {
        return DbStorageFactory.getFactory().getInstance();
    }
    protected static IOperationManager getOperationManager() {
        return OperationManager.Factory.getFactory().getInstance();
    }

    protected static void reloadResultStorage() {
        ResultStorageFactory.getFactory().reload();
    }

    protected static IPublishService getPublishService() {
        return PublishServiceFactory.getFactory().getInstance();
    }

    protected static IRepository getRepository() {
        return RepositoryFactory.getRepository();
    }

    protected void tryAddAddonFile(String path, String name, List<AddonFile> list) throws IOException {
        IRepository repository = getRepository();

        if (repository.isFileExists(path)) {
            list.add(new AddonFile(path, name));
        }
    }
}
