package com.wiley.cms.cochrane.cmanager.entitywrapper;

import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import com.wiley.cms.cochrane.activitylog.UUIDEntity;
import com.wiley.cms.cochrane.cmanager.CmsException;
import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.CochraneCMSBeans;
import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.data.PublishEntity;
import com.wiley.cms.cochrane.cmanager.data.PublishTypeEntity;
import com.wiley.cms.cochrane.cmanager.data.issue.IssueVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.AbstractManager;
import com.wiley.cms.cochrane.cmanager.publish.IPublish;
import com.wiley.cms.cochrane.cmanager.publish.IPublishStorage;
import com.wiley.cms.cochrane.cmanager.publish.PublishHelper;
import com.wiley.cms.cochrane.cmanager.publish.PublishStorageFactory;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.cmanager.res.PubType;
import com.wiley.cms.cochrane.cmanager.res.PublishProfile;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.MessageBuilder;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.DbUtils;
import com.wiley.tes.util.Logger;

/**
 * @author <a href='mailto:ikirov@wiley.com'>Igor Kirov</a>
 * @version 18.12.2009
 */
public final class PublishWrapper implements java.io.Serializable {
    private static final Logger LOG = Logger.getLogger(PublishWrapper.class);
    private static final long serialVersionUID = 1L;

    protected PublishProfile.PubLocationPath path;
    protected String newPackageName;

    protected boolean send;
    protected boolean generate;
    protected boolean unpack;
    protected boolean delete;

    protected boolean staticContentDisabled;
    protected boolean onlySpecialContent;

    private int deliveryFileId = DbEntity.NOT_EXIST_ID;
    private int recordsProcessId;
    private Date startDate;

    private PublishEntity entity;
    private PublishWrapper next;
    private PublishWrapper pwAwait;

    private byte await;  // 0 - not awaiting; 1 - awaiting for ... (LIT event), 2 - can not to await anymore

    private Set<String> names;

    private MessageBuilder mb = new MessageBuilder();

    private int oldFeedId = DbEntity.NOT_EXIST_ID;

    private boolean scopeSkipped;

    private String hwFrequency;

    private String transactionId;

    private PublishWrapper(PublishProfile.PubLocationPath path, String dbName, boolean inc) {

        this(path);

        Integer pType = PublishTypeEntity.getNamedEntityId(PublishProfile.buildExportDbName(getType(), false, inc));
        IPublishStorage ps = PublishStorageFactory.getFactory().getInstance();
        initEntity(ps.takeEntirePublishByDbAndType(dbName, pType));
        if (inc) {
            resetPublishEntity();
        }
    }

    private PublishWrapper(int dbId, PublishProfile.PubLocationPath path, boolean whenReady, boolean inc) {

        this(path);

        IPublishStorage ps = PublishStorageFactory.getFactory().getInstance();

        String publishType = PublishProfile.buildExportDbName(getType(), whenReady, inc);
        Integer pType = PublishTypeEntity.getNamedEntityId(publishType);

        if (whenReady || inc) {
            initEntity(new PublishEntity(null, pType));
        } else {
            initEntity(ps.takePublishByDbAndType(dbId, pType));
        }
    }

    private PublishWrapper(PublishEntity pe, PublishProfile.PubLocationPath path) {
        this(path);
        setPublishEntity(pe);
    }

    private PublishWrapper(PublishProfile.PubLocationPath path) {
        initWorkflow(false, false, false, false);
        this.path = path;
    }

    public static PublishWrapper createEntirePublishWrapper(String type, String majorType, String dbName,
        boolean inc, Date startDate) throws CmsException {

        PublishProfile.PubLocationPath path = PublishProfile.getProfile().get().getEntirePubLocation(
                majorType, type, dbName);
        if (path == null) {
            throw new CmsException(
                    String.format("cannot find publication path for %s-%s of %s", type, majorType, dbName));
        }

        PublishWrapper pw = new PublishWrapper(path, dbName, inc);
        pw.setStartDate(startDate);

        if (path.getPubType().isLiteratum()) {
            pw.initOnlySpecialContent(false, false, inc);
            pw.setNewPackageName(getLiteratumPackageName(dbName, pw.isOnlyNewContent(), pw.getStartDate(),
                    path.getArchive()));

        } else if (path.getPubType().canGenerate()) {
            IssueVO issueVO = new IssueVO(AbstractManager.getResultStorage().getLastApprovedDatabaseIssue(dbName));

            String nowParamVal;
            PublishProfile.PubLocationPath tmpPath;

            if (inc) {
                nowParamVal = PublishHelper.buildNowParam(dbName, pw.getStartDate(), false);
                tmpPath = PublishProfile.getProfile().get().getIssuePubLocation(majorType, type, dbName);

            } else {
                boolean hw = PubType.isSemantico(path.getPubType().getMajorType());
                boolean hwTop = hw && PubType.TYPE_SEMANTICO_TOPICS.equals(type);

                nowParamVal = PublishHelper.buildNowParam(dbName, pw.getStartDate(), !hwTop);
                tmpPath = path;
            }
            issueVO.setPublishDate(pw.getStartDate());
            pw.setNewPackageName(getPackageName(tmpPath, dbName, issueVO, pw.getDeliveryFileId(), nowParamVal, false,
                    pw.isOnlyNewContent()));
        }

        return pw;
    }

