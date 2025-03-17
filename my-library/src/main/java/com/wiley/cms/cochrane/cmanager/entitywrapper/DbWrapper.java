package com.wiley.cms.cochrane.cmanager.entitywrapper;

import java.io.IOException;
import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.activitylog.UUIDEntity;
import com.wiley.cms.cochrane.authentication.IVisit;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.FilePathCreator;
import com.wiley.cms.cochrane.cmanager.MessageSender;
import com.wiley.cms.cochrane.cmanager.central.IPackageDownloader;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.db.ClDbVO;
import com.wiley.cms.cochrane.cmanager.data.db.DbVO;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.data.record.RecordVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.process.ICMSProcessManager;
import com.wiley.cms.cochrane.process.IRecordCache;
import com.wiley.cms.cochrane.process.Wml3gConversionManager;
import com.wiley.cms.cochrane.process.handler.DbHandler;
import com.wiley.cms.cochrane.process.handler.JatsConversionHandler;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ContentLocation;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.converter.services.IConversionProcess;
import com.wiley.cms.process.ProcessHelper;
import com.wiley.cms.process.ProcessVO;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.cms.process.res.ProcessType;
import com.wiley.cms.cochrane.test.LogPatterns;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 07.12.2006
 */
public class DbWrapper extends AbstractWrapper implements java.io.Serializable {
    private static final Logger LOG = Logger.getLogger(DbWrapper.class);

    private static final String PATH = "%path%";

    private ClDbEntity entity;
    private IssueWrapper issue;

    private ArrayList<PublishWrapper> publishList;

    private int rejectedRecordCount;
    private boolean rejectedRecordCountApproved;

    private boolean existsMinApprovedRecordsCount;
    private boolean existsMinApprovedRecordsCountApproved;

    private int approvedRecordCount;

    private int recordRenderPassedListCount;
    private boolean recordRenderPassedListCountApproved = false;

    private Action[] actionArray;
    private Res<BaseType> baseType;
   
    public DbWrapper(ClDbEntity entity) {
        setEntity(entity);
        askFields();
    }

    public DbWrapper(int clDbId) {
        askEntity(clDbId);
        askFields();
    }

    public DbWrapper(int clDbId, int deliveryFileId) {
        askEntity(clDbId);
    }

    public void setEntity(ClDbEntity entity) {
        this.entity = entity;
    }

    public ClDbEntity getEntity() {
        return entity;
    }

    public DbWrapper getDbWrapper() {
        return this;
    }

    public ArrayList<PublishWrapper> getPublishList() {
        return publishList;
    }

    public void setPublishList(ArrayList<PublishWrapper> publishList) {
        this.publishList = publishList;
    }

    public Integer getId() {
        return entity.getId();
    }

    public String getTitle() {
        return entity.getTitle();
    }

    public boolean getInitialPackageDelivered() {
        return entity.isInitialPackageDelivered();
    }

    public boolean isWRSupported() {
        return BaseType.isWRSupported(baseType);
    }

    public boolean isSysrevTitle() {
        return entity.getTitle().equals(CochraneCMSPropertyNames.getCDSRDbName());
    }

    public boolean isCentralTitle() {
        return entity.getTitle().equals(CochraneCMSPropertyNames.getCentralDbName());
    }

    public boolean isEditorialTitle() {
        return entity.getTitle().equals(CochraneCMSPropertyNames.getEditorialDbName());
    }

    public int getAllCount() {
        Integer count = entity.getAllCount();
        return (count == null) ? 0 : count;
    }

    public int getRenderedCount() {
        Integer count = entity.getRenderedCount();
        return (count == null) ? 0 : count;
    }

    public Date getDate() {
        return entity.getDate();
    }

    public IssueWrapper getIssue() {
        return issue;
    }

    public Date getStatusDate() {
        return entity.getStatusDate();
    }

    public void setStatusDate(Date date) {
        entity.setStatusDate(date);
    }

    public String getShortName() {
        try {
            return BaseType.find(entity.getTitle()).get().getShortName();
        } catch (Exception e) {
            return "";
        }
    }

    public String getFullName() {
        try {
            return BaseType.find(entity.getTitle()).get().getFullName();
        } catch (Exception e) {
            return "";
        }
    }

