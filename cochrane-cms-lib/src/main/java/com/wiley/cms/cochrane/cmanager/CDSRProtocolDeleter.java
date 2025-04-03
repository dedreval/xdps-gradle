package com.wiley.cms.cochrane.cmanager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.db.DbVO;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 25.08.2011
 */
public class CDSRProtocolDeleter {
    public static final String DELETED_FILE_NAME = "CL_deleted_protocols.txt";

    private static final Logger LOG = Logger.getLogger(CDSRProtocolDeleter.class);

    private static IDbStorage dbs = DbStorageFactory.getFactory().getInstance();
    private static IRepository rps = RepositoryFactory.getRepository();
    private static RecordsDeleter rd = new RecordsDeleter();

    private static final RecordsDeleter.DeleterContext CONTEXT = rd.new DeleterContext(
            CochraneCMSPropertyNames.getCDSRDbName(), true,
            FilePathCreator.getFilePathToDeletedRecordsEntire(CochraneCMSPropertyNames.getCDSRDbName()), true, true);

    private CDSRProtocolDeleter() {
    }

    public static Set<String> deleteProtocols(int dbId) throws NamingException {
        LOG.debug("Delete protocols started");

        DbVO dbVO = dbs.getDbVO(dbId);

        int issueId = dbVO.getIssueId();
        IActivityLogService logService = rd.getActivityLogService();
        String logUser = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);

        List<String> recordNames;

        try {
            recordNames = rd.getDeletedProtocolsListFromFile(issueId, CochraneCMSPropertyNames.getCDSRDbName(),
                    DELETED_FILE_NAME);
        } catch (Exception e) {
            LOG.error(e, e);
            logService.error(ActivityLogEntity.EntityLevel.DB, ILogEvent.PROTOCOL_DELETION_FAILED, dbId,
                    dbVO.getTitle(), logUser, e.getMessage());
            return new HashSet<String>();
        }

        Set<String> result = rd.deleteRecords(CONTEXT, dbId, recordNames);

        LOG.debug("Delete protocols finished: " + result.size());
        return result;
    }

    public static void createDeletedProtocolsList(int dbId, List<String> deletedRecords) {

        int issueId = dbs.getDbVO(dbId).getIssueId();

        List<String> recordNames;

        try {
            recordNames = deletedRecords != null ? deletedRecords : getDeletedProtocolsList(issueId);
        } catch (Exception e) {
            LOG.error(e, e);
            return;
        }

        rd.createDeletedRecordsList(CONTEXT, issueId, recordNames, DELETED_FILE_NAME);
    }

    public static List<String> getDeletedProtocolsListFromFile(int issueId) throws IOException {
        return rd.getDeletedProtocolsListFromFile(issueId, CochraneCMSPropertyNames.getCDSRDbName(), DELETED_FILE_NAME);
    }

    private static List<String> getDeletedProtocolsList(int issueId) throws IOException {

        final String reviewTag = "<REVIEW ";
        final String cdNumberAtr = "CD_NUMBER=\"";
        final String stageAtr = "STAGE=\"";
        final String statusAtr = "STATUS=\"";

        String pathToInput = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                + "/" + issueId + "/" + CochraneCMSPropertyNames.getCDSRDbName() + "/input";

        List<String> result = new ArrayList<String>();

        File[] files = rps.getFilesFromDir(pathToInput);
        if (files == null) {
            result.add("");
            return result;
        }
        for (File file : files) {

            if (!file.isDirectory()) {
                continue;
            }

            String metadataPath = pathToInput + "/" + file.getName() + "/metadata.xml";

            if (!rps.isFileExists(metadataPath)) {
                LOG.error("Metadata for CDSR not exist: " + metadataPath);
                continue;
            }

            InputStream is = rps.getFile(metadataPath);

            String xml = InputUtils.readStreamToString(is);

            int cur = 0;
            while (xml.indexOf(reviewTag, cur) != -1) {
                int reviewStart = xml.indexOf(reviewTag, cur);
                int cdNumberStart = xml.indexOf(cdNumberAtr, reviewStart);
                int cdNumberEnd = xml.indexOf("\"", cdNumberStart + cdNumberAtr.length());
                int stageStart = xml.indexOf(stageAtr, reviewStart);
                int stageEnd = xml.indexOf("\"", stageStart + stageAtr.length());
                int statusStart = xml.indexOf(statusAtr, reviewStart);
                int statusEnd = xml.indexOf("\"", statusStart + statusAtr.length());
                String recordName = xml.substring(cdNumberStart + cdNumberAtr.length(), cdNumberEnd);
                String reviewType = xml.substring(stageStart + stageAtr.length(), stageEnd);
                String status = xml.substring(statusStart + statusAtr.length(), statusEnd);

                if (status.equals("DELETED") && RecordMetadataEntity.isStageP(reviewType)) {
                    result.add(recordName);
                }

                cur = statusEnd;
            }
        }

        return result;
    }
}