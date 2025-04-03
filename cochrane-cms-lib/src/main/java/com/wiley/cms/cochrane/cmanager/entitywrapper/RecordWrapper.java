package com.wiley.cms.cochrane.cmanager.entitywrapper;

import com.wiley.cms.cochrane.activitylog.ActivityLogEntity;
import com.wiley.cms.cochrane.activitylog.ILogEvent;
import com.wiley.cms.cochrane.authentication.IVisit;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.RecordLightVO;
import com.wiley.cms.cochrane.cmanager.contentworker.JatsPackage;
import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;
import com.wiley.cms.cochrane.cmanager.data.DbRecordPublishEntity;
import com.wiley.cms.cochrane.cmanager.data.DeliveryFileEntity;
import com.wiley.cms.cochrane.cmanager.data.PrevVO;
import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.cmanager.data.entire.IEntireDBStorage;
import com.wiley.cms.cochrane.cmanager.data.record.IRecord;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordManifest;
import com.wiley.cms.cochrane.cmanager.data.record.UnitStatusEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.repository.IRepository;
import com.wiley.cms.cochrane.repository.IRepositorySessionFacade;
import com.wiley.cms.cochrane.repository.RepositoryFactory;
import com.wiley.cms.cochrane.services.ImageLinksHelper;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.cochrane.test.Hooks;
import com.wiley.tes.util.Extensions;
import com.wiley.tes.util.InputUtils;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.Now;

import javax.naming.NamingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author <a href='mailto:gkhristich@wiley.ru'>Galina Khristich</a>
 *         Date: 07.12.2006
 */
public class RecordWrapper extends AbstractWrapper implements IRecord {
    protected static final Logger LOG = Logger.getLogger(RecordWrapper.class);

    private static final long serialVersionUID = 1L;

    private static final String STARTED = " started";

    private RecordVersions versions;

    private Integer id;

    private String name;

    private String unitTitle;
    private String unitTitleNormalised;

    private boolean qasCompleted;
    private boolean qasSuccessful;

    private boolean renderingCompleted;
    private boolean renderingSuccessful;

    private DbWrapper db;
    private DeliveryFileWrapper deliveryFile;

    private boolean approved;
    private boolean rejected;
    private boolean isRawDataExists;
    private String stateDescription;
    private String notes;
    private String unitStatus;
    private String productSubtitle;

    private boolean disabled;
    private String recordPath;

    private String groupName;

    private boolean edited;
    private boolean unchanged;
    private int specialUpdated;
    private int lastSpecialUpdated;

    private int state;
    private int recordNumber;

    //private Boolean publishLit;
    //private Boolean publishHW;

    private RecordManifest manifest;
    private ICDSRMeta meta;

    public RecordWrapper() {
    }

    public RecordWrapper(String name, String recordPath, int dfId, int dbId) {

        setName(name);
        setRecordPath(recordPath);
        DeliveryFileWrapper df =  new DeliveryFileWrapper(dfId);
        setDeliveryFile(df);
        setDb(new DbWrapper(dbId));

        versions = RecordVersions.UNDEF_VERSION_INSTANCE;
    }

    public RecordWrapper(int recordId) {
        askEntity(recordId, true);
    }

    public RecordWrapper(RecordEntity entity) {
        initFields(entity);
    }

    public RecordWrapper(RecordEntity entity, boolean allPrevious) {
        initFields(entity, allPrevious);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void initCDSRMeta() {
        if (meta == null) {
            initMetaData(getResultStorage().findLatestMetadata(getName(), false));
        }
    }

    public boolean isHistoricalImport() {
        return meta != null && meta.isJats() && CmsUtils.isImportIssue(getIssueId())
                && meta.getHistoryNumber() > RecordEntity.VERSION_LAST;
    }

    /*private void setLastPublishedState(PublishedAbstractEntity entity) {
        publishLit = getPublishedStateLit(publishLit, entity);
        publishHW = getPublishedStateHW(publishHW, entity);
    }

    private void setLastPublishedState(PublishRecordEntity entity) {
        if (publishLit == null) {
            if (PubType.LITERATUM_RT_TYPES.contains(entity.getPublishPacket().getPublishType())) {
                publishLit = isProperPublishRecord(entity);
                return;
            }
        }
        if (publishHW == null && PubType.SEMANTICO_RT_TYPES.contains(entity.getPublishPacket().getPublishType())) {
            publishHW = isProperPublishRecord(entity);
        }
    }
    
    private boolean isProperPublishRecord(PublishRecordEntity entity) {
        return entity.getDeliveryId() == null || entity.getDeliveryId().equals(getDeliveryFileId());
    }

    private static Boolean getPublishedStateLit(Boolean current, PublishedAbstractEntity entity) {
        if (current != null) {
            return current;
        }
        int publish = entity.getPubNotified();
        return publish == PublishDestination.WOLLIT.ordinal() || publish == PublishDestination.LITERATUM_HW.ordinal();
    }

    private static Boolean getPublishedStateHW(Boolean current, PublishedAbstractEntity entity) {
        if (current != null) {
            return current;
        }
        int publish = entity.getPubNotified();
        return publish == PublishDestination.SEMANTICO.ordinal() || publish == PublishDestination.LITERATUM_HW.ordinal()
                     || publish == PublishDestination.SEMANTICO_WOL.ordinal();
    } */

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return CmsUtils.isSpecialIssue(getIssueId()) && meta != null
                ? RevmanMetadataHelper.buildPubName(meta.getCdNumber(), meta.getPubNumber()) : name;
    }