    public String getStatus() {
        String status;
        if (entity.isArchiving()) {
            status = CochraneCMSProperties.getProperty("db.status.archiving");
        } else if (entity.isArchived()) {
            status = CochraneCMSProperties.getProperty("db.status.archived");
        } else if (entity.isClearing()) {
            status = CochraneCMSProperties.getProperty("db.status.clearing");
        } else if (entity.getApproved() && !isExistsRejectedRecords() && isExistsMinApprovedRecordsCount()) {
            status = CochraneCMSProperties.getProperty("db.status.approved");
        } else {
            status = CochraneCMSProperties.getProperty("db.status.not_approved");
        }
        return status;
    }

    public String getExtStatus() {
        String status = "";
        Map<String, String> map = new HashMap<>();
        if (entity.isClearing()) {
            status = CochraneCMSProperties.getProperty("db.extstatus.clearing");

        } else if (entity.getApproved() && !isExistsRejectedRecords() && isExistsMinApprovedRecordsCount()) {
            map.put("publish_path", PATH + Action.PUBLISH_ACTION);
            map.put("unapprove_path", PATH + Action.UN_APPROVE_ACTION);
            status = CochraneCMSProperties.getProperty("db.extstatus.approved", map);

        } else if (!isExistsRejectedRecords() && isExistsMinApprovedRecordsCount()) {
            if (!getDbWrapper().getTitle().equals(CochraneCMSPropertyNames.getCcaDbName())) {
                map.put("approve_path", PATH + Action.APPROVE_ACTION);
                status = CochraneCMSProperties.getProperty("db.extstatus.can_approved", map);
            }
        } else if (isExistsRejectedRecords() || !isExistsMinApprovedRecordsCount()) {
            map.put("minimum", "" + baseType.get().getMinApproved());
            map.put("approved", Integer.toString(getApprovedRecordCount()));
            status = CochraneCMSProperties.getProperty("db.extstatus.can_not_approved", map);
        }

        return status;
    }

    private boolean isExistsRejectedRecords() {
        return (getRejectedRecordCount() > 0);
    }

    private int getRejectedRecordCount() {
        if (!rejectedRecordCountApproved) {
            rejectedRecordCount = getResultStorage().getRejectedRecordCount(entity.getId());
            rejectedRecordCountApproved = true;
        }
        return rejectedRecordCount;
    }

    public int getApprovedRecordCount() {
        return approvedRecordCount;
    }

    public boolean isApproveAllowed() {
        return !getTitle().equals(CochraneCMSPropertyNames.getCcaDbName());
    }

    public boolean isExistsMinApprovedRecordsCount() {
        if (existsMinApprovedRecordsCountApproved) {
            return existsMinApprovedRecordsCount;
        }

        if (getAllCount() < 1) {
            existsMinApprovedRecordsCount = false;
        } else {
            int minCount = baseType.get().getMinApproved();
            int realCount = getResultStorage().getRecordCount(entity.getId());
            if (realCount < minCount) {
                minCount = realCount;
            }
            approvedRecordCount = getResultStorage().getApprovedRecordCount(entity.getId());
            existsMinApprovedRecordsCount = (approvedRecordCount >= minCount);
        }

        existsMinApprovedRecordsCountApproved = true;

        return existsMinApprovedRecordsCount;
    }

    public int getRecordRenderPassedListCount() {
        if (!recordRenderPassedListCountApproved) {
            recordRenderPassedListCount = getRecordResultStorage().
                    getDbRecordListCount(getId(), null, SearchRecordStatus.RENDER_PASSED);
            recordRenderPassedListCountApproved = true;
        }
        return recordRenderPassedListCount;
    }


    public boolean isClearAllowed() {
        boolean clearAnyDbAllowed =
                Boolean.parseBoolean(CochraneCMSProperties.getProperty("cms.cochrane.any.db.clear.allowed", "false"));
        boolean empty = true;
        if (!clearAnyDbAllowed) {
            DbVO nextDB = getDbStorage().getNextDbVOByNameAndIssue(getTitle(), getIssue().getId());
            if (nextDB != null && nextDB.getAllCount() > 0){
                empty = false;
            }
        }
        //return (clearAnyDbAllowed || empty) && !getDbStorage().isClearingInProgress(entity.getId());
        return (clearAnyDbAllowed || empty) && !entity.isClearing();
    }

