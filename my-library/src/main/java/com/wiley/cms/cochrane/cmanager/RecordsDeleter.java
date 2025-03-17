package com.wiley.cms.cochrane.cmanager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.IActivityLogService;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.cmanager.data.DatabaseEntity;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.StatsUpdater;
import com.wiley.cms.cochrane.cmanager.data.db.DbStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.db.DbVO;
import com.wiley.cms.cochrane.cmanager.data.db.IDbStorage;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecordStorage;
import com.wiley.cms.cochrane.cmanager.data.record.RecordStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.rendering.RenderingPlan;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.entitywrapper.ResultStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:svyatoslav.gulin@gmail.com'>Svyatoslav Gulin</a>
 * @version 08.12.2011
 */
public class RecordsDeleter {
    private static final Logger LOG = Logger.getLogger(RecordsDeleter.class);

    private static IEntireDBStorage edbs = EntireDBStorageFactory.getFactory().getInstance();
    private static IRecordStorage rs = RecordStorageFactory.getFactory().getInstance();
    private static IDbStorage dbs = DbStorageFactory.getFactory().getInstance();
    private static IRepository rps = RepositoryFactory.getRepository();
    private static IResultsStorage ress = ResultStorageFactory.getFactory().getInstance();
    private static final String XML_EXT = ".xml";
    private static final String SRC_DIR = "/src/";
    //private static final String REL000_DIR_PREFIX = "/rel000";
    private static final String FAILED_DELETE_WML3G_MSG_TEMP = "Failed to delete WML3G %s from [%s] to [%s], %s.\n";

    public RecordsDeleter() {
    }

    public Set<String> getDeletedRecordsList(DeleterContext context, int issueId, String fileName) {

        Set<String> ret = new HashSet<String>();
        String filePath = getFilePath(context, issueId, fileName);
        try {
            if (rps.isFileExists(filePath)) {
                InputStream is = rps.getFile(filePath);
                String str = InputUtils.readStreamToString(is);
                String[] strs = str.split("\n");
                ret.addAll(Arrays.asList(strs));
                is.close();
            }
        } catch (Exception ex) {
            LOG.error(ex);
        }
        return ret;
    }

    public InputStream getDeletedRecordsBody(DeleterContext context, int issueId, String fileName) {
        String filePath = getFilePath(context, issueId, fileName);
        try {
            if (rps.isFileExists(filePath)) {
                return rps.getFile(filePath);
            }

        } catch (Exception ex) {
            LOG.error(ex);
        }
        return null;
    }

    public boolean isDeletedRecordsListExists(DeleterContext context, int issueId, String fileName) {
        String filePath = getFilePath(context, issueId, fileName);
        try {
            if (rps.isFileExists(filePath)) {
                InputStream is = rps.getFile(filePath);
                String str = InputUtils.readStreamToString(is);
                return str.trim().length() > 0;
            }

        } catch (Exception ex) {
            LOG.error(ex);
        }
        return false;
    }

    public void createDeletedRecordsList(DeleterContext context, int issueId, Collection<String> recordNames,
        String fileName) {

        String filePath = getFilePath(context, issueId, fileName);
        try {
            rps.putFile(filePath, createDeletedRecordsInputStream(recordNames));
        } catch (Exception e) {
            LOG.error(e, e);
        }
    }

    public InputStream createDeletedRecordsInputStream(Collection<String> recordNames) {
        StringBuilder res = new StringBuilder();
        boolean first = true;
        for (String name : recordNames) {
            if (first) {
                first = false;
                res.append(name);
            } else {
                res.append("\n").append(name);
            }
        }
        return new ByteArrayInputStream(res.toString().trim().getBytes());
    }

    private String getFilePath(DeleterContext context, int issueId,  String fileName) {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
            + "/" + issueId + "/" + context.getDbName() + "/" + fileName;
    }

    Set<String> deleteRecords(DeleterContext context, int dbId, List<String> recordNames) {

        Set<String> result = new HashSet<>();

        DbVO dbVO = dbs.getDbVO(dbId);
        BaseType bt = BaseType.find(dbVO.getTitle()).get();

        Integer issueId = dbVO.getIssueId();
        IActivityLogService logService = getActivityLogService();
        String logUser = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.ACTIVITY_LOG_SYSTEM_NAME);

