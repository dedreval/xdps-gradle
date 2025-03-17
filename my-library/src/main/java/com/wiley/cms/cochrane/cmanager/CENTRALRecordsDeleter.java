package com.wiley.cms.cochrane.cmanager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.db.DbVO;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:svyatoslav.gulin@gmail.com'>Svyatoslav Gulin</a>
 * @version 25.08.2011
 */
public final class CENTRALRecordsDeleter {
    public static final String DELETED_FILE_NAME = "CL_deleted_withdrawn_records.txt";

    private static final Logger LOG = Logger.getLogger(CENTRALRecordsDeleter.class);
    private static final String CENTRAL = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL);

    private static IRecordStorage rs = RecordStorageFactory.getFactory().getInstance();
    private static IDbStorage dbs = DbStorageFactory.getFactory().getInstance();
    private static RecordsDeleter rd = new RecordsDeleter();

    private static final RecordsDeleter.DeleterContext CONTEXT = rd.new DeleterContext(CENTRAL, false,
        FilePathCreator.getFilePathToDeletedRecordsEntire(CENTRAL), false, false);

    private CENTRALRecordsDeleter() {
    }

    public static boolean isDeletedListExists(Integer issueId) {
        return issueId != null && rd.isDeletedRecordsListExists(CONTEXT, issueId, DELETED_FILE_NAME);
    }

    public static InputStream getDeletedRecordsBody(int issueId) {
        return rd.getDeletedRecordsBody(CONTEXT, issueId, DELETED_FILE_NAME);
    }

    public static InputStream getDeletedRecordsBody(Collection<String> recordNames) {
        return rd.createDeletedRecordsInputStream(recordNames);
    }

    public static List<String> getDeletedRecordsListFromFile(int issueId) throws IOException {
        return rd.getDeletedProtocolsListFromFile(issueId, CENTRAL, DELETED_FILE_NAME);
    }

    public static Set<String> deleteRecords(Integer issueId) {
        LOG.debug("Delete records started");

        DbVO dbVO = dbs.getDbVOByNameAndIssue(CENTRAL, issueId);

        IActivityLogService logService = rd.getActivityLogService();
        String logUser = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);

        List<String> recordNames;

        try {
            recordNames = rd.getDeletedProtocolsListFromFile(issueId, CENTRAL, DELETED_FILE_NAME);
        } catch (Exception e) {
            LOG.error(e, e);
            logService.error(ActivityLogEntity.EntityLevel.DB, ILogEvent.PROTOCOL_DELETION_FAILED, dbVO.getId(),
                    dbVO.getTitle(), logUser, e.getMessage());
            return Collections.emptySet();
        }

        Set<String> result = rd.deleteRecords(CONTEXT, dbVO.getId(), recordNames);

        LOG.debug("Delete records finished: " + result.size());
        return result;
    }

    public static List<String> createDeletedRecordsList(int issueId, int dbId, int dfId) {

        Set<String> recordNames = rd.getDeletedRecordsList(CONTEXT, issueId, DELETED_FILE_NAME);
        List<String> currentDeleted = getDeletedRecordsList(dbId, dfId);
        try {
            recordNames.addAll(currentDeleted);
        } catch (Exception e) {
            LOG.error(e, e);
            return Collections.emptyList();
        }
        rd.createDeletedRecordsList(CONTEXT, issueId, recordNames, DELETED_FILE_NAME);
        return currentDeleted;
    }

    private static List<String> getDeletedRecordsList(int dbId, int dfId) {
        return rs.getWithdrawnRecords(dbId, dfId);
    }
}
