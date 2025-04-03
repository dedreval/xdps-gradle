package com.wiley.cms.cochrane.cmanager.data.translation;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.wiley.cms.cochrane.activitylog.FlowProduct;
import com.wiley.cms.cochrane.activitylog.IFlowProduct;
import com.wiley.cms.cochrane.activitylog.snowflake.ISFProductPart;
import com.wiley.cms.cochrane.activitylog.snowflake.SFType;
import com.wiley.cms.cochrane.cmanager.FilePathBuilder;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.contentworker.AriesHelper;
import com.wiley.cms.cochrane.cmanager.data.TranslatedAbstract;
import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.cmanager.publish.generate.semantico.HWFreq;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.cmanager.translated.TranslatedAbstractVO;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.process.entity.DbEntity;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 04.08.12
 */
@Entity
@Table(name = "COCHRANE_TRANSLATED_ABSTRACTS_PUBLISHED")

@NamedQueries({
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_BY_IDS,  // on published
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE pae.id IN (:ids) ORDER BY pae.id"
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_BY_PUBLISH_DB_AND_NUMBERS,
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE pae.dbId =:db AND pae.notified < "
                    + PublishedAbstractEntity.NOT_NOTIFIED + " AND pae.number IN (:nu) ORDER BY pae.number, pae.id DESC"
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_BY_DF,
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE (pae.deliveryId =:df"
                    + " OR pae.initialDeliveryId =:df) AND pae.notified < " + PublishedAbstractEntity.NOT_NOTIFIED
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_BY_DB,   // on PWR
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE pae.dbId =:db AND pae.notified < "
                        + PublishedAbstractEntity.NOT_NOTIFIED
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_BY_COCHRANE_NOTIFICATION,
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE "
                        + "pae.cochraneNotificationFileName =:notificationFileName ORDER BY pae.date DESC"
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_BY_DB_AND_MANUSCRIPT_NUMBERS_OR_EMPTY_ACK,   
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE pae.dbId IN (:db)"
                        + " AND pae.manuscriptNumber IS NOT NULL AND (pae.manuscriptNumber IN (:mu)"
                        + " OR pae.acknowledgementId IS NULL) ORDER BY pae.id DESC"
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_BY_DB_AND_EMPTY_ACK,
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE pae.dbId IN (:db)"
                        + " AND pae.manuscriptNumber IS NOT NULL AND pae.acknowledgementId IS NULL ORDER BY pae.id DESC"
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_BY_DF_AND_NAME,
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE pae.deliveryId =:df AND pae.recordName =:na "
                        + " ORDER BY pae.id DESC"
        ),
        @NamedQuery(
            name = PublishedAbstractEntity.QUERY_SELECT_BY_DF_AND_NAMES,
            query = "SELECT pae FROM PublishedAbstractEntity pae WHERE pae.deliveryId =:df AND pae.recordName IN (:na)"
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_BY_DB_AND_NAME,
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE pae.dbId =:db AND pae.recordName =:na"
                        + " ORDER BY pae.date DESC"
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_BY_DB_AND_NAMES,               
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE pae.dbId =:db AND pae.recordName IN (:na)"
                        + " ORDER BY pae.date DESC"
        ),
        @NamedQuery(
            name = PublishedAbstractEntity.QUERY_SELECT_NOT_NOTIFIED,
            query = "SELECT pae FROM PublishedAbstractEntity pae WHERE pae.dbId IN (:db) AND pae.notified < "
                + PublishedAbstractEntity.PUB_NOTIFIED
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_NOT_NOTIFIED_BY_NUMBER_AND_PUB,
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE"
                        + "((pae.notified < " + PublishedAbstractEntity.PUB_NOTIFIED + ")"
                        + "OR pae.notified IN (1000, 1004))"
                        + "AND pae.number =:nu AND pae.pubNumber =:pu"
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_DUPLICATES_BY_NUMBER_AND_PUB_AND_VERSION,
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE"
                        + " pae.number =:nu AND pae.pubNumber =:pu AND pae.version =:ver"
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_DUPLICATES_BY_NUMBER_AND_PUB_AND_VERSION_AND_LANG_AND_SID,
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE"
                        + " pae.number =:nu AND pae.pubNumber =:pu AND pae.version =:ver "
                        + "AND pae.language =:lang AND pae.sid =: sid"
        ),
        @NamedQuery(
                name = PublishedAbstractEntity.QUERY_SELECT_BY_NUMBER,
                query = "SELECT pae FROM PublishedAbstractEntity pae WHERE pae.number =:nu"
        ),
        @NamedQuery(
            name = PublishedAbstractEntity.QUERY_UPDATE_PUBLISH_CANCEL_NOTIFIED_BY_DF_AND_NAME,
            query = "UPDATE PublishedAbstractEntity pae"
                + " SET pae.notified=pae.notified % " + PublishedAbstractEntity.CANCEL_NOTIFIED + " + "
                    + PublishedAbstractEntity.CANCEL_NOTIFIED + " WHERE pae.deliveryId =:df AND pae.recordName =:na"
        ),
        @NamedQuery(
            name = PublishedAbstractEntity.QUERY_DELETE_BY_DB,
            query = "DELETE FROM PublishedAbstractEntity pae WHERE pae.dbId =:db"
        ),
        @NamedQuery(
            name = PublishedAbstractEntity.QUERY_DELETE_BY_DB_AND_NAME,
            query = "DELETE FROM PublishedAbstractEntity pae WHERE pae.dbId =:db AND pae.recordName =:na"
        )
    })