        StringBuilder warnings = new StringBuilder();
        StringBuilder logger = new StringBuilder("<br><b>Details</b>:<ul>");

        String dbName = context.getDbName();
        DatabaseEntity database = ress.getDatabaseEntity(dbName);
        int failed = 0;
        for (String recordName : recordNames) {
            RecordHelper.makeBackUp(bt, issueId, recordName, rps);

            if (edbs.isRecordExists(database.getId(), 0, recordName)) {
                entireDelete(context, logger, warnings, recordName);
            }
            if (rs.isRecordExists(dbId, recordName)) {
                failed = issueDelete(logger, warnings, recordName, dbId, issueId, context) ? failed : ++failed;
                result.add(recordName);
            }
        }

        dbs.updateRecordCount(dbId);
        StatsUpdater.onUpdate(dbName);

        logger.append("</ul>");

        if (!result.isEmpty()) {
            logService.info(ActivityLogEntity.EntityLevel.DB, ILogEvent.PROTOCOL_DELETION_SUCCESS, dbId,
                    dbVO.getTitle(), logUser, "" + result.size());
        }

        if (failed > 0) {
            logService.error(ActivityLogEntity.EntityLevel.DB, ILogEvent.PROTOCOL_DELETION_FAILED, dbId,
                    dbVO.getTitle(), logUser, "" + failed);
        }
        return result;
    }

    public List<String> getDeletedProtocolsListFromFile(int issueId, String dbName, String fileName)
        throws IOException {

        String filePath =
                CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + issueId
                        + "/" + dbName + "/" + fileName;

        List<String> result = new ArrayList<String>();
        if (!rps.isFileExists(filePath)) {
            return result;
        }

        InputStream is = rps.getFile(filePath);
        String s = InputUtils.readStreamToString(is);
        if (s.length() > 0) {
            String[] recordNames = s.split("\n");
            result.addAll(Arrays.asList(recordNames));
        }

        return result;
    }

    private boolean entireDelete(DeleterContext context, StringBuilder logger, StringBuilder warnings,
                                 String recordName) {
        try {
            deleteFromEntire(context, recordName);
            edbs.remove(context.getDbName(), recordName);

            if (context.isHasPrevious()) {
                deleteFromPrevious(recordName);
            }

            logger.append("<li>Record has been deleted from entire DB: ").append(recordName);
        } catch (Exception e) {
            LOG.error(e, e);
            warnings.append("Failed to delete next record from Entire DB: ").append(recordName).append("\n").append(
                    e.getMessage()).append("\n");
            return false;
        }
        return true;
    }

    public IActivityLogService getActivityLogService() {
        return AbstractManager.getActivityLogService();
    }


    private void deleteFromEntire(DeleterContext context, String recordName) throws Exception {
        String filePath = FilePathCreator.getFilePathToSourceEntire(context.getDbName(), recordName);
        if (!rps.isFileExists(filePath)) {
            return;
        }

        InputStream is = rps.getFile(filePath);

        String prefixForDeleted = context.getFilePathToDeletedRecords();
        String pathTo = FilePathCreator.addRecordNameToPath(prefixForDeleted + "/src", recordName,
                context.getDbName()) + XML_EXT;
        String pathFrom;

        rps.putFile(pathTo, is);
        rps.deleteFile(filePath);

        /* deleting WML3G content */
        if (CmsUtils.isConversionTo3gAvailable(context.getDbName())) {
            deleteWml3gContent(Constants.UNDEF, context.getDbName(), recordName, ContentLocation.ENTIRE,
                    prefixForDeleted);
        }

        String dirPath = filePath.replace(XML_EXT, "");
        File[] files = rps.getFilesFromDir(dirPath);

        if (files != null && files.length > 0) {
            pathTo = prefixForDeleted + SRC_DIR + recordName;
            CmsUtils.writeDir(dirPath, pathTo);
            deleteDir(dirPath);
        }

        if (context.isPdfExists()) {
            moveRenderingToDeletedDir(recordName, context.getDbName(), RenderingPlan.PDF_TEX, prefixForDeleted);
            moveRenderingToDeletedDir(recordName, context.getDbName(), RenderingPlan.PDF_FOP, prefixForDeleted);
        }

        moveRenderingToDeletedDir(recordName, context.getDbName(), RenderingPlan.HTML, prefixForDeleted);
    }

    private void moveRenderingToDeletedDir(String recName, String dbName, RenderingPlan rndPlan, String deletedDir)
            throws Exception {
        String pathFrom = FilePathCreator.getRenderedDirPathEntire(dbName, recName, rndPlan);
        if (!rps.isFileExists(pathFrom)) {
            return;
        }

        String rndDir = "/" + FilePathCreator.getRenderingPlanDirName(rndPlan, false) + "/";
        String pathTo = deletedDir + rndDir + recName;
        CmsUtils.writeDir(pathFrom, pathTo);
        deleteDir(pathFrom);
    }


    private void deleteWml3gContent(int issueId,
                                    String dbName,
                                    String recName,
                                    ContentLocation contentLocation,
                                    String pathPrefix) {
        StringBuilder errs = new StringBuilder();

        String srcUri;
        String destUri;
        if (contentLocation == ContentLocation.ISSUE) {
            srcUri = FilePathCreator.getFilePathForMl3gXml(issueId, dbName, recName);
            destUri = null;
        } else {
            srcUri = FilePathCreator.getFilePathForEntireMl3gXml(dbName, recName);
            destUri = FilePathCreator.addRecordNameToPath(pathPrefix + FilePathCreator.ML3G_DIR, recName, dbName)
                    + Extensions.XML;
        }

        try {
            if (rps.isFileExists(srcUri)) {
                copyContent(srcUri, destUri);
                rps.deleteFile(srcUri);
            }
        } catch (Exception e) {
            errs.append(String.format(FAILED_DELETE_WML3G_MSG_TEMP, Constants.XML_STR, srcUri, destUri, e));
        }

        if (dbName.equals(CochraneCMSPropertyNames.getCDSRDbName())) {
            if (contentLocation == ContentLocation.ISSUE) {
                srcUri = FilePathCreator.getFilePathForMl3gAssets(issueId, dbName, recName);
                destUri = null;
            } else {
                srcUri = FilePathCreator.getFilePathForEntireMl3gAssets(dbName, recName);
                destUri = FilePathCreator.addRecordNameToPath(pathPrefix
                        + FilePathCreator.ML3G_DIR, recName, dbName) + Extensions.ASSETS;
            }

            try {
                if (rps.isFileExists(srcUri)) {
                    copyContent(srcUri, destUri);
                    rps.deleteFile(srcUri);
                }
            } catch (Exception e) {
                errs.append(String.format(FAILED_DELETE_WML3G_MSG_TEMP, Constants.ASSETS_STR, srcUri, destUri, e));
            }
        }

        if (errs.length() > 0) {
            LOG.error(errs);
        }
    }

    private static void copyContent(String srcUri, String destUri) throws IOException {
        if (destUri == null) {
            return;
        }

        InputStream is = rps.getFile(srcUri);
        rps.putFile(destUri, is);
    }

    private void deleteFromPrevious(String recordName) throws Exception {
        List<PrevVO> versions = CochraneCMSBeans.getVersionManager().getVersions(recordName);
        if (versions.isEmpty()) {
            return;
        }
        versions.remove(0);
        for (PrevVO prev : versions) {
            String filePath = FilePathCreator.getPreviousSrcPath(recordName, prev.version);

            if (!rps.isFileExists(filePath)) {
                continue;
            }

            InputStream is = rps.getFile(filePath);

            String prefixForDeletedProtocols = FilePathCreator.getFilePathToDeletedProtocolsPrevious();
            String pathTo = prefixForDeletedProtocols + "/" + FilePathBuilder.buildHistoryDir(prev.version)
                    + SRC_DIR + filePath.substring(filePath.lastIndexOf("/"));

            rps.putFile(pathTo, is);

            String dirPath = filePath.replace(XML_EXT, "");
            File[] files = rps.getFilesFromDir(dirPath);
            if (files != null && files.length > 0) {
                pathTo = prefixForDeletedProtocols + "/" + FilePathBuilder.buildHistoryDir(prev.version)
                        + prev.version + SRC_DIR + recordName;
                CmsUtils.writeDir(dirPath, pathTo);
            }

            copyPreviousRenderingToDir(recordName, prev.version, RenderingPlan.PDF_TEX, prefixForDeletedProtocols);
            copyPreviousRenderingToDir(recordName, prev.version, RenderingPlan.HTML, prefixForDeletedProtocols);
            copyPreviousRenderingToDir(recordName, prev.version, RenderingPlan.PDF_FOP, prefixForDeletedProtocols);
        }
    }

    private void copyPreviousRenderingToDir(String recName, int version, RenderingPlan rndPlan, String baseDestDir)
            throws IOException {
        String rndDir = "/" + FilePathCreator.getRenderingPlanDirName(rndPlan, false) + "/";
        String pathTo = baseDestDir + "/" + FilePathBuilder.buildHistoryDir(version) + rndDir + recName;
        String pathFrom = FilePathCreator.getPreviousPdfPath(recName, version);
        if (rps.isFileExistsQuiet(pathFrom)) {
            CmsUtils.writeDir(pathFrom, pathTo);
        } else {
            LOG.warn("Failed to copy " + pathFrom + " to " + pathTo);
        }
    }

    private boolean issueDelete(StringBuilder logger, StringBuilder warnings, String recordName, int dbId,
                                int issueId, DeleterContext context) {
        try {
            deleteFromIssue(context, issueId, dbId, recordName);
            deleteFromEntire(context, recordName);
            rs.remove(dbId, recordName);
            //dbs.updateRecordCount(dbId);
            logger.append("<li>Record has been deleted from Issue DB: ").append(recordName);
        } catch (Exception e) {
            LOG.error(e, e);
            warnings.append("Failed to delete next record from DB: ").append(recordName).append("\n").append(
                    e.getMessage()).append("\n");
            return false;
        }
        return true;
    }

    private void deleteFromIssue(DeleterContext context, int issueId, int dbId, String recordName) throws Exception {
        List<String> sourceFilePaths = rs.getRecordPathByNameAndIssue(dbId, recordName);
        if ((sourceFilePaths != null) && (sourceFilePaths.size() > 0)) {
            for (String sourceFilePath : sourceFilePaths) {
                deleteFilesFromIssue(context, issueId, recordName, sourceFilePath);
            }
        }
    }

    private void deleteFilesFromIssue(DeleterContext context, int issueId, String recordName, String sourceFilePath)
        throws Exception {

        if (!rps.isFileExists(sourceFilePath)) {
            return;
        }

        rps.deleteFile(sourceFilePath);

        String filePath = sourceFilePath.replace(XML_EXT, "");

        if (context.isSourceCatalogExists()) {
            rps.deleteDir(filePath);
        }

        /* deleting WML3G content */
        if (CmsUtils.isConversionTo3gAvailable(context.getDbName())) {
            deleteWml3gContent(issueId, context.getDbName(), recordName, ContentLocation.ISSUE, null);
        }

        String renderedHtmlPath = FilePathCreator.getRenderedDirPath(filePath, RenderingPlan.HTML);
        deleteDir(renderedHtmlPath);

        String renderedPdfPath = FilePathCreator.getRenderedDirPath(filePath, RenderingPlan.PDF_TEX);
        deleteDir(renderedPdfPath);

        deleteDir(FilePathCreator.getRenderedDirPath(filePath, RenderingPlan.PDF_FOP));
    }

    private boolean deleteDir(String dir) throws Exception {
        boolean deleted  = rps.isFileExists(dir);
        if (deleted) {
            rps.deleteDir(dir);
        }
        return deleted;
    }

    /**
     * @author <a href='mailto:svyatoslav.gulin@gmail.com'>Svyatoslav Gulin</a>
     * @version 25.08.2011
     */
    public class DeleterContext {
        private String dbName;
        private boolean isPdfExists;
        private String filePathToDeletedRecords;
        private boolean hasPrevious;
        private boolean sourceCatalogExists;

        public DeleterContext(String dbName, boolean pdfExists, String filePathToDeletedRecords, boolean hasPrevious,
                              boolean sourceCatalogExists) {
            this.dbName = dbName;
            isPdfExists = pdfExists;
            this.filePathToDeletedRecords = filePathToDeletedRecords;
            this.hasPrevious = hasPrevious;
            this.sourceCatalogExists = sourceCatalogExists;
        }

        public String getDbName() {
            return dbName;
        }

        public boolean isPdfExists() {
            return isPdfExists;
        }

        public String getFilePathToDeletedRecords() {
            return filePathToDeletedRecords;
        }

        public boolean isHasPrevious() {
            return hasPrevious;
        }

        public boolean isSourceCatalogExists() {
            return sourceCatalogExists;
        }
    }

}
