package com.wiley.cms.cochrane.cmanager.entitywrapper;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.archiver.Archiver;
import com.wiley.cms.cochrane.authentication.IVisit;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.data.IResultsStorage;
import com.wiley.cms.cochrane.cmanager.data.IssueEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBStorageFactory;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueStorageFactory;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.medlinedownloader.IMedlineRequest;
import com.wiley.cms.cochrane.medlinedownloader.MedlineDownloaderParameters;
import com.wiley.cms.cochrane.medlinedownloader.meshtermdownloader.MeshtermDownloaderRequest;
import com.wiley.cms.cochrane.process.OperationManager;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.IRepositorySessionFacade;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.repository.RepositoryUtils;
import com.wiley.cms.cochrane.term2num.Term2NumHelper;
import com.wiley.cms.process.jms.JMSSender;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnectionFactory;
import javax.jms.Session;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.wiley.cms.cochrane.cmanager.contentworker.CentralDownloader.resetCentralDownloaderStatus;

/**
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */

public class IssueWrapper extends AbstractWrapper implements java.io.Serializable {
    private static final Logger LOG = Logger.getLogger(IssueWrapper.class);
    private static final String CREATE_ISSUE_ASSETS_SETTING_FAILED = "Create issue: assets setting failed";

    private IssueEntity entity;

    public IssueWrapper() {
        entity = new IssueEntity();
    }

    public IssueWrapper(int issueId) {
        askEntity(issueId);
    }

    public IssueWrapper(IssueEntity entity) {
        this.entity = entity;
    }

    public void createIssue(String title, int number, int year, Date publishDate) {

        int issueId = createIssueDb(title, number, year, publishDate);
        //setAssets();
        askEntity(issueId);
    }

    public static synchronized int createIssueDb(String title, int number, int year, Date publishDate) {
        IResultsStorage rs = getResultStorage();
        int issueId = rs.findIssue(year, number);
        if (!DbUtils.exists(issueId)) {
            issueId = rs.createIssue(title, Now.getNowUTC().getTime(), number, year, publishDate);
            try {
                createDbs(issueId);
                CochraneCMSPropertyNames.lookupRecordCache().refreshLastDatabases();
                resetCentralDownloaderStatus();
            } catch (Exception e) {
                LOG.error("Create issue: create dbs failed", e);
            }

            RepositoryUtils.createDirectory(FilePathBuilder.getPathToIssue(issueId));
            createInputToIssue(issueId);
        }

        return issueId;
    }

