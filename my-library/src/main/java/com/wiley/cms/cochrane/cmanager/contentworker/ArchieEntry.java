package com.wiley.cms.cochrane.cmanager.contentworker;

import org.jdom.Element;

import com.wiley.cms.cochrane.activitylog.IFlowLogger;
import com.wiley.cms.cochrane.activitylog.FlowProduct;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.data.translation.PublishedAbstractEntity;
import com.wiley.cms.cochrane.cmanager.entity.TitleEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.cochrane.utils.ErrorInfo;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.KibanaUtil;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 25.10.12
 */
public class ArchieEntry implements java.io.Serializable {

    public static final String NONE_VERSION = "0.0";
    private static final long serialVersionUID = 1L;

    protected String version = NONE_VERSION;

    private int id = DbEntity.NOT_EXIST_ID;
    private final String cdNumber;
    private String archiveFileName;
    private String articleName;
    private int status = RecordMetadataEntity.RevmanStatus.NONE.dbKey;
    private String stage;
    private int pubNumber = Constants.FIRST_PUB;
    private String manuscriptNumber;

    private String path;

    private boolean wasNotified;
    private boolean wasReprocessed;
    private Integer titleId;

    public ArchieEntry(PublishedAbstractEntity pe) {
        this(pe.getRecordName(), pe.getPubNumber(), pe.getVersion());
        setId(pe.getId());
    }

    public ArchieEntry(String cdNumber, int pub, String version, int status) {
        this(cdNumber, pub, version);
        setStatus(status);
    }

    public ArchieEntry(String cdNumber, int pub, String version) {
        this(cdNumber, pub);
        setCochraneVersion(version);
    }

    public ArchieEntry(String cdNumber, Integer pub) {
        this(cdNumber);
        if (pub != null) {
            setPubNumber(pub);
        }
    }

    public ArchieEntry(String cdNumber) {
        this.cdNumber = cdNumber;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public PublishedAbstractEntity createWhenReadyEntity(BaseType baseType, Integer initialPackageId, Integer dbId,
                                                         Integer recId) {
        return new PublishedAbstractEntity(baseType, this, initialPackageId, dbId, recId);
    }

    public boolean same(PublishedAbstractEntity pae) {
        return pae.getRecordName().equals(getName()) && pae.getPubNumber() == getPubNumber()
                && pae.getVersion().equals(getCochraneVersion());
    }

    public Element asErrorElement(ArchieResponseBuilder rb, ErrorInfo err, IFlowLogger logger) {
        return onEventLog(rb.asErrorElement(this, err), rb, logger);
    }

    public Element asSuccessfulElement(ArchieResponseBuilder rb, IFlowLogger logger) {
        return onEventLog(rb.asSuccessfulElement(this), rb, logger);
    }

    protected final Element onEventLog(Element el, ArchieResponseBuilder rb, IFlowLogger logger) {
        if (logger != null && el != null) {
            if (rb.isOnPublish()) {
                logger.onDashboardEventStart(KibanaUtil.Event.NOTIFY_PUBLISHED, FlowProduct.State.PUBLISHED,
                        rb.getProcess(), null, getName(), getLanguage(), rb.sPD().is());
            } else {
                logger.onDashboardEventStart(KibanaUtil.Event.NOTIFY_ARCHIE_RECEIVED, FlowProduct.State.RECEIVED,
                        rb.getProcess(), rb.getPackageId(), getName(), getLanguage(), rb.sPD().is());
            }
        }
        return el;
    }

    public String getName() {
        return cdNumber;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return this.path;
    }

    public void setPubNumber(String pubNumber) {
        if (pubNumber.startsWith(Constants.PUB_PREFIX)) {
            this.pubNumber = Integer.valueOf(pubNumber.substring(Constants.PUB_PREFIX.length()));
        }
    }

    public void setPubNumber(int pubNumber) {
        this.pubNumber = pubNumber;
    }

    public int getPubNumber() {
        return pubNumber;
    }

    public void setManuscriptNumber(String manuscriptNumber) {
        this.manuscriptNumber = manuscriptNumber;
    }

    public String getManuscriptNumber() {
        return manuscriptNumber;
    }

    public void setDoi(String doi) {
    }

    public String getDoi() {
        return null;
    }

    public String getCochraneVersion() {
        return version == null ? NONE_VERSION : version;
    }

    public void setCochraneVersion(String version) {
        this.version = version;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isDeleted() {
        return RecordMetadataEntity.isDeleted(status);
    }

    public boolean isWithdrawn() {
        return RecordMetadataEntity.isWithdrawn(status);
    }

    public boolean isUnchanged() {
        return RecordMetadataEntity.isUnchanged(status);
    }

    public boolean isNew() {
        return RecordMetadataEntity.isNew(status);
    }

    public boolean isScheduled() {
        return false;
    }

    public void setDeleted(boolean deleted) {
        status = deleted ? RecordMetadataEntity.RevmanStatus.DELETED.dbKey
                : RecordMetadataEntity.RevmanStatus.NONE.dbKey;
    }

    public String getSid() {
        return getName();
    }

    public String getTitle() {
        return null;
    }

    public void setTitle(String title) {
    }

    public String getEnglishTitle() {
        return null;
    }

    public void setEnglishTitle(String title) {
    }

    public final Integer getTitleId() {
        return titleId;
    }

    public final void setTitleId(TitleEntity te) {
        if (te != null) {
            titleId = te.getId();
            setTitle(te.getTitle());
        }
    }

    public String getLanguage() {
        return null;
    }

    private static boolean isNullRevmanVersion(String version) {
        return version == null || version.equals(NONE_VERSION);
    }

    public boolean hasNullVersion() {
        return isNullRevmanVersion(getCochraneVersion());
    }

    public boolean wasNotified() {
        return wasNotified;
    }

    public void setWasNotified(boolean value) {
        this.wasNotified = value;
    }

    public boolean wasReprocessed() {
        return wasReprocessed;
    }

    public void setWasReprocessed(boolean value) {
        this.wasReprocessed = value;
    }

    public boolean isJatsTa() {
        return false;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public final String getPubName() {
        return RecordHelper.buildPubName(cdNumber, pubNumber);
    }

    public String getArchiveFileName() {
        return archiveFileName;
    }

    public void setArchiveFileName(String archiveFileName) {
        this.archiveFileName = archiveFileName;
    }

    public String getArticleName() {
        return articleName;
    }

    public void setArticleName(String articleName) {
        this.articleName = articleName;
    }

    @Override
    public String toString() {
        return getPubName();
    }
}