    public void setName(String name) {
        this.name = name;
        setNumber();
    }

    final void setNumber() {
        recordNumber = RecordHelper.buildRecordNumber(getName());
    }

    public String getPubName() {
        return RevmanMetadataHelper.buildPubName(getName(), getPubNumber());
    }

    public int getPubNumber() {
        return meta != null ? meta.getPubNumber() : versions.getPub();
    }

    @Override
    public String getPublishedOnlineFinalForm() {
        return meta != null ? meta.getPublishedOnlineFinalForm() : null;
    }

    public String getPublishedDateNormalized() {
        Date date = getPublishedDate();
        return date != null ? Now.formatDate(date) : null;
    }

    @Override
    public String getFirstOnline() {
        return meta != null ? meta.getFirstOnline() : null;
    }

    @Override
    public Date getPublishedDate() {
        return meta != null ? meta.getPublishedDate() : null;
    }

    public final int getNumber() {
        return recordNumber;
    }

    public String getUnitTitle() {
        return unitTitle;
    }

    public void setUnitTitle(String unitTitle) {
        this.unitTitle = unitTitle;
    }

    public String getUnitTitleNormalised() {
        if (unitTitleNormalised == null) {
            unitTitleNormalised = CmsUtils.unescapeEntities(getUnitTitle());
        }
        return unitTitleNormalised;
    }

    public DbWrapper getDb() {
        return db;
    }

    public void setDb(DbWrapper db) {
        this.db = db;
    }

    public void setDeliveryFile(DeliveryFileWrapper deliveryFile) {
        this.deliveryFile = deliveryFile;
    }

    public DeliveryFileWrapper getDeliveryFile() {
        return deliveryFile;
    }

    public boolean canHasWml21Content() {
        return deliveryFile == null || !DeliveryFileEntity.isWml3g(deliveryFile.getType());   // todo check 4 JATS
    }

    public boolean getApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public boolean getRejected() {
        return rejected;
    }

    public void setRejected(boolean rejected) {
        this.rejected = rejected;
    }

    public String getStateDescription() {
        return stateDescription;
    }