    public static PublishWrapper createPublishWrapperSelective(PubType pt, String dbName, boolean entire,
                                                               IssueVO issueVO, Date startDate) {
        String type = pt.getId();
        PublishProfile.PubLocationPath path = PublishProfile.getProfile().get().getPubLocation(
                type, dbName, entire, false);
        if (path == null) {
            LOG.error(String.format("publishing location for %s of %s is null", pt, dbName));
            return null;
        }
        PublishWrapper pw = new PublishWrapper(path);
        pw.setStartDate(startDate);

        if (path.getPubType().canGenerate()) {
            PublishProfile.PubLocationPath tmpPath = PublishProfile.getProfile().get().getIssuePubLocation(
                    pt.getMajorType(), type, dbName);
            boolean lit = path.getPubType().isLiteratum();
            if (lit) {
                pw.initOnlySpecialContent(false, false, true);
            }
            issueVO.setPublishDate(pw.getStartDate());
            pw.setNewPackageName(getPackageName(tmpPath, dbName, issueVO, pw.getDeliveryFileId(),
                PublishHelper.buildNowParam(dbName, startDate, false), lit, pw.isOnlyNewContent()));
        }
        return pw;
    }

    public static PublishWrapper createIssuePublishWrapper(String type, String dbName, int dbId, boolean wr,
        boolean inc, Date startDate) {
        return createIssuePublishWrapper(type, dbName, dbId, DbEntity.NOT_EXIST_ID, startDate, wr, inc);
    }

    public static PublishWrapper createIssuePublishWrapper(String type, String dbName, int dbId, int dfId,
                                                           Date startDate, boolean wr, boolean inc) {
        try {
            return createIssuePublishWrapperEx(type, dbName, dbId, dfId, startDate, new boolean[] {wr, inc, false});
        } catch (Exception e) {
            LOG.error(e);
            return null;
        }
    }

    public static PublishWrapper createPublishWrapperToResendOnly(PublishEntity pe, String type, boolean wrFolder) {
        PublishProfile.PubLocationPath path = PublishProfile.getProfile().get().getPubLocation(type,
                pe.getDb().getTitle(), pe.getDb().isEntire(), wrFolder);

        PublishWrapper pw = new PublishWrapper(pe, path);
        pw.setStartDate(pe.getGenerationDate());
        pw.setNewPackageName(pe.getFileName());
        pw.setGenerate(false);
        pw.setSend(true);
        pw.setDeliveryFileId(IPublish.BY_WHEN_READY); // an automatic re-sending without deliveryId must have it
        return pw;
    }