    private static void createInputToIssue(int issueId) {

        IRepository rps = RepositoryFactory.getRepository();

        String dbName = CochraneCMSPropertyNames.getCDSRDbName();
        String inputDir = FilePathCreator.getInputDir(issueId, dbName);
        String revmanDirPath = FilePathCreator.getDirPathForRevmanEntire();

        File[] groups = rps.getFilesFromDir(revmanDirPath);
        if (groups == null || groups.length == 0) {
            LOG.error(String.format("Can not find topics: %s to copy in issue %d", revmanDirPath, issueId));
            return;
        }

        try {
            for (File group: groups) {

                if (!group.isDirectory()) {
                    continue;
                }

                String relativePath = FilePathCreator.getRevmanTopicSource(group.getName());
                String topicPath = revmanDirPath + relativePath;
                if (!rps.isFileExistsQuiet(topicPath)) {
                    continue;
                }
                rps.putFile(inputDir + FilePathCreator.SEPARATOR + relativePath, rps.getFile(topicPath), true);
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private static void createDbs(int issueId) {
        //String dbs = CochraneCMSProperties.getProperty("cms.cochrane.dbs.create");
        //if (dbs == null) {
        //    throw new IllegalStateException("Not installed system property cms.cochrane.dbs.create");
        //}
        //String[] dbNames = dbs.split(",");
        List<String> dbNames = BaseType.getDbNames4Create();

        for (String db : dbNames) {
            db = db.trim();
            getResultStorage().createDb(issueId,
                    CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CMS_COCHRANE_PREFIX + db),
                    Integer.parseInt(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CMS_COCHRANE_PREFIX
                            + db + ".priority"))
            );
        }
    }

    private void setAssets() {
        try {
            IRepositorySessionFacade rps = CochraneCMSPropertyNames.lookup("RepositorySessionFacade",
                    IRepositorySessionFacade.class);
            rps.setAssets("content-data");
            LOG.debug("Create issue: assets setting finished successfully");
        } catch (NamingException e) {
            LOG.error(CREATE_ISSUE_ASSETS_SETTING_FAILED, e);
        } catch (Exception e) {
            LOG.error(CREATE_ISSUE_ASSETS_SETTING_FAILED);
        }
    }

    public Integer getId() {
        return entity.getId();
    }

    public String getTitle() {
        String titleStr = entity.getTitle();
        return titleStr == null || titleStr.length() == 0
                ? "Issue " + entity.getNumber() + ", " + entity.getYear() : titleStr;
    }

    public void setTitle(String value) {
        entity.setTitle(value);
    }

    public int getNumber() {
        return entity.getNumber();
    }

    public void setNumber(int value) {
        entity.setNumber(value);
    }

    public int getYear() {
        return entity.getYear();
    }

    public void setYear(int value) {
        entity.setYear(value);
    }

    public int getFullNumber() {
        return entity.getFullNumber();
    }

    public Date getDate() {
        return entity.getDate();
    }

    public void setDate(Date value) {
        entity.setDate(value);
    }

    public Date getPublishDate() {
        return entity.getPublishDate();
    }

    public void setPublishDate(Date date) {

        Date value = date;
        if (value == null) {
            value = entity.getPublishDate();
        }
        if (value == null) {
            value = new Date();
        }
        entity.setPublishDate(value);
    }

    public String getStatus() {
        String status;
        if (entity.isArchiving()) {
            status = "Archiving";
        } else if (entity.isArchived()) {
            status = "Archived";
        } else {
            status = "";
        }
        return status;
    }

    public String getMeshtermsStatus() {
        String status;
        if (entity.isMeshtermsDownloaded()) {
            status = "Meshterms Downloaded";
        } else if (entity.isMeshtermsDownloading()) {
            status = "Downloading meshterms...";
        } else {
            status = "";
        }
        return status;
    }

    public Action[] getActions() {
        Action[] actions;
        if (entity.isArchived() && !entity.isArchiving()) {
            actions = new Action[]{new UnArchiveAction()};
        } else if (entity.isArchiving() || CmsUtils.isSpecialIssue(entity.getId())) {
            actions = new Action[0];
        } else {
            actions = new Action[]{new ArchiveAction()};
        }
        return actions;
    }

    public void performAction(int action, IVisit visit) {
        switch (action) {
            case Action.ARCHIVE_ACTION:
                new ArchiveAction().perform(visit);
                break;
            case Action.DELETE_ACTION:
                new DeleteAction().perform(visit);
                break;
            case Action.UNARCHIVE_ACTION:
                new UnArchiveAction().perform(visit);
                break;
            case Action.DOWNLOAD_MESHTERMS_ACTION:
                new MeshtermsDownloadAction().perform(visit);
                break;
            default:
                throw new IllegalArgumentException();
        }
        askEntity(entity.getId());
    }

    private void askEntity(int issueId) {
        entity = getResultStorage().getIssue(issueId);
    }

    public void merge() {
        getResultStorage().mergeIssue(entity);
        try {
            CochraneCMSPropertyNames.lookupRecordCache().refreshLastDatabases();
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    public boolean isExistsIssueByYearAndNumber() {
        int id = 0;
        if (entity.getId() != null) {
            id = entity.getId();
        }
        return getResultStorage().isExistsIssueByYearAndNumber(
                id, entity.getYear(), entity.getNumber(), true);
    }

    public static List<IssueWrapper> getIssueWrapperList(List<IssueEntity> list) {
        List<IssueWrapper> wrapperList = new ArrayList<IssueWrapper>();
        for (IssueEntity entity : list) {
            wrapperList.add(new IssueWrapper(entity));
        }
        return wrapperList;
    }

    public static List<IssueWrapper> getIssueWrapperList() {
        List<IssueEntity> list = getResultStorage().getIssueList();
        return getIssueWrapperList(list);
    }

    public static int getIssueListCount() {
        return getResultStorage().getIssueListCount();
    }

    public static List<IssueWrapper> getIssueWrapperList(int beginIndex, int limit) {
        return getIssueWrapperList(getResultStorage().getIssueList(beginIndex, limit));
    }

    public String getTerm2numLast() {
        return Term2NumHelper.getLastCreationDate();
    }

    public Action[] getActionsMeshtermsDownload() {
        Action[] actionArray;
        ArrayList<Action> actions = new ArrayList<Action>();
        if (!entity.isMeshtermsDownloaded() && !entity.isMeshtermsDownloading()) {
            actions.add(new MeshtermsDownloadAction());
        }

        actionArray = new Action[actions.size()];
        actions.toArray(actionArray);

        return actionArray;
    }

    public Action[] getActionsTerm2Num() {
        Action[] actions = {
            new Term2numNormalAction(),
            new Term2numForceAction(),
            new Term2numCheckChangedMeshCodesAction(),
        };
        return actions;
    }

    public void performTerm2NumAction(int action, IVisit visit) {
        switch (action) {
            case Action.CREATE_ACTION:
                OperationManager.Factory.getFactory().getInstance().performTerm2NumCreation(false, visit.getLogin());
                break;
            case Action.MAKE_PERM_ACTION:
                OperationManager.Factory.getFactory().getInstance().performTerm2NumCreation(true, visit.getLogin());
                break;
            case Action.CHECK_CHANGES_MESH_CODES_ACTION:
                OperationManager.Factory.getFactory().getInstance().updateMeshtermCodes(visit.getLogin());
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private class Term2numForceAction extends AbstractAction {
        public int getId() {
            return Action.MAKE_PERM_ACTION;
        }

        public String getDisplayName() {
            return "Load by FTP and Rebuild";
        }

        public void perform(IVisit visit) {
            try {
                logAction(visit, ActivityLogEntity.EntityLevel.TERM2NUM, ILogEvent.TERM2NUM_FORCE_REBUILD_STARTED,
                        IssueWrapper.this.getId(), IssueWrapper.this.getTitle(), "force rebuild started");
            } catch (Exception e) {
                LOG.error(e, e);
            }
        }
    }

    private class Term2numCheckChangedMeshCodesAction extends AbstractAction {
        public int getId() {
            return Action.CHECK_CHANGES_MESH_CODES_ACTION;
        }

        public String getDisplayName() {
            return "Check Changed Mesh Codes";
        }

        public boolean isConfirmable() {
            return true;
        }

        public boolean isCommentRequested() {
            return true;
        }

        public String getConfirmMessage() {

            return "Are you sure you want to check for changed Mesh Codes? "
                    + "This will lead to entire Re-Rendering for those records, which contains changes in Mesh Codes. "
                    + "Please verify that old term2num.xml has been renamed to term2num_old.xml and new term2num.xml "
                    + "has been placed to "
                    + CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.RESOURCES_TERM2NUM_OUTPUT)
                        .replace("file:///", "");
        }

        public void perform(IVisit visit) {
            try {
                logAction(visit, ActivityLogEntity.EntityLevel.ENTIREDB, ILogEvent.CHECK_CHANGES_MESH_CODES_STARTED,
                        IssueWrapper.this.getId(), "", "Check for changed Mesh Codes started");
            } catch (Exception e) {
                LOG.error(e, e);
            }
        }
    }

    private class Term2numNormalAction extends AbstractAction {
        public int getId() {
            return Action.CREATE_ACTION;
        }

        public String getDisplayName() {
            return "Rebuild";
        }

        public void perform(IVisit visit) {
            try {
                logAction(visit, ActivityLogEntity.EntityLevel.TERM2NUM, ILogEvent.TERM2NUM_REBUILD_STARTED,
                        IssueWrapper.this.getId(), IssueWrapper.this.getTitle(), "rebuild started");
            } catch (Exception e) {
                LOG.error(e, e);
            }
        }
    }

    private class MeshtermsDownloadAction extends AbstractAction {

        public int getId() {
            return Action.DOWNLOAD_MESHTERMS_ACTION;
        }

        public String getDisplayName() {
            return "Download Meshterms";
        }

        public void perform(IVisit visit) {
            try {
                IssueStorageFactory.getFactory().getInstance().setIssueMeshtermsDownloading(entity, true);
                logAction(visit, ActivityLogEntity.EntityLevel.ISSUE, ILogEvent.MESHTERMS_DOWNLOADER_STARTED,
                        IssueWrapper.this.getId(), IssueWrapper.this.getTitle(), "Meshterms downloader started");

                InitialContext ctx = new InitialContext();
                JMSSender.send((QueueConnectionFactory) ctx.lookup(JMSSender.DEFAULT_CONNECTION_LOOKUP),
                        (Queue) ctx.lookup("java:jboss/exported/jms/queue/medline-downloader-service"),
                        new JMSSender.MessageCreator() {
                            public Message createMessage(Session session) throws JMSException {
                                ObjectMessage message = session.createObjectMessage();
                                int currentIssue =
                                        Integer.parseInt(String.format("%d%02d", entity.getYear(), entity.getNumber()));
                                IMedlineRequest request =
                                        new MeshtermDownloaderRequest(currentIssue, entity.getId(), entity.getTitle());
                                List<Map<String, String>> params = getParams();
                                request.setParameters(new MedlineDownloaderParameters(params,
                                        CochraneCMSProperties.getProperty("cms.resources.medline.downloader.downloads")
                                                + currentIssue));
                                message.setObject(request);
                                return message;
                            }
                        });
            } catch (Exception e) {
                LOG.error(e, e);
            }
        }

        private List<Map<String, String>> getParams() {
            List<String> recordNames = EntireDBStorageFactory.getFactory().getInstance().findSysrevReviewRecordNames();
            List<Map<String, String>> params = new ArrayList<Map<String, String>>();
            for (String recordName : recordNames) {
                Map<String, String> singleParam = new HashMap<String, String>();
                singleParam.put("revmanId", recordName);
                singleParam.put("writeSearchResult", "false");
                params.add(singleParam);
            }
            return params;
        }
    }

    private class DeleteAction extends AbstractAction {
        public int getId() {
            return Action.DELETE_ACTION;
        }

        public String getDisplayName() {
            return "Delete";
        }

        public void perform(IVisit visit) {
            LOG.warn(String.format("'delete Issue' operation for %s is currently not supported",
                    IssueWrapper.this.getTitle()));
            /*IRepository rp = RepositoryFactory.getRepository();
            try {
                
                rp.deleteDir(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                        + "/" + CochraneCMSProperties.getProperty("cms.cochrane.archivepath")
                        + "/" + entity.getId());

                DbStorageFactory.getFactory().getInstance().deleteDbByIssue(entity);
                IssueStorageFactory.getFactory().getInstance().deleteIssue(entity);
                CochraneCMSPropertyNames.lookupRecordCache().refreshLastDatabases();

            } catch (Exception e) {
                LOG.error(e, e);
            }
            logAction(visit, ActivityLogEntity.EntityLevel.ISSUE, ILogEvent.DELETE, IssueWrapper.this.getId(),
                      IssueWrapper.this.getTitle(), "issue removed ");

            LOG.info("issue " + IssueWrapper.this.getId() + "removed");*/
        }
    }

    private class ArchiveAction extends AbstractAction {
        public int getId() {
            return Action.ARCHIVE_ACTION;
        }

        public String getDisplayName() {
            return "Archive";
        }

        public void perform(IVisit visit) {
            try {
                logAction(visit, ActivityLogEntity.EntityLevel.ISSUE, ILogEvent.ARCHIVE_STARTED,
                          IssueWrapper.this.getId(), IssueWrapper.this.getTitle(), "archiving started");

                new Thread(new Archiver(entity)).start();
            } catch (Exception e) {
                LOG.error(e, e);
            }
        }
    }

    private class UnArchiveAction extends AbstractAction {
        public int getId() {
            return Action.UNARCHIVE_ACTION;
        }

        public String getDisplayName() {
            return Archiver.isUnArchivingFullySupported(entity) ? "Unarchive" : "Unpack";
        }

        public void perform(IVisit visit) {
            new Archiver(entity).startUnArchiving();
        }
    }
}