public class PublishedAbstractEntity extends TranslatedAbstract implements IFlowProduct, java.io.Serializable {

    public static final int NOT_NOTIFIED = 100;
    public static final int REC_NOTIFIED_ON_FAIL = 101;
    public static final int REC_NOTIFIED = 0;
    public static final int PUB_NOTIFIED = 10;
    public static final int CANCEL_NOTIFIED = 1000;

    // use only for detect and set NOTIFIED (16)
    // PUB_NOTIFIED + PublishDestination.LITERATUM_HW.ordinal()
    public static final int DUPLICATED_HW_RECEIVING = 8;
    static final String QUERY_SELECT_BY_IDS = "selectAbstractsByIds";
    static final String QUERY_SELECT_BY_DB = "selectAbstractsByDb";
    static final String QUERY_SELECT_BY_COCHRANE_NOTIFICATION = "selectAbstractByCochraneNotification";
    static final String QUERY_SELECT_BY_NUMBER = "selectAbstractsByNumber";
    static final String QUERY_SELECT_BY_DB_AND_NAME = "selectAbstractsByDbAndName";
    static final String QUERY_SELECT_BY_DB_AND_NAMES = "selectAbstractsByDbAndNames";
    static final String QUERY_SELECT_BY_PUBLISH_DB_AND_NUMBERS = "selectAbstractsByPublishDbAndNumbers";
    static final String QUERY_SELECT_NOT_NOTIFIED = "selectNotNotifiedAbstracts";
    static final String QUERY_SELECT_NOT_NOTIFIED_BY_NUMBER_AND_PUB = "selectNotNotifiedByNumberAndPub";
    static final String QUERY_SELECT_DUPLICATES_BY_NUMBER_AND_PUB_AND_VERSION
            = "selectAbstractsByNumberAndPubAndVersion";
    static final String QUERY_SELECT_DUPLICATES_BY_NUMBER_AND_PUB_AND_VERSION_AND_LANG_AND_SID
            = "selectAbstractsByNumberAndPubAndVersionAndLangAndSid";
    static final String QUERY_SELECT_BY_DF =  "selectAbstractsByDf";
    static final String QUERY_SELECT_BY_DF_AND_NAME = "selectAbstractsByDfAndName";
    static final String QUERY_SELECT_BY_DF_AND_NAMES = "selectAbstractsByDfAndNames";
    static final String QUERY_SELECT_BY_DB_AND_MANUSCRIPT_NUMBERS_OR_EMPTY_ACK =
            "selectAbstractsByDbAndManuscriptNumbersOrEmptyAck";
    static final String QUERY_SELECT_BY_DB_AND_EMPTY_ACK = "selectAbstractsByDbAndEmptyAck";
    static final String QUERY_UPDATE_PUBLISH_CANCEL_NOTIFIED_BY_DF_AND_NAME = "updatePublishCancelNotifiedByDfAndName";
    static final String QUERY_DELETE_BY_DB = "deleteAbstractsByDb";
    static final String QUERY_DELETE_BY_DB_AND_NAME = "deleteAbstractsByDbAndName";

