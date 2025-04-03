package com.wiley.cms.cochrane.cmanager.entitymanager;

import com.wiley.cms.cochrane.activitylog.ActivityLogFactory;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IIssueStorage;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.export.data.ExportStorageFactory;
import com.wiley.cms.cochrane.cmanager.export.data.IExportStorage;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.converter.services.ConversionProcessFactory;
import com.wiley.cms.converter.services.IConversionProcess;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
public class AbstractManager {

    private AbstractManager() {

    }

    public static IResultsStorage getResultStorage() {
        return ResultStorageFactory.getFactory().getInstance();
    }

    public static IRepository getRepository() {
        return RepositoryFactory.getRepository();
    }

    public static IDbStorage getResultDbStorage() {
        return DbStorageFactory.getFactory().getInstance();
    }

    public static IRecordStorage getRecordStorage() {
        return RecordStorageFactory.getFactory().getInstance();
    }

    public static IEntireDBStorage getEntireDBStorage() {
        return EntireDBStorageFactory.getFactory().getInstance();
    }

    public static IExportStorage getExportStorage() {
        return ExportStorageFactory.getFactory().getInstance();
    }

    public static IIssueStorage getIssueStorage() {
        return IssueStorageFactory.getFactory().getInstance();
    }

    public static IConversionProcess getConversionProcess() {
        return ConversionProcessFactory.getFactory().getInstance();
    }

    public static IActivityLogService getActivityLogService() {
        return ActivityLogFactory.getFactory().getInstance();
    }
}