    /**
     * Create a publish wrapper object containing all the details needed to generate and send a publishing package
     * @param pubType     a publication type
     * @param dbName      a database name
     * @param dbId        a database identifier
     * @param dfId        a database id related to this publishing
     * @param startDate   a date of publishing start
     * @param options     optional flags where [0] means real-time publishing with some specific logic related to
     *                                             when-ready delivery only,
     *                                         [1] means incremental publishing by record specified ,
     *                                         [2] means automated sending dsMonthlyCentralAut that would include only
     *                                             confirmed by HW CONTENT_ONLINE DOIs
     * @return a publish wrapper prepared
     * @throws Exception
     */
    public static PublishWrapper createIssuePublishWrapperEx(String pubType, String dbName, int dbId, int dfId,
                                                             Date startDate, boolean[] options) throws Exception {
        boolean wr = options[0];
        boolean inc = options[1];
        boolean dsMonthlyCentralAut = options[2];
        PublishProfile.PubLocationPath path = PublishProfile.getProfile().get().getPubLocation(
                pubType, dbName, false, wr);

        if (path == null) {
            throw new Exception(String.format("issue publishing location for %s of %s is null", pubType, dbName));
        }

        PublishWrapper pw = new PublishWrapper(dbId, path, wr, inc);
        pw.setStartDate(startDate);
        pw.setDeliveryFileId(dfId);

        if (path.getPubType().isAriesAck()) {
            return pw;
        }

        if (path.getPubType().canGenerate()) {

            IssueVO issueVO = new IssueVO(new DbWrapper(dbId).getIssue());
            Date pubDate;
            String nowParamVal;
            boolean lit = !dsMonthlyCentralAut && path.getPubType().isLiteratum();
            boolean hw = !lit && PubType.isSemantico(path.getPubType().getMajorType());
            boolean hwTop = hw && PubType.TYPE_SEMANTICO_TOPICS.equals(pubType);
            if (lit) {
                pw.initOnlySpecialContent(false, wr, inc);
            } else if (dsMonthlyCentralAut) {
                pw.initOnlySpecialContent(dsMonthlyCentralAut, wr, inc);
            }
            if (wr || lit || inc || hwTop) {
                pubDate = pw.getStartDate();
                nowParamVal = PublishHelper.buildNowParam(dbName, pubDate, false);
            } else {
                pubDate = issueVO.getPublishDate();
                nowParamVal = PublishHelper.buildNowParam(dbName, new Date(), true);
            }
            issueVO.setPublishDate(pubDate);
            pw.setNewPackageName(getPackageName(path, dbName, issueVO, pw.getDeliveryFileId(), nowParamVal, lit,
                    pw.isOnlyNewContent()));
        }
        return pw;
    }

    private static String getPackageName(PublishProfile.PubLocationPath path, String dbName, IssueVO issueVO,
        int dfId, String nowParamValue, boolean lit, boolean onlyNewContent) {

        String issueNumberFmt = Constants.ISSUE_NUMBER_FORMAT;
        LocalDate spdDate = CmsUtils.isScheduledIssue(issueVO.getId()) ? LocalDate.now() : null;

        Map<String, String> replaceList = new HashMap<>();
        replaceList.put("issue", String.format(issueNumberFmt,
                spdDate != null ? LocalDate.now().getMonthValue() : issueVO.getNumber()));
        replaceList.put("year", String.valueOf(spdDate != null ? spdDate.getYear() : issueVO.getYear()));
        replaceList.put("dbname", dbName);
        PublishHelper.addCommonAttrs(nowParamValue, replaceList);

        replaceList.put("publishdate", PublishHelper.buildNowParam(dbName, issueVO.getPublishDate(), false));
        replaceList.put("publishdateshort", PublishHelper.buildNowParam(dbName, issueVO.getPublishDate(), true));

        String pubType = path.getPubType().getId();
        if (lit) {
            PublishHelper.addLiteratumAttrs(dbName, onlyNewContent, replaceList);

        } else if (PubType.TYPE_SEMANTICO_DELETE.equals(pubType)) {
            replaceList.put("tail", DbUtils.exists(dfId) ? "_" + dfId : "");

        } else if (PubType.TYPE_UDW.equals(pubType)) {
            PublishHelper.addUdwAttrs(dbName, replaceList);
        } else {
            replaceList.put(UUIDEntity.UUID, UUIDEntity.UUID);  // currently it is used for DS only
        }
        return PublishProfile.buildExportFileName(path.getArchive(), replaceList);
    }

    public static String getLiteratumPackageName(String dbName, boolean newContent, Date now, String template) {
        Map<String, String> replaceList = new HashMap<>();

        PublishHelper.addCommonAttrs(PublishHelper.buildNowParam(dbName, now, false), replaceList);
        PublishHelper.addLiteratumAttrs(dbName, newContent, replaceList);

        return PublishProfile.buildExportFileName(template, replaceList);
    }

    public void initWorkflow(boolean gen, boolean send, boolean unpack, boolean delete)  {
        this.send = send;
        this.generate = gen;
        this.unpack = unpack;
        this.delete = delete;
    }

    public boolean setPublishToAwait(PublishWrapper pw) {
        return setPublishToAwait(pw, false);
    }

    public boolean setPublishToAwait(PublishWrapper pw, boolean centralHW) {
        if (pw != null) {
            if (centralHW || (isGenerate() && pw.isGenerate() && isSend() && pw.isSend())) {
                pwAwait = pw;
                pwAwait.mb = mb;
                pw.startToAwait();
                return true;
            }
        } else {
            pwAwait = null;
        }
        return false;
    }

    public PublishWrapper getPublishToAwait() {
        return pwAwait;
    }

    public boolean hasPublishToAwait() {
        return pwAwait != null;
    }

    public void stopToAwait() {
        await = 2;
    }

    private void startToAwait() {
        await = 1;
    }

    private boolean wasToAwait() {
        return await > 0;
    }

    private boolean isAwaitStopped() {
        return await == 2;
    }