    //static final String QUERY_UPDATE_ABSTRACTS_DATE_BY_TYPE = "UPDATE COCHRANE_TRANSLATED_ABSTRACTS_PUBLISHED abe"
    //    + " JOIN COCHRANE_PUBLISH p ON (abe.publish_id = p.id) SET abe.date =:publish WHERE abe.publishDate IS NULL"
    //        + " AND p.type_id =:pubtype AND abe.pub =:pub AND abe.recordName =:recordName";

    /** get unpublished yet record's identifiers from one destination with already published identifiers from another */
    //private static final String QUERY_SELECT_UNPUBLISHED_WITH_2_DEST_PUBLISHED =
    //    "SELECT a.id FROM COCHRANE_TRANSLATED_ABSTRACTS_PUBLISHED a JOIN COCHRANE_PUBLISH p ON (a.publish_id=p.id)"
    //        + " JOIN COCHRANE_TRANSLATED_ABSTRACTS_PUBLISHED a1 ON (a1.recordName=a.recordName AND a1.pub=a.pub"
    //            + " AND a1.publish_id != a.publish_id AND a1.date IS NOT NULL)"
    //        + " JOIN COCHRANE_PUBLISH p1 ON (a1.publish_id=p1.id AND p1.generationDate=p.generationDate)"
    //        + " WHERE a.date IS NOT NULL AND a.publishDate IS NULL ORDER BY a.id DESC";

    private static final String QUERY_UPDATE_ON_RECEIVED = "UPDATE COCHRANE_TRANSLATED_ABSTRACTS_PUBLISHED pae"
        + " LEFT JOIN COCHRANE_RECORD r ON (r.id=pae.record_id) SET pae.notified=:no, r.state=:st WHERE pae.id IN(:id)";

    //private static final String QUERY_UPDATE_DF_ON_RECEIVED = "UPDATE COCHRANE_RECORD r"
    //    + " LEFT JOIN COCHRANE_TRANSLATED_ABSTRACTS_PUBLISHED pae ON (r.id=pae.record_id AND pae.df_id=:if)"
    //    + " SET pae.df_id=:df, r.delivery_file_id =:df WHERE r.delivery_file_id=:if AND r.name IN (:na)";

    private Integer dbId;
    private Integer initialDfId;
    private Integer ackDfId;
    private String manuscriptNumber;

    private Integer recordId;
    private Date publishDate;
    private String recordName;

    // 1xxx - WR article was reset (canceled process)
    // 100 - hasn't been notified on received
    // 0 - notified on received
    // 1 | 2 | 3 - published to WOL or HW or (WOL & HW) but hasn't been notified on published yet for some reasons
    // 10 - notified on published
    // 101 - notified on received with failure
    // 11 | 12 | 13 | 16 - notified on published to WOL or HW or (WOL & HW) with success
    private int notified = NOT_NOTIFIED;

    private int highPriority = 0;

    private FlowProduct.SPDState spd = FlowProduct.SPDState.NONE;

    private String cochraneNotificationFileName;
    public PublishedAbstractEntity() {
    }

    public PublishedAbstractEntity(BaseType baseType, ArchieEntry ae, Integer initialPackageId, Integer dbId,
                                   Integer recordId) {
        this(ae.getName(), baseType.getProductType().buildRecordNumber(ae.getName()), ae.getCochraneVersion(),
                ae.getPubNumber(), initialPackageId, dbId, recordId);
        setManuscriptNumber(ae.getManuscriptNumber());
    }