    public boolean isNotImport() {
        return !CmsUtils.isImportIssue(getIssue().getId())
                || !getTitle().contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLSYSREV));
    }

    public Action[] getActions() {
        fillActionsArray();
        return actionArray;
    }

    private void fillActionsArray() {
        ArrayList<Action> actions = new ArrayList<>();

        if (entity.isClearing() || entity.isArchiving()) {
            actionArray = new Action[0];
            return;
        }

        if (entity.isArchived()) {
            actions.add(new RecoverAction());
            return;
        }

        if (entity.getApproved() && !isExistsRejectedRecords() && isExistsMinApprovedRecordsCount()) {
            actions.add(new UnApproveAction());
            if (BaseType.find(entity.getTitle()).get().canPublish()) {
                actions.add(new PublishAction());
            }
        } else if (!isExistsRejectedRecords() && isExistsMinApprovedRecordsCount() && isApproveAllowed()) {
            actions.add(new ApproveAction());
        }

        actionArray = new Action[actions.size()];
        actions.toArray(actionArray);
    }

    public void performAction(int action, IVisit visit) {

        AbstractAction act;
        switch (action) {
            case Action.APPROVE_ACTION:
                act = new ApproveAction();
                break;
            case Action.UN_APPROVE_ACTION:
                act = new UnApproveAction();
                break;
            case Action.PUBLISH_ACTION:
                act = new PublishAction();
                break;
            case Action.RECOVER_ACTION:
                act = new RecoverAction();
                break;
            case Action.DELIVER_CENTRAL_PACKAGE_ACTION:
                act = new DeliverCentralPackageAction();
                break;
            case Action.RENDER_HTML_ACTION:
            case Action.RENDER_PDF_ACTION:
                act = new RenderAction(action);
                break;
            case Action.CONVERT_TO_3G_ACTION:
                act = new ConvertTo3gAction();
                break;
            case Action.CONVERT_REVMAN_ACTION:
                act = new ConvertRevmanAction();
                break;
            case Action.CONVERT_JATS_ACTION:
                act = new ConvertJatsAction();
                break;

            default:
                throw new IllegalArgumentException();
        }

        if (entity.isClearing()) {

            String msg = String.format("%s is not allowed because database %s is clearing", act.getDisplayName(),
                        getTitle());
            LOG.warn(msg);
            act.logAction(visit, ActivityLogEntity.EntityLevel.DB, ILogEvent.EXCEPTION, getId(), getTitle(), msg);
            return;
        }

        act.perform(visit);
        askEntity(entity.getId());
        askFields();
    }

    private void askEntity(int clDbId) {
        setEntity(getResultStorage().getDb(clDbId));
    }

    private void askFields() {
        issue = new IssueWrapper(entity.getIssue());
        baseType = BaseType.find(getTitle());
    }

    public static List<DbWrapper> getDbWrapperList(List<ClDbEntity> list) {
        List<DbWrapper> wrapperList = new ArrayList<>();
        for (ClDbEntity entity : list) {

            BaseType bt = BaseType.find(entity.getTitle()).get();
            if (bt.legal()) {
                wrapperList.add(new DbWrapper(entity));
            }
        }
        return wrapperList;
    }

    public static List<DbWrapper> getDbWrapperList(int curIssueId) {
        return getDbWrapperList(getResultStorage().getDbList(curIssueId));
    }

    public static Map<Integer, ClDbEntity> getDbWrapperMap(int curIssueId) {
        List<ClDbEntity> list = getResultStorage().getDbList(curIssueId, true);
        if (list.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, ClDbEntity> ret = new HashMap<>();
        list.forEach(clDb -> ret.put(clDb.getId(), clDb));
        return  ret;
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void merge() {
        getResultStorage().mergeDb(entity);
    }

    public List<Integer> getDbRecordIdList(String[] items, int searchStatus) {
        return getRecordResultStorage().getDbRecordIdList(getId(), items, searchStatus, Constants.UNDEF,
                Constants.UNDEF);
    }

    public void archiveIssueDb() {
        getDbStorage().clearIssueDb(entity.getId(), true);
        CochraneCMSBeans.getRecordCache().update();
    }

    public void clearDbAsynchronous(IVisit visit) {
        if (CmsUtils.isSpecialIssue(entity.getIssue().getId())) {
            LOG.warn("clearing for Issue 1 to Import or SPD Issue 2 is not supported");
            return;
        }
        ClDbEntity clDb = getDbStorage().getDbAfterDbForClearing(entity.getDatabase().getId(),
                entity.getIssue().getYear(), entity.getIssue().getNumber());
        if (clDb != null && !CmsUtils.isScheduledIssue(entity.getIssue().getId())) {
            LOG.warn("clearing for %s is not possible until %s is removed or cleared", entity, clDb);
            return;
        }
        getOperationManager().clearDB(entity.getId(), entity.getTitle(), visit.getLogin());
    }

    public List<AddonFile> getAddonFileList() {
        String path = CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY)
                + "/" + getIssue().getId()
                + "/" + getTitle() + "/";
        List<AddonFile> list = new ArrayList<>();

        tryAddAddonFile(path + "CL_new_reviews.xls", "New reviews", list);
        tryAddAddonFile(path + "CL_updated_reviews.xls", "Updated reviews", list);
        tryAddAddonFile(path + "CL_new_protocols.xls", "New protocols", list);
        tryAddAddonFile(path + "CL_updated_protocols.xls", "Updated protocols", list);
        tryAddAddonFile(path + "Updated_CDSR_records_connected_with_CCA.xls",
                "Updated CDSR records connected with CCA", list);
        tryAddAddonFile(path + "manifest.csv", "Manifests", list);

        return list;
    }

    public String getManualReportPath() {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + getIssue().getId()
                + "/" + getTitle() + "/ManualReport.xls";
    }

    public String getImportReportPath() {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + getIssue().getId()
                + "/" + getTitle() + "/ImportReport.xls";
    }

    public String getNihMandateReportPath() {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + getIssue().getId()
                + "/" + getTitle() + "/NihMandateReport.xls";
    }

    public String getWhenReadyReportPath() {
        return CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.PREFIX_REPOSITORY) + "/" + getIssue().getId()
                + "/" + getTitle() + buildWRReportName(getIssue().getYear(), getIssue().getNumber());
    }

    public AddonFile getManualReport() {
        List<AddonFile> list = new ArrayList<>();
        tryAddAddonFile(getManualReportPath(), "Manual Report", list);
        return list.size() > 0 ? list.get(0) : null;
    }

    public AddonFile getImportReport() {
        List<AddonFile> list = new ArrayList<>();
        tryAddAddonFile(getImportReportPath(), "Import Report", list);
        return list.size() > 0 ? list.get(0) : null;
    }

    public AddonFile getNihMandateReport() {
        List<AddonFile> list = new ArrayList<>();
        tryAddAddonFile(getNihMandateReportPath(), "Nih Mandate Report", list);
        return list.size() > 0 ? list.get(0) : null;
    }

    public AddonFile getWhenReadyReport() {
        List<AddonFile> list = new ArrayList<>();
        tryAddAddonFile(getWhenReadyReportPath(), "PWR Report", list);
        return list.size() > 0 ? list.get(0) : null;
    }

    protected void tryAddAddonFile(String path, String name, List<AddonFile> list) {
        try {
            super.tryAddAddonFile(path, name, list);
        } catch (IOException e) {
            LOG.debug(e, e);
        }
    }

    public boolean isGenerating(String type) {
        return getResultStorage().isGenerating(DbWrapper.this.getId(), type);
    }

    public String getGenerationDate(String type) {
        return getResultStorage().getGenerationDate(DbWrapper.this.getId(), type);
    }

    public boolean isSending(String type) {
        return getResultStorage().isSending(DbWrapper.this.getId(), type);
    }

    public String getSendingDate(String type) {
        return getResultStorage().getSendingDate(DbWrapper.this.getId(), type);
    }

    private String buildWRReportName(int year, int month) {
        return String.format("/XDPS-PWR-%s-%d.xls", new DateFormatSymbols().getShortMonths()[month - 1], year);
    }

    private class DeliverCentralPackageAction extends AbstractAction {

        public int getId() {
            return Action.DELIVER_CENTRAL_PACKAGE_ACTION;
        }

        public String getDisplayName() {
            return "Download Package";
        }

        public void perform(IVisit visit) {
            IRecordCache tmpLocker = null;
            try {
                if (entity.isArchived() || entity.isArchiving() || entity.isClearing()) {
                    throw new Exception("Issue is not open to perform downloading");
                }

                LOG.info(LogPatterns.OP_OBJECT_STARTING_BY, getName(), visit.getLogin(),
                        entity.getTitle(), entity.getId());
                tmpLocker = checkIn();
                logAction(visit, ActivityLogEntity.EntityLevel.DB, ILogEvent.PACKAGE_DOWNLOAD_STARTED, entity.getId(),
                        entity.getTitle());
                IPackageDownloader pd = CochraneCMSPropertyNames.lookup("PackageDownloader", IPackageDownloader.class);
                pd.downloadCentral(issue);

            } catch (Throwable tr) {
                LOG.error(LogPatterns.OP_OBJECT_FAIL, getName(), entity.getTitle(), entity.getId(), tr.getMessage());
                getActivityLog().error(ActivityLogEntity.EntityLevel.DB, ILogEvent.PACKAGE_DOWNLOAD_FAILED,
                        entity.getId(), entity.getTitle(), visit.getLogin(), tr.getMessage());

            } finally {
                logAction(visit, ActivityLogEntity.EntityLevel.DB, ILogEvent.PACKAGE_DOWNLOAD_FINISHED, entity.getId(),
                        entity.getTitle());
                checkOut(tmpLocker);
            }
        }

        private void checkOut(IRecordCache tmpLocker) {
            if (tmpLocker != null) {
                tmpLocker.removeRecord(entity.getId().toString());
            }
        }

        private IRecordCache checkIn() throws Exception {
            IRecordCache cache = CochraneCMSPropertyNames.lookupRecordCache();
            if (entity.isInitialPackageDelivered() || !cache.addRecord(entity.getId().toString(), false)) {
                throw new CmsException("it was already started by other action");
            }
            return cache;
        }

        private String getName() {
            return "Package download";
        }
    }

    private class ApproveAction extends AbstractAction {
        public int getId() {
            return Action.APPROVE_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("db.action.approve.name");
        }

        public void perform(IVisit visit) {
            LOG.debug("Approve action started for " + entity.getTitle());
            entity.setApproved(true);
            entity.setStatusDate(new Date());
            getResultStorage().mergeDb(entity);

            logAction(visit, ActivityLogEntity.EntityLevel.DB, ILogEvent.APPROVE, DbWrapper.this.getId(),
                      DbWrapper.this.getTitle());
        }
    }

    private class UnApproveAction extends AbstractAction {
        public int getId() {
            return Action.UN_APPROVE_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("db.action.unapprove.name");
        }

        public void perform(IVisit visit) {
            LOG.debug("UnApprove action started for " + entity.getTitle());
            entity.setApproved(false);
            entity.setStatusDate(new Date());
            getResultStorage().mergeDb(entity);

            logAction(visit, ActivityLogEntity.EntityLevel.DB, ILogEvent.UNAPPROVE, DbWrapper.this.getId(),
                      DbWrapper.this.getTitle());
        }
    }

    private class PublishAction extends AbstractAction {

        PublishAction() {
            setConfirmable(true);
        }

        public int getId() {
            return Action.PUBLISH_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("db.action.publish.name");
        }

        public void perform(IVisit visit) {
            try {
                DbWrapper.this.getPublishList().forEach(pw -> pw.setTransactionId(UUIDEntity.UUID));
                getPublishService().publishDb(DbWrapper.this.getId(), DbWrapper.this.getPublishList());

                logAction(visit, ActivityLogEntity.EntityLevel.DB, ILogEvent.PUBLISH, DbWrapper.this.getId(),
                          DbWrapper.this.getTitle());
            } catch (Exception e) {
                LOG.debug(e, e);
            }
        }
    }

    private class RecoverAction extends AbstractAction {

        public int getId() {
            return Action.RECOVER_ACTION;
        }

        public String getDisplayName() {
            return "Recover";
        }

        public void perform(IVisit visit) {
            LOG.debug("Recover DB not implemented");
        }
    }

    private class RenderAction extends AbstractRenderAction {

        public RenderAction(int action) {
            this.action = action;
        }

        public void perform(IVisit visit) {
            try {
                if (action != Action.RENDER_PDF_ACTION) {
                    return;
                }
                ClDbVO dbVO = new ClDbVO(getEntity());
                CochraneCMSBeans.getRenderManager().startFOPRendering(dbVO, visit.getLogin());

            } catch (Exception e) {
                LOG.error(e, e);
            }
        }
    }

    private class ConvertTo3gAction extends AbstractAction {

        public int getId() {
            return Action.CONVERT_TO_3G_ACTION;
        }

        public String getDisplayName() {
            return "WML3G conversion";
        }

        public void perform(IVisit visit) {

            Wml3gConversionManager.Factory.getBeanInstance()
                    .startConversion(new IssueVO(getIssue()), entity.getId(), visit.getLogin());
        }
    }

    private class ConvertJatsAction extends AbstractAction {

        public int getId() {
            return Action.CONVERT_JATS_ACTION;
        }

        public String getDisplayName() {
            return "JATS to WML3G";
        }

        public void perform(IVisit visit) {
            String dbName = DbWrapper.this.getTitle();
            List<RecordVO> list = getRecordResultStorage().getDbRecordVOList(DbWrapper.this.getId());
            if (list.isEmpty()) {
                LOG.info(String.format("no records for re-conversion found in Issue %d", getIssue().getFullNumber()));
                return;
            }
            Integer[] ids = new Integer[list.size()];
            int i = 0;
            for (RecordVO rvo: list) {
                ids[i++] = rvo.getId();
            }

            DbHandler dbHandler = new DbHandler(DbWrapper.this.getIssue().getFullNumber(), dbName,
                    DbWrapper.this.getId(), DbEntity.NOT_EXIST_ID, DbWrapper.this.getIssue().getId());
            JatsConversionHandler jh = new JatsConversionHandler(dbHandler, 0);
            ProcessType pt = ProcessType.find(ICMSProcessManager.PROC_TYPE_CONVERT_JATS).get();
            try {
                ProcessVO pvo = ProcessHelper.createIdPartsProcess(jh, pt, pt.getPriority(), visit.getLogin(), ids,
                        DbEntity.NOT_EXIST_ID, pt.batch());
                CochraneCMSBeans.getCMSProcessManager().startProcess(pvo);

            } catch (Exception e) {
                MessageSender.sendWml3gConversion(ContentLocation.ISSUE.getShortString(
                        DbWrapper.this.getIssue().getFullNumber(), dbName, null), e.getMessage());
                LOG.error("Jats conversion failed, " + e);
            }
        }
    }

    private class ConvertRevmanAction extends AbstractConvertRevmanAction {

        public void perform(IVisit visit) {
            try {
                IConversionProcess cp = AbstractManager.getConversionProcess();

                String inputDir = FilePathCreator.getInputDir(getIssue().getId(), getTitle())
                        + FilePathCreator.SEPARATOR;
                String destination = FilePathBuilder.getPathToReconvertedIssueDb(getIssue().getId(), getTitle());

                List<ErrorInfo> errors;

                int issueNumber = CmsUtils.getIssueNumber(getIssue().getYear(), getIssue().getNumber());
                Set<String> includedNames = new HashSet<>();
                List<RecordVO> list = getRecordResultStorage().getDbRecordVOList(DbWrapper.this.getId());
                if (list.isEmpty()) {
                    LOG.info(String.format("there are no records for re-conversion in Issue %d",
                            getIssue().getFullNumber()));
                }
                list.forEach(f -> includedNames.add(f.getName()));
                errors = cp.convert(inputDir, destination, new IssueVO(getIssue().getId(), getIssue().getYear(),
                        getIssue().getNumber(), new Date()), includedNames);
                if (!errors.isEmpty()) {
                    throw new Exception(getReport(errors, issueNumber, getActivityLog()));
                }

                logAction(visit, ActivityLogEntity.EntityLevel.DB, ILogEvent.REVMAN_DATA_CONVERTED, getId(),
                        getTitle());
            } catch (Exception e) {
                Map<String, String> message = new HashMap<>();
                message.put(MessageSender.MSG_PARAM_ISSUE, getIssue().getNumber() + "-" + getIssue().getYear());
                message.put(MessageSender.MSG_PARAM_LIST, e.getMessage());
                MessageSender.sendMessage(MessageSender.MSG_TITLE_DB_REVMAN_CONVERSION_FAILED, message);

                LOG.error("Revman conversion failed, " + e);
                logAction(visit, ActivityLogEntity.EntityLevel.DB, ILogEvent.CONVERSION_REVMAN_FAILED, getId(),
                        getTitle());
            }
        }
    }
}