    /**
     * @return  It returns a publish wrapper awaiting for publishing this.
     *          NULL means that it's included into current publish wrapper chain and it's not awaiting anymore .
     */
    public PublishWrapper checkPublishAwaitCompleted() {

        PublishWrapper pw = getPublishToAwait();
        if (pw.isAwaitStopped()) {
            // nothing to await
            setPublishToAwait(null);
        }
        boolean hasRecordsToPublish = pw.hasCdNumbers() || DbUtils.exists(pw.getRecordsProcessId());
        if (pw.wasToAwait() && hasRecordsToPublish) {
            // there is a few records to publish right now
            pw.await = 0;
            insertNext(pw);
            return null;
        }
        return pw;
    }

    public boolean hasCdNumbers() {
        return names != null && !names.isEmpty();
    }

    public void setCdNumbers(Set<String> names) {
        this.names = names;
    }

    public Set<String> getCdNumbers() {
        return names;
    }

    public Set<String> takeCdNumbers() {
        return names != null ? new HashSet<>(names) : null;
    }

    public boolean hasMessage() {
        return !mb.isEmpty();
    }

    public void addSubMessages(String mainMessage, String... messages) {
        mb.addSubMessages(mainMessage, messages);
    }

    public void addSubMessage(String message) {
        mb.addSubMessage(message);
    }

    public void addMessage(String message) {
        mb.addMessage(message);
    }

    public void sendMessages() {
        if (mb.isEmpty()) {
            return;
        }

        if (hasPublishToAwait() && PubType.MAJOR_TYPE_S.equals(pwAwait.getMajorType())) {
            mb.addMessage(CochraneCMSProperties.getProperty("sending.hw.await", ""));
        }

        mb.sendMessages(getPath().getBaseType().getId(), getType());
    }