    public PublishedAbstractEntity(TranslatedAbstractVO tvo, Integer initialPackageId, Integer dbId, Integer recordId) {
        this(tvo.getName(), RecordHelper.buildRecordNumberCdsr(tvo.getName()), tvo.getCochraneVersion(),
                tvo.getPubNumber(), initialPackageId, dbId, recordId);
        setLanguage(tvo.getLanguage());
        setSid(tvo.getSid());
    }

    private PublishedAbstractEntity(String recordName, int number, String version, int pubNumber,
                                    Integer initialPackageId, Integer dbId, Integer recordId) {
        setRecordName(recordName);
        setVersion(version);
        setPubNumber(pubNumber);
        setDeliveryId(initialPackageId);
        setInitialDeliveryId(initialPackageId);
        setDbId(dbId);
        setRecordId(recordId);
        setDate(new Date());
        setNumber(number);
    }

    public static Query queryAbstractsByIds(Collection<Integer> ids, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_IDS).setParameter("ids", ids);
    }

    public static Query queryPublishedAbstracts(int recordNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_NUMBER).setParameter("nu", recordNumber);
    }

    public static Query queryPublishedAbstracts(Integer dbId, Collection<Integer> numbers, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_PUBLISH_DB_AND_NUMBERS).setParameter(
                "db", dbId).setParameter("nu", numbers);
    }

    public static Query queryUnpublishedAbstracts(Collection<Integer> dbIds, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_NOT_NOTIFIED).setParameter("db", dbIds);
    }

    public static Query queryAbstractsNoAcknowledgement(Collection<Integer> dbIds,
            Collection<String> manuscriptNumbers, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_MANUSCRIPT_NUMBERS_OR_EMPTY_ACK).setParameter(
                "db", dbIds).setParameter("mu", manuscriptNumbers);
    }

    public static Query queryAbstractsNoAcknowledgement(Collection<Integer> dbIds, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_EMPTY_ACK).setParameter("db", dbIds);
    }

    public static Query queryAbstractsByDb(int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB).setParameter("db", dbId);
    }

    public static Query queryAbstractsByCochraneNotification(String notificationFileName, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_COCHRANE_NOTIFICATION)
                .setParameter("notificationFileName", notificationFileName).setMaxResults(1);
    }

    public static Query queryUnpublishedAbstracts(int recordNumber, int pub, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_NOT_NOTIFIED_BY_NUMBER_AND_PUB).setParameter(
                "nu", recordNumber).setParameter("pu", pub);
    }

    public static Query queryDuplicatesAbstract(int recordNumber, int pub, String version, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_DUPLICATES_BY_NUMBER_AND_PUB_AND_VERSION).setParameter(
                "nu", recordNumber).setParameter("pu", pub).setParameter("ver", version);
    }

    public static Query queryDuplicatesAbstract(int recordNumber, int pub, String version,
                                                String language, String sid, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_DUPLICATES_BY_NUMBER_AND_PUB_AND_VERSION_AND_LANG_AND_SID)
                .setParameter("nu", recordNumber).setParameter("pu", pub).setParameter("ver", version)
                .setParameter("lang", language).setParameter("sid", sid);
    }

    public static Query queryAbstractsByDf(int dfId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DF).setParameter("df", dfId);
    }

    public static Query queryAbstractsByDf(int dfId, String cdNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DF_AND_NAME).setParameter("df", dfId).setParameter(
                "na", cdNumber);
    }

    public static Query queryAbstractsByDf(int dfId, Collection<String> cdNumbers, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DF_AND_NAMES).setParameter("df", dfId).setParameter(
                "na", cdNumbers);
    }

    public static Query queryAbstractsByDb(int dbId, String cdNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_NAME).setParameter("db", dbId).setParameter(
                "na", cdNumber);
    }

    public static Query queryAbstractByDbAndName(int dbId, String cdNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_NAME).setParameter("db", dbId).setParameter(
                "na", cdNumber).setMaxResults(1);
    }

    public static Query queryAbstractsByDb(int dbId, Collection<String> cdNumbers, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_NAMES).setParameter("db", dbId).setParameter(
                "na", cdNumbers);
    }
   
    public static Query querySetAbstractsCanceledNotified(String cdNumber, int dfId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_PUBLISH_CANCEL_NOTIFIED_BY_DF_AND_NAME).setParameter(
            "df", dfId).setParameter("na", cdNumber);
    }

    public static Query querySetTaAbstractsReceived(Collection<Integer> ids, int notified, int state,
                                                    EntityManager manager) {
        return manager.createNativeQuery(QUERY_UPDATE_ON_RECEIVED).setParameter("id", ids).setParameter(
                "no", notified).setParameter("st", state);
    }

    public static Query queryDeletePublishedAbstractsByDb(int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_DB).setParameter("db", dbId);
    }

    public static Query queryDeletePublishedAbstractsByDb(String cdNumber, int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_DB_AND_NAME).setParameter("db", dbId).setParameter(
                "na", cdNumber);
    }

    @Column(name = "db_id")
    public Integer getDbId() {
        return dbId;
    }

    public void setDbId(Integer dbId) {
        this.dbId = dbId;
    }

    @Column(length = STRING_VARCHAR_LENGTH_16, nullable = false)
    public String getRecordName() {
        return recordName;
    }

    public void setRecordName(String recordName) {
        this.recordName = recordName;
    }

    @Column(nullable = false)
    public int getNotified() {
        return notified;
    }

    public void setNotified(int notified) {
        this.notified = notified;
    }

    @Column(name = "record_id", updatable = false)
    public Integer getRecordId() {
        return recordId;
    }

    public void setRecordId(Integer recordId) {
        this.recordId = recordId;
    }

    @Column(name = "published")
    public Date getPublishedDate() {
        return publishDate;
    }

    public void setPublishedDate(Date date) {
        publishDate = date;
    }

    @Column(name = "initial_df_id")
    public Integer getInitialDeliveryId() {
        return initialDfId;
    }
    
    public void setInitialDeliveryId(Integer dfId) {
        this.initialDfId = dfId;
    }

    @Column(name = "ack_publish_id")
    public Integer getAcknowledgementId() {
        return ackDfId;
    }
    @Column(name = "cochrane_notification")
    public String getCochraneNotificationFileName() {
        return cochraneNotificationFileName;
    }

    public void setCochraneNotificationFileName(String cochraneNotificationFileName) {
        this.cochraneNotificationFileName = cochraneNotificationFileName;
    }

    public void setAcknowledgementId(Integer dfId) {
        this.ackDfId = dfId;
    }

    @Column(name = "manuscript")
    public String getManuscriptNumber() {
        return manuscriptNumber;
    }

    public void setManuscriptNumber(String manuscriptNumber) {
        this.manuscriptNumber = manuscriptNumber;
    }

    @Override
    @Transient
    public FlowProduct.SPDState sPD() {
        return spd;
    }

    @Override
    @Transient
    public void sPD(boolean value) {
        spd = value ? FlowProduct.SPDState.ON : FlowProduct.SPDState.OFF;
    }

    @Column(name = "spd")
    public int getSPD() {
        return spd.ordinal();
    }

    public void setSPD(int value) {
        spd = FlowProduct.SPDState.get(value);
    }

    @Override
    @Column(name = "high")
    public int getHighPriority() {
        return highPriority;
    }

    @Override
    public void setHighPriority(int value) {
        highPriority = value;
    }

    @Override
    @Transient
    public boolean isHighProfile() {
        return AriesHelper.isHighProfile(getHighPriority());
    }

    @Transient
    public void setHighProfile(boolean highProfile) {
        setHighPriority(AriesHelper.addHighProfile(highProfile, getHighPriority()));
    }

    @Override
    @Transient
    public boolean isHighFrequency() {
        return AriesHelper.isHighFrequency(getHighPriority());
    }

    @Transient
    public void setHighFrequency(boolean highFrequency) {
        setHighPriority(AriesHelper.addHighFrequency(highFrequency, getHighPriority()));
    }

    @Override
    @Transient
    public String getFrequency() {
        return AriesHelper.isHighFrequency(getHighPriority()) ? HWFreq.HIGH.getValue() : HWFreq.REAL_TIME.getValue();
    }

    @Transient
    public boolean isReprocessed() {
        return !getDeliveryId().equals(getInitialDeliveryId());
    }

    @Transient
    public int getPubNotified() {
        return getPubNotified(notified);
    }

    @Transient
    public boolean hasLanguage() {
        return getLanguage() != null;
    }

    @Transient
    public String toString() {
        return hasLanguage() ? getPubName() + "." + getLanguage() : getPubName();
    }

    @Transient
    public boolean same(PublishedAbstractEntity pae) {
        return getVersion().equals(pae.getVersion()) && sPD() == pae.sPD() && toString().equals(pae.toString());
    }

    @Transient
    public String getPubName() {
        return RecordHelper.buildPubName(getRecordName(), getPubNumber());
    }

    @Transient
    @Override
    public Integer getEntityId() {
        return DbEntity.NOT_EXIST_ID;
    }

    @Transient
    @Override
    public String getSID() {
        return hasLanguage() ? getSid() : getManuscriptNumber();
    }

    @Transient
    @Override
    public void setSID(String value) {
        if (hasLanguage()) {
            setSid(value);
        } else {
            setManuscriptNumber(value);
        }
    }

    @Transient
    @Override
    public String getCochraneVersion() {
        return getVersion();
    }

    @Transient
    @Override
    public void setCochraneVersion(String version) {
        setVersion(version);
    }

    @Transient
    @Override
    public FlowProduct.State getState() {
        int realNotified = clearCanceled(getNotified());
        return isNotifiedOnPublished(realNotified) ? FlowProduct.State.PUBLISHED
                : (isNotifiedOnReceived(realNotified) ? FlowProduct.State.RECEIVED : FlowProduct.State.UNDEFINED);
    }

    @Transient
    @Override
    public Map<String, String> getRawData() {
        Map<String, String> ret  = new HashMap<>();
        ret.put(ISFProductPart.P_TR_LANGUAGE, getLanguage());
        SFType.FLOW.setType(ret);
        SFType.setPackageId(getSourcePackageId(), ret);
        return ret;
    }

    @Transient
    @Override
    public Integer getSourcePackageId() {
        return sPD().off() ? getDeliveryId() : getInitialDeliveryId();
    }

    @Transient
    @Override
    public String getPubCode() {
        return hasLanguage() ? FilePathBuilder.buildTAName(getLanguage(), getPubName()) : getPubName();
    }

    @Transient
    @Override
    public String getParentDOI() {
        return hasLanguage() ? Constants.NA : getBaseType().getProductType().getParentDOI();
    }

    @Transient
    @Override
    public String getType() {
        return getBaseType().getShortName();
    }

    @Transient
    @Override
    public int getDbType() {
        return getBaseType().getDbId();
    }

    @Transient
    public BaseType getBaseType() {
        return BaseType.find(RecordHelper.getDbNameByRecordNumber(getNumber())).get();
    }

    @Transient
    @Override
    public String getPublicationType() {
        return null;
    }

    @Transient
    @Override
    public String getSourceStatus() {
        return hasLanguage() ? Constants.TR : null;
    }

    @Transient
    @Override
    public String getDOI() {
        return getBaseType().getProductType().buildDoi(getRecordName(), getPubNumber());
    }

    @Transient
    public String getVendor() {
        return getManuscriptNumber() != null ? PackageChecker.ARIES
                : (getBaseType().isCDSR() ? PackageChecker.ARCHIE : PackageChecker.APTARA);
    }

    public static int getPubNotified(int notified) {
        return notified % PUB_NOTIFIED;
    }

    public static boolean isNotifiedOnPublished(int notified) {
        return notified >= PUB_NOTIFIED && notified < NOT_NOTIFIED;
    }

    public static int clearCanceled(int notified) {
        return notified % CANCEL_NOTIFIED;
    }

    public static boolean isNotifiedOnReceived(int notified) {
        return notified < NOT_NOTIFIED || notified == REC_NOTIFIED_ON_FAIL;
    }

    public static boolean isNotifiedOnReceivedSuccess(int notified) {
        return notified < NOT_NOTIFIED;
    }

    public static boolean isCanceled(int notified) {
        return notified >= CANCEL_NOTIFIED;
    }
}