    public void setStateDescription(String stateDescription) {
        this.stateDescription = stateDescription;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isQasCompleted() {
        return qasCompleted;
    }

    public void setQasCompleted(boolean qasCompleted) {
        this.qasCompleted = qasCompleted;
    }

    public String getQasResult() {
        return getRecordResultStorage().getRecordQASResults(getId());
    }

    public boolean isQasSuccessful() {
        return qasSuccessful;
    }

    public void setQasSuccessful(boolean qasSuccessful) {
        this.qasSuccessful = qasSuccessful;
    }

    public boolean isRenderingCompleted() {
        return renderingCompleted;
    }

    public void setRenderingCompleted(boolean renderingCompleted) {
        this.renderingCompleted = renderingCompleted;
    }

    public boolean isRenderingSuccessful() {
        return renderingSuccessful;
    }

    public boolean isPublished() {
        return RecordEntity.isPublished(state);
    }

    public boolean hasHWError() {
        return state == RecordEntity.STATE_HW_PUBLISHING_ERR;
    }

    public void setRenderingSuccessful(boolean renderingSuccessful) {
        this.renderingSuccessful = renderingSuccessful;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public String getRecordPath() {
        return recordPath;
    }

    public void setRecordPath(String recordPath) {
        this.recordPath = recordPath;
    }

    public boolean isRawDataExists() {
        return isRawDataExists;
    }

    public void setRawDataExists(boolean rawDataExists) {
        isRawDataExists = rawDataExists;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    public RecordVersions getVersions() {
        return versions;
    }

    public void setVersions(RecordVersions versions) {
        this.versions = versions;
        initMetaData(getResultStorage().getCDSRMetadata(getName(), versions.getPub()));
    }

    public String getIssueStr() {
        return meta == null ? null : String.format("%d Issue %d", CmsUtils.getYearByIssueNumber(meta.getIssue()),
                CmsUtils.getIssueByIssueNumber(meta.getIssue()));
    }

    public static boolean isRawDataExists(int issue, String db, String recordName) {

        return getResultStorage().getRecord(issue, db, recordName).isRawDataExists();
    }

    public boolean isSysrev() {
        return getDb().isSysrevTitle();
    }

    public RecordManifest getRecordManifest() {
        return manifest;
    }

    private void setInnerState(int state) {
        this.state = state;
    }

    public boolean hasState() {
        return state != RecordEntity.STATE_UNDEFINED;
    }

    public void setState(String state) {
    }

    /*private String buildFinalPublishedState(boolean ds, boolean cch) {

        boolean lit = Boolean.TRUE.equals(publishLit);
        boolean hw = Boolean.TRUE.equals(publishHW);

        StringBuilder ret = new StringBuilder("Published");
        if (!lit && !hw && !ds && !cch) {
            // a state is published, but specific details are missing
            return ret.toString();
        }

        ret.append(" [");
        boolean wasAppend = false;
        if (lit) {
            ret.append("LIT");
            wasAppend = true;
        }
        if (hw) {
            ret.append(wasAppend ? ",HW" : "HW");
            wasAppend = true;
        }
        if (ds) {
            ret.append(wasAppend ? ",DS" : "DS");

        } else if (cch) {
            ret.append(wasAppend ? ",CCH" : "CCH");
        }
        ret.append("]");
        return ret.toString();
    }*/

    public String getState() {

        String ret;
        switch (state) {

            case RecordEntity.STATE_CCH_PUBLISHED:
            case RecordEntity.STATE_DS_PUBLISHED:
            case RecordEntity.STATE_WR_PUBLISHED:
                ret = "Published";
                break;

            case RecordEntity.STATE_DS_PUBLISHING:
                ret = "Sending [DS]";
                break;

            case RecordEntity.STATE_WAIT_WR_PUBLISHED_NOTIFICATION:
                ret = "Awaiting publication";
                break;

            case RecordEntity.STATE_WAIT_WR_CANCELLED_NOTIFICATION:
                ret = "Awaiting cancellation";
                break;

            case RecordEntity.STATE_WR_PUBLISHING:
                ret = "Sending";
                break;

            case RecordEntity.STATE_HW_PUBLISHING:
                ret = "Sending [HW]";
                break;

            case RecordEntity.STATE_PROCESSING:
                ret = "Processing";
                break;

            case RecordEntity.STATE_WR_ERROR:
            case RecordEntity.STATE_WR_ERROR_FINAL:
            case RecordEntity.STATE_HW_PUBLISHING_ERR:
                ret = "Failed";
                break;

            default:
                ret = "";
                break;
        }

        return ret;
    }

    public String getStatus() {
        String status;
        if (state == RecordEntity.STATE_HW_PUBLISHING_ERR) {
            status = CochraneCMSProperties.getProperty("record.status.hw_failed");
        } else if (getApproved()) {
            status = CochraneCMSProperties.getProperty("record.status.approved");
        } else if (getRejected()) {
            status = CochraneCMSProperties.getProperty("record.status.rejected");
        } else {
            status = CochraneCMSProperties.getProperty("record.status.not_approved");
        }
        return status;
    }

    public boolean isUnchanged() {
        return unchanged;
    }

    public boolean isJats() {
        return meta != null ? meta.isJats() : recordPath != null && recordPath.contains(JatsPackage.JATS_FOLDER);
    }

    public Action[] getActions() {
        ArrayList<Action> actions = new ArrayList<>();

        if (isDisabled()) {
            return new Action[actions.size()];
        }

        if (RecordEntity.isProcessed(state)) {

            actions.add(new ResetAction());

            if (RecordEntity.isProcessedError(state)) {

                addApproveUnapproveActions(actions);
                addRejectUnrejectActions(actions);
                actions.add(new DeleteAction());
            }

        } else {
            addApproveUnapproveActions(actions);
            addRejectUnrejectActions(actions);
            actions.add(new DeleteAction());
        }

        Action[] actionArray = new Action[actions.size()];
        actions.toArray(actionArray);

        return actionArray;
    }

    private void addRejectUnrejectActions(ArrayList<Action> actions) {
        //add reject action
        if (!getApproved() && !getRejected()) {
            actions.add(new RejectAction());
        }

        //add unreject action
        if (getRejected()) {
            actions.add(new UnRejectAction());
        }
    }

    private void addApproveUnapproveActions(ArrayList<Action> actions) {
        //add approve action
        if (isQasSuccessful() && isRenderingSuccessful() && allRenderingsApproved() && !getRejected()
            && !getApproved()) {
            actions.add(new ApproveAction());
        }

        //add unapprove action
        if (getApproved()) {
            actions.add(new UnApproveAction());
        }
    }

    public boolean allRenderingsApproved() {
        List<RenderingWrapper> list = RenderingWrapper.getRenderingWrapperList(getId());
        for (RenderingWrapper rw : list) {
            if (!rw.isApproved()) {
                return false;
            }
        }
        return true;
    }

    public void performAction(int action, IVisit visit) {
        switch (action) {
            case Action.APPROVE_ACTION:
                new ApproveAction().perform(visit);
                break;
            case Action.UN_APPROVE_ACTION:
                new UnApproveAction().perform(visit);
                break;
            case Action.REJECT_ACTION:
                new RejectAction().perform(visit);
                break;
            case Action.UN_REJECT_ACTION:
                new UnRejectAction().perform(visit);
                break;
            case Action.DELETE_ACTION:
                new DeleteAction().perform(visit);
                break;
            case Action.COMMENT_ACTION:
                new CommentAction().perform(visit);
                break;
            case Action.RESET_ACTION:
                new ResetAction().perform(visit);
                break;
            default:
                throw new IllegalArgumentException();
        }
        askEntity(getId(), false);
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getGroupName() {
        return groupName;
    }

    @Override
    public String getGroupSid() {
        return getGroupName();
    }

    public boolean getRenderingCompleted() {
        return isRenderingCompleted();
    }

    public void askEntity(int recordId, boolean initMeta) {
        if (recordId == 0) {
            return;
        }
        RecordEntity entity = getResultStorage().getRecord(recordId);
        if (entity != null) {
            if (initMeta) {
                initMetaData(entity.getMetadata());
            }
            initFields(entity);
        }
    }

    private void initMetaData(ICDSRMeta metadata) {
        meta = metadata;
        if (meta != null) {
            setGroupName(meta.getGroupSid());
            setUnitTitle(meta.getTitle());
        }
    }

    private void initFields(RecordEntity entity) {
        initFields(entity, false);
    }

    private void initFields(RecordEntity entity, boolean allPrevious) {

        setId(entity.getId());

        UnitStatusEntity unitStatusEntity = entity.getUnitStatus();
        if (unitStatusEntity != null) {
            setUnitStatus(unitStatusEntity.getName());
            int status = unitStatusEntity.getId();
            switch (status)  {
                case UnitStatusEntity.UnitStatus.TRANSLATED_ABSTRACTS:
                    setTranslationUpdated();
                    break;
                case UnitStatusEntity.UnitStatus.MESHTERMS_UPDATED:
                    setMeshtermUpdated();
                    unchanged = true;
                    break;
                case UnitStatusEntity.UnitStatus.UNCHANGED:
                case UnitStatusEntity.UnitStatus.UNCHANGED_COMMENTED:
                    unchanged = true;
                    break;
                default:
                    break;
            }
        }

        setProductSubtitle(entity.getProductSubtitle() != null ? entity.getProductSubtitle().getName() : null);
        setApproved(entity.getApproved());

        DeliveryFileEntity df = entity.getDeliveryFile();
        if (df != null) {
            if (DeliveryPackage.isTranslatedAbstract(df.getType())) {
                setLastTranslationUpdated();   // to handle when ready translations
            }
        }
        setInnerState(entity.getState());

        setDisabled(entity.isDisabled());
        setName(entity.getName());
        setQasCompleted(entity.isQasCompleted());
        setQasSuccessful(entity.isQasSuccessful());
        setRejected(entity.getRejected());
        setStateDescription(entity.getStateDescription());
        setNotes(entity.getNotes());
        setRenderingCompleted(entity.isRenderingCompleted());
        setRenderingSuccessful(entity.isRenderingSuccessful());

        setRecordPath(entity.getRecordPath());
        setRawDataExists(entity.isRawDataExists());
        setEdited(entity.isEdited());

        initDb(entity, allPrevious);

        DeliveryFileWrapper file = null;
        if (entity.getDeliveryFile() != null) {
            file = new DeliveryFileWrapper(entity.getDeliveryFile());
        }
        setDeliveryFile(file);

        initMetaData(entity.getMetadata());
        setUnitTitle(entity.getUnitTitle());
    }

    private void initDb(RecordEntity entity, boolean allPrevious) {
        ClDbEntity clDb = entity.getDb();
        if (clDb == null) {
            return;
        }

        setDb(new DbWrapper(clDb));
        String dbName = clDb.getTitle();

        initPrevious(allPrevious, getFullIssueNumber(entity));

        manifest = new RecordManifest(clDb.getIssue().getId(), dbName, getName(), getRecordPath(),
                isHistoricalImport() ? meta.getHistoryNumber() : null);
    }

    protected int getFullIssueNumber(RecordEntity rec) {
        return rec == null ? 0 : rec.getDb().getIssue().getFullNumber();
    }

    public String getWML3GPath() {
        return FilePathBuilder.ML3G.getPathToMl3gRecord(getIssueId(), getDbName(), getName());
    }

    protected void initPrevious(boolean allPrevious, int fullIssueNumber) {
        if (!getDbName().equals(CochraneCMSPropertyNames.getCDSRDbName()) || CmsUtils.isSpecialIssue(getIssueId())) {
            versions = RecordVersions.UNDEF_VERSION_INSTANCE;
            return;
        }
        List<PrevVO> list = CochraneCMSBeans.getVersionManager().getVersions(fullIssueNumber, getName());
        if (!list.isEmpty()) {
            PrevVO latest = list.remove(0);
            setGroupName(latest.group);
            versions = new RecordVersions(latest.version, latest.pub, allPrevious, list);
        } else {
            versions = RecordVersions.UNDEF_VERSION_INSTANCE;
            LOG.warn(String.format(" %s version entity can't be found in the DB", getName()));
        }
    }

    public static int getDeliveryFileRecordListCount(int fileId) {
        return (int) getRecordResultStorage().getRecordCountByDf(fileId);
    }

    public static List<RecordWrapper> getDeliveryFileRecordWrapperList(int fileId,
                                                                       int beginIndex,
                                                                       int limit,
                                                                       int orderField,
                                                                       boolean orderDesc) {
        return getRecordWrapperList(
            getRecordResultStorage().getDFRecordList(
                fileId, beginIndex, limit, orderField, orderDesc));
    }

    public static List<Integer> getProcessRecordIds(int processId, int beginIndex, int limit) {
        return getProcessStorage().getProcessPartUIDs(processId, beginIndex, limit);
    }

    public static List<RecordWrapper> getProcessRecordWrapperList(int processId, int beginIndex, int limit) {
        return getProcessRecordWrapperList(processId, beginIndex, limit, false);
    }

    public static List<RecordWrapper> getProcessRecordWrapperList(int processId, int beginIndex, int limit,
                                                                  boolean allPrevious) {
        List<Integer> ids = getProcessRecordIds(processId, beginIndex, limit);
        return ids.isEmpty() ? new ArrayList<>()
                : getRecordWrapperList(getRecordResultStorage().getRecordEntitiesByIds(ids), allPrevious);
    }

    public static int getDbRecordListCount(int dbId, String[] items, int searchStatus, String fileStatus) {
        return getRecordResultStorage().getDbRecordListCount(dbId, items, searchStatus, fileStatus);
    }

    public static int getDbRecordListCount(int dbId, String[] items, int searchStatus, String text, String fileStatus) {
        return getRecordResultStorage().getDbRecordListCount(dbId, items, searchStatus, text, fileStatus);
    }

    public static void updateRecordList(int dbId, String[] items, boolean rejected, boolean approved,
                                        int searchStatus) {
        getRecordResultStorage().updateRecordList(dbId, items, rejected, approved, searchStatus);
    }

    public static List<RecordLightVO> getUpdatedRecordList(int dbId, String[] items, int searchStatus) {
        return getRecordResultStorage().getRecordLightVOList(dbId, items, searchStatus);
    }

    public static List<RecordWrapper> getDbRecordWrapperList(int dbId) {
        return getDbRecordWrapperList(dbId, 0, 0, null, 0, null, 0, false);
    }

    public static List<RecordWrapper> getDbRecordWrapperList(int dbId, int dfId, int beginIndex, int limit) {
        return getRecordWrapperList(getRecordResultStorage().getDbRecordList(dbId, dfId, beginIndex, limit, null,
            SearchRecordStatus.QA_PASSED, null, SearchRecordOrder.NAME, false, null));
    }

    public static List<RecordWrapper> getDbRecordWrapperList(int dbId, int dfId, int beginIndex, int limit,
        boolean allPrevious) {
        return getRecordWrapperList(getRecordResultStorage().getDbRecordList(dbId, dfId, beginIndex, limit, null,
            SearchRecordStatus.QA_PASSED, null, SearchRecordOrder.NAME, false, null), allPrevious);
    }

    public static List<RecordWrapper> getDbRecordWrapperList(int dbId, int beginIndex, int limit, boolean allPrevious) {
        return getDbRecordWrapperList(dbId, beginIndex, limit, null, SearchRecordStatus.QA_PASSED, null,
                SearchRecordOrder.NAME, false, allPrevious);
    }

    public static List<RecordWrapper> getDbRecordWrapperList(int dbId, int beginIndex, int limit) {
        return getDbRecordWrapperList(dbId, beginIndex, limit, null, SearchRecordStatus.QA_PASSED, null, 0, false);
    }

    public static List<RecordWrapper> getDbRecordWrapperList(int dbId, int beginIndex, int limit, String[] items,
        int searchStatus, String fileStatus, int orderField, boolean orderDesc) {

        return getRecordWrapperList(getRecordResultStorage().getDbRecordList(dbId, beginIndex, limit, items,
                searchStatus, fileStatus, orderField, orderDesc));
    }

    public static List<RecordWrapper> getDbRecordWrapperList(int dbId, int beginIndex, int limit, String[] items,
        int searchStatus, String fileStatus, int orderField, boolean orderDesc, boolean allPrevious) {

        return getRecordWrapperList(getRecordResultStorage().getDbRecordList(dbId, beginIndex, limit, items,
             searchStatus, fileStatus, orderField, orderDesc), allPrevious);
    }

    public static List<RecordWrapper> getDbRecordWrapperMap(int dbId, int beginIndex, int limit, String[] items,
        int searchStatus, String fileStatus, int orderField, boolean orderDesc, Map<Integer, RecordWrapper> map) {

        return getRecordWrapperMap(getRecordResultStorage().getDbRecordList(dbId, beginIndex, limit, items,
                searchStatus, fileStatus, orderField, orderDesc), map);
    }

    public static List<RecordWrapper> getDbRecordWrapperList(int dbId,
                                                             int beginIndex,
                                                             int limit,
                                                             String[] items,
                                                             int searchStatus,
                                                             String fileStatus,
                                                             int orderField,
                                                             boolean orderDesc,
                                                             String text) {
        return getRecordWrapperList(
                getRecordResultStorage().getDbRecordList(
                        dbId, beginIndex,
                        limit, items,
                        searchStatus, fileStatus, orderField, orderDesc, text)
        );
    }

    public static List<RecordWrapper> getRecordWrapperListByDbPublishRecords(List<DbRecordPublishEntity> list) {
        List<RecordWrapper> wrapperList = new ArrayList<>();
        for (DbRecordPublishEntity entity : list) {
            wrapperList.add(new RecordWrapper(entity.getRecord()));
        }
        return wrapperList;
    }

    public static List<RecordWrapper> getRecordWrapperList(List<RecordEntity> list, boolean allPrevious) {
        List<RecordWrapper> wrapperList = new ArrayList<>();
        for (RecordEntity entity : list) {
            wrapperList.add(new RecordWrapper(entity, allPrevious));
        }
        return wrapperList;
    }

    public static List<RecordWrapper> getRecordWrapperList(List<RecordEntity> list) {
        List<RecordWrapper> wrapperList = new ArrayList<RecordWrapper>();
        for (RecordEntity entity : list) {
            wrapperList.add(new RecordWrapper(entity));
        }
        return wrapperList;
    }

    public static List<RecordWrapper> getRecordWrapperMap(List<RecordEntity> list, Map<Integer, RecordWrapper> map) {
        List<RecordWrapper> wrapperList = new ArrayList<>();
        for (RecordEntity entity : list) {

            RecordWrapper rw = new RecordWrapper(entity);
            wrapperList.add(rw);
            map.put(rw.getNumber(), rw);
        }
        return wrapperList;
    }

    public Map<String, String> getSimilarRecordsMap() {
        return getResultStorage().getSimilarRecordsMap(getId());
    }

    public static int findRecord(int dbId, String recordName) {
        return getResultStorage().findRecord(dbId, recordName);
    }

    /*public static void setPublishedStates(Integer clDbId, Map<Integer, RecordWrapper> wrapperMap) {
        if (wrapperMap.isEmpty()) {
            return;
        }
        List<PublishRecordEntity> sentList = CochraneCMSBeans.getPublishStorage().findSentPublishRecords(
                wrapperMap.keySet(), clDbId, PubType.UI_STATE_TYPES);

        sentList.forEach(pre -> wrapperMap.get(pre.getNumber()).setLastPublishedState(pre));

        if (sentList.isEmpty()) {
            // using an old approach for compatibility with old Issues
            List<PublishedAbstractEntity> list = getResultStorage().getPublishedAbstracts(wrapperMap.keySet(), clDbId);

            for (PublishedAbstractEntity pe : list) {
                RecordWrapper rw = wrapperMap.get(pe.getNumber());
                Integer dfId = pe.getDeliveryId();
                if (dfId == null || dfId.equals(rw.getDeliveryFile().getId())) {
                    rw.setLastPublishedState(pe);
                }
            }
        }
    }*/

    public String getSourceXmlFromFile() {
        String data = "";
        try {
            IRepositorySessionFacade rsf = CochraneCMSPropertyNames.lookup("RepositorySessionFacade",
                    IRepositorySessionFacade.class);

            data = rsf.getArticleXmlFileAsString(getRecordPath(), true);

        } catch (NamingException e) {
            LOG.error(e, e);
        } catch (IOException e) {
            LOG.error(e, e);
        }
        return data;
    }

    public String getRevManStatsDataURI() {
        if (!getDb().getTitle().equals(CochraneCMSPropertyNames.getCDSRDbName())) {
            return null;
        }

        String ret = null;
        List<String> uris = manifest.getUris(Constants.SOURCE);

        for (String uri : uris) {
            if (uri.endsWith(Extensions.RM5)) {
                ret = uri;
                break;
            }
        }
        return ret;
    }

    public String getRawDataFile() throws IOException {
        String rawDataURIString = getRawDataURI();
        IRepository rps = RepositoryFactory.getRepository();
        return InputUtils.readStreamToString(rps.getFile(rawDataURIString));
    }

    public void saveRawData(String data) throws IOException {
        String rawDataURIString = getRawDataURI();
        IRepository rps = RepositoryFactory.getRepository();
        ByteArrayInputStream bais = new ByteArrayInputStream(data.getBytes());
        rps.putFile(rawDataURIString, bais);
    }

    public String getRawDataURI() {
        return FilePathBuilder.buildRawDataPathByUri(getRecordPath(), getName());
    }

    public List<AddonFile> getAddonFileList() {

        ImageLinksHelper imageHelper = new ImageLinksHelper(getRecordPath());
        List<String> uris = imageHelper.getImageList();

        List<AddonFile> list = new ArrayList<AddonFile>();

        for (String uri : uris) {
            try {
                tryAddAddonFile(uri, uri.substring(uri.lastIndexOf("/") + 1), list);
            } catch (IOException e) {
                LOG.debug(e, e);
            }
        }

        return list;
    }

    public void saveFile(String uri, InputStream data) {
        try {
            getRepository().putFile(uri, data);
        } catch (IOException e) {
            LOG.debug(e, e);
        }
    }

    public Integer getIssueId() {
        return db != null ? getDb().getIssue().getId() : null;
    }

    @Override
    public Integer getDeliveryFileId() {
        return deliveryFile != null ? getDeliveryFile().getId() : null;
    }

    public String getDbName() {
        return getDb().getTitle();
    }

    public String getDbShortName() {
        return getDb().getShortName();
    }

    public String getDbFullName() {
        return getDb().getFullName();
    }

    public String getUnitStatus() {
        return unitStatus;
    }

    public void setUnitStatus(String unitStatus) {
        this.unitStatus = unitStatus;
    }

    public String getProductSubtitle() {
        return productSubtitle;
    }

    public void setProductSubtitle(String productSubtitle) {
        this.productSubtitle = productSubtitle;
    }

    public void setLastTranslationUpdated() {
        lastSpecialUpdated = 1;
    }

    public boolean isLastTranslationUpdated() {
        return lastSpecialUpdated == 1;
    }

    public void setTranslationUpdated() {
        specialUpdated = 1;
    }

    public boolean isTranslationUpdated() {
        return specialUpdated == 1;
    }

    public void setMeshtermUpdated() {
        specialUpdated = 2;
    }

    public boolean isMeshtermUpdated() {
        return specialUpdated == 2;
    }

    public boolean isPublishingCanceled() {
        return RecordEntity.STATE_WAIT_WR_CANCELLED_NOTIFICATION == state;
    }

    private class ApproveAction extends AbstractAction {
        public int getId() {
            return Action.APPROVE_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("record.action.approve.name");
        }

        public void perform(IVisit visit) {
            LOG.debug("Approve action for " + getName() + STARTED);
            setRejected(false);
            setStateDescription("");
            setApproved(true);

            getResultStorage().mergeRecord(RecordWrapper.this);

            logAction(visit, ActivityLogEntity.EntityLevel.RECORD, ILogEvent.APPROVE, RecordWrapper.this.getId(),
                      RecordWrapper.this.getName());
        }
    }

    private class UnApproveAction extends AbstractAction {
        public int getId() {
            return Action.UN_APPROVE_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("record.action.unapprove.name");
        }

        public void perform(IVisit visit) {
            LOG.debug("UnApprove action for " + getName() + STARTED);
            setRejected(false);
            setStateDescription("");
            setApproved(false);
            getResultStorage().mergeRecord(RecordWrapper.this);

            if (!getDb().isExistsMinApprovedRecordsCount()) {
                getDb().performAction(Action.UN_APPROVE_ACTION, visit);
            }

            logAction(visit, ActivityLogEntity.EntityLevel.RECORD, ILogEvent.UNAPPROVE, RecordWrapper.this.getId(),
                      RecordWrapper.this.getName());
        }
    }

    private class RejectAction extends AbstractAction {

        RejectAction() {
            setCommentRequested(true);
        }

        public int getId() {
            return Action.REJECT_ACTION;
        }


        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("record.action.reject.name");
        }

        public void perform(IVisit visit) {
            LOG.debug("Reject action for " + getName() + STARTED);
            setApproved(false);
            setRejected(true);
            getResultStorage().mergeRecord(RecordWrapper.this);

            logAction(visit, ActivityLogEntity.EntityLevel.RECORD, ILogEvent.REJECT, RecordWrapper.this.getId(),
                      RecordWrapper.this.getName(), RecordWrapper.this.getStateDescription());
        }
    }

    private class UnRejectAction extends AbstractAction {
        public int getId() {
            return Action.UN_REJECT_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("record.action.unreject.name");
        }

        public void perform(IVisit visit) {
            LOG.debug("Unreject action for " + getName() + STARTED);
            setApproved(false);
            setRejected(false);
            setStateDescription("");
            getResultStorage().mergeRecord(RecordWrapper.this);

            logAction(visit, ActivityLogEntity.EntityLevel.RECORD, ILogEvent.UNREJECT, RecordWrapper.this.getId(),
                      RecordWrapper.this.getName());
        }
    }

    private class DeleteAction extends AbstractAction {

        DeleteAction() {
            setConfirmable(true);
        }

        public int getId() {
            return Action.DELETE_ACTION;
        }

        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("record.action.delete.name");
        }

        public void perform(IVisit visit) {
            IEntireDBStorage edbs = getEntireDbStorage();
            String cdNumber = RecordWrapper.this.getName();
            ClDbEntity clDb  = RecordWrapper.this.getDb().getEntity();

            boolean toImport = CmsUtils.isImportIssue(db.getIssue().getId());
            boolean spd = !toImport && CmsUtils.isScheduledIssue(db.getIssue().getId());
            if (!spd) {
                EntireDBEntity entireRecord = edbs.findRecordByName(clDb.getDatabase(), cdNumber);
                if (entireRecord != null && entireRecord.getLastIssuePublished() > clDb.getIssue().getFullNumber()) {
                    LOG.warn("deleting for %s from %s is not possible until it is deleted from Issue %d",
                            cdNumber, clDb, entireRecord.getLastIssuePublished());
                    return;
                }
            }

            LOG.debug(String.format("Delete action for %s %s", cdNumber, STARTED));

            List<String> cdNumbers = new ArrayList<>();
            cdNumbers.add(cdNumber);
            int recordId = RecordWrapper.this.getId();
            Integer cldbId = clDb.getId();
            try {
                Hooks.captureRecords(cldbId, cdNumbers, Hooks.DELETE_START);
                if (toImport) {
                    if (meta != null && !CmsUtils.isImportIssueNumber(meta.getIssue())) {
                        throw new Exception(String.format("metadata of %s is belong to another Issue - %d",
                                cdNumber, meta.getIssue()));
                    }
                }
                logAction(visit, ActivityLogEntity.EntityLevel.RECORD, ILogEvent.DELETE, recordId, cdNumber,
                        toImport ? "removed from 'Issue to Import' only" : "");
                BaseType bt = BaseType.find(getDb().getTitle()).get();
                RecordHelper.removeRecordFolders(bt, clDb.getIssue().getId(), RecordWrapper.this);
                getResultStorage().removeRecord(bt, recordId, spd, false);
                getDbStorage().updateRecordCount(getDb().getId());
                RecordWrapper.this.setId(0);

                if (!spd && !toImport) {
                    edbs.clearRecord(cldbId, cdNumber, bt.canPdfFopConvert());
                }

            } catch (Exception e) {
                LOG.error("Deleting record " + RecordWrapper.this.getId() + " failed. Exception: " + e);

            }  finally {
                CochraneCMSBeans.getRecordCache().removeRecord(getName(), spd);
                Hooks.captureRecords(cldbId, cdNumbers, Hooks.DELETE_END);
            }
        }
    }

    private class ResetAction extends AbstractAction {

        ResetAction() {
            setConfirmable(true);
            setConfirmMessage(CochraneCMSProperties.getProperty("confirm.perform.cdsr.action.process"));
        }

        public int getId() {
            return Action.RESET_ACTION;
        }

        public String getDisplayName() {
            return "Cancel process";
        }

        public void perform(IVisit visit) {
            LOG.debug("Reset process state for " + getName() + STARTED);
            try {
                CochraneCMSBeans.getRecordManager().cancelWhenReady(id, getName(), BaseType.find(
                        getDbName()).get().getProductType().buildRecordNumber(getName()), getDeliveryFile().getId());
                CochraneCMSBeans.getRecordCache().removeRecord(getName(), CmsUtils.isScheduledIssue(getIssueId()));

                logAction(visit, ActivityLogEntity.EntityLevel.FLOW, ILogEvent.CANCEL_PROCESS, getId(), getName(), "");

            } catch (Exception e) {
                LOG.error("Reset process state record " + RecordWrapper.this.getId() + " failed: " + e);
            }
        }
    }

    private class CommentAction extends AbstractAction {
        private static final int COMMENT_THRESHOLD = 96;

        CommentAction() {
        }

        public int getId() {
            return Action.COMMENT_ACTION;
        }


        public String getDisplayName() {
            return CochraneCMSProperties.getProperty("record.action.comment.name");
        }

        public void perform(IVisit visit) {
            LOG.debug("Comment action for " + getName() + STARTED);
            getResultStorage().mergeRecord(RecordWrapper.this);

            String comments = RecordWrapper.this.getNotes();

            if (comments != null && comments.length() > COMMENT_THRESHOLD) {
                comments = comments.substring(0, COMMENT_THRESHOLD) + " ...";
            }

            logAction(visit, ActivityLogEntity.EntityLevel.RECORD, ILogEvent.COMMENTED, RecordWrapper.this.getId(),
                      RecordWrapper.this.getName(), comments);
        }
    }
}