    public PublishProfile.PubLocationPath getPath() {
        return path;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public void setStartSendingAndUnpackingDates(Date date, boolean send, boolean unpack) {
        if (send) {
            entity.setStartSendingDate(date);
        }
        if (unpack) {
            entity.setStartUnpackingDate(date);
        }
    }

    public boolean isStaticContentDisabled() {
        return staticContentDisabled;
    }

    public void setStaticContentDisabled(boolean staticContentDisabled) {
        this.staticContentDisabled = staticContentDisabled;
    }

    public boolean isUnpack() {
        return unpack;
    }

    public void setUnpack(boolean unpack) {
        this.unpack = unpack;
    }

    public boolean isSend() {
        return send;
    }

    public void setSend(boolean send) {
        this.send = send;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public String getStatus() {
        String status = "";
        if (entity.isWaiting()) {
            status = "waiting";
        } else if (entity.isGenerating()) {
            status = "generating";
        } else if (entity.isUnpacking()) {
            status = "unpacking";
        } else if (entity.isSending()) {
            status = "sending";
        } else if (entity.isDeleting()) {
            status = "deleting";
        }
        return status;
    }

    public void setPubLocationPath(PublishProfile.PubLocationPath path) {
        this.path = path;
    }

    // Use for LIT
    public boolean isOnlyNewContent() {
        return onlySpecialContent;
    }

    // Use for DS Central
    public boolean isOnlyPublishedContent() {
        return onlySpecialContent;
    }

    private void initOnlySpecialContent(boolean dsMonthlyCentral, boolean wr, boolean inc) {
        onlySpecialContent = dsMonthlyCentral ? !inc : (wr || !inc);
    }

    public String getType() {
        return path.getPubType().getId();
    }

    public boolean isGenerate() {
        return generate;
    }

    public void setGenerate(boolean generate) {
        this.generate = generate;
    }

    public String getHWFrequency() {
        return hwFrequency;
    }

    public void setHWFrequency(String hwFrequency) {
        this.hwFrequency = hwFrequency;
    }

    public void setHWFrequencyUI(String hwFrequency) {
        setHWFrequency(HWFreq.valueOf(hwFrequency).getValue());
    }

    public String getHWFrequencyUI() {
        return hwFrequency;
    }


    public String getMajorType() {
        return path.getPubType().getMajorType();
    }

    public Date getGenerationDate() {
        return entity.getGenerationDate();
    }

    public Date getStartUnpackingDate() {
        return entity.getStartUnpackingDate();
    }

    public Date getUnpackingDate() {
        return entity.getUnpackingDate();
    }

    public Date getStartSendingDate() {
        return entity.getStartSendingDate();
    }

    public Date getSendingDate() {
        return entity.getSendingDate();
    }

    public boolean isSending() {
        return entity.isSending();
    }

    public boolean isGenerating() {
        return entity.isGenerating();
    }

    public boolean isUnpacking() {
        return entity.isUnpacking();
    }

    public boolean isWaiting() {
        return entity.isWaiting();
    }

    public boolean isDeleting() {
        return entity.isDeleting();
    }

    public String getUiName() {
        PubType pt = path.getPubType();
        String uiName = pt.getUIName();
        return isOnlyNewContent() && pt.isLiteratum() ? uiName + " (New for Literatum)" : uiName;
    }

    public String getFileNameUI() {
        if (isPublishEntityExist() && getGenerationDate() != null) {
            String fileName = getFileName();
            if (fileName != null && !fileName.isEmpty()) {
                //return String.format("%s\n[%s]", getFileName(), getFileSize());
                return fileName;
            }
        }
        return null;
    }

    public String getFileSizeUI() {
        String ret = "";
        String fileName = getFileNameUI();
        if (fileName == null) {
            return ret;
        }
        long size = entity.getFileSize();
        if (hasRelatedPackages()) {
            List<PublishEntity> list = getRelatedPackages();
            for (PublishEntity pe: list) {
                size += pe.getFileSize();
            }
            ret = String.format("[%s in %d files]", FileUtils.byteCountToDisplaySize(size), list.size() + 1);
        } else {
            ret = String.format("[%s]", FileUtils.byteCountToDisplaySize(size));
        }
        return ret;
    }

    public String getFileName() {
        return entity != null ? entity.getFileName() : null;
    }

    public int getId() {
        return entity != null ? entity.getId() : DbEntity.NOT_EXIST_ID;
    }

    public boolean isPublishEntityExist() {
        return entity != null && !isNewPublish();
    }

    public boolean isNewPublish() {
        return !entity.exists();
    }

    public void setPublishEntity(PublishEntity entity) {
        this.entity = entity;
    }

    public void resetPublishEntity() {
        if (isPublishEntityExist()) {
            oldFeedId = entity.getId();
            entity.setId(DbEntity.NOT_EXIST_ID);
        }
    }

    public PublishEntity getPublishEntity() {
        return entity;
    }

    public int getDeliveryFileId() {
        return deliveryFileId;
    }

    public void setDeliveryFileId(int dfId) {
        deliveryFileId = dfId;
    }

    public int getRecordsProcessId() {
        return recordsProcessId;
    }

    public void setRecordsProcessId(int rpId) {
        recordsProcessId = rpId;
    }

    public String getNewPackageNameUI() {
        if (newPackageName == null || newPackageName.trim().isEmpty() || PubType.TYPE_LITERATUM.equals(getType())
                || CochraneCMSPropertyNames.getCcaDbName().equals(getPath().getBaseType().getId())) {
            return Constants.NA;
        }
        return newPackageName;
    }

    public String getNewPackageName() {
        return newPackageName;
    }

    public void setNewPackageName(String newPackageName) {
        this.newPackageName = newPackageName;
    }

    public void setNext(PublishWrapper next) {
        this.next = next;
        if (next != null) {
            next.mb = this.mb;
        }
    }

    private void insertNext(PublishWrapper next) {
        next.setNext(this.next);
        this.next = next;
    }

    public PublishWrapper getNext() {
        return next;
    }

    public boolean hasNext() {
        return next != null;
    }

    public boolean byRecords() {
        return recordsProcessId > 0;
    }

    public boolean canGenerate() {
        return path.getPubType().canGenerate();
    }

    private void initEntity(PublishEntity pe) {
        entity = pe;
        if (!entity.isGenerated() && !path.getPubType().canGenerate()) {
            entity.setGenerated(true);
        }
        oldFeedId = entity.getId();
    }

    public void resetPublishForWaiting(IPublishStorage ps) {
        if (isPublishEntityExist()) {
            ps.updatePublishForWaiting(getId(), false);
        }
        ps.updatePublishForWaiting(oldFeedId, false);
    }

    public boolean hasRelatedPackages() {
        return entity.hasRelation() && path.getPubType().canHaveSubPackages();
    }

    public List<PublishEntity> getRelatedPackages() {
        return CochraneCMSBeans.getPublishStorage().getRelatedPublishList(entity.getParentId(), entity.getId());
    }

    public void setScopeSkipped(boolean value) {
        scopeSkipped = value;
    }

    public boolean isScopeSkipped() {
        return scopeSkipped;
    }

    public boolean isDS() {
        return PubType.isDS(getMajorType());
    }

    public boolean isWOLLIT() {
        return PubType.isLiteratum(getMajorType());
    }

    public boolean isHW() {
        return PubType.isSemantico(getMajorType());
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
}
