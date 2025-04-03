package com.wiley.cms.cochrane.cmanager.data;

import java.util.Collection;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.wiley.cms.cochrane.cmanager.CochraneCMSProperties;
import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;
import com.wiley.cms.cochrane.cmanager.DeliveryPackage;
import com.wiley.cms.cochrane.cmanager.IDeliveryFileStatus;
import com.wiley.cms.cochrane.cmanager.res.BaseType;
import com.wiley.cms.cochrane.test.PackageChecker;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.BitValue;

/**
 * Cochrane update file entity bean.
 *
 * @author <a href='mailto:ademidov@wiley.ru'>Andrey Demidov</a>
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @author <a href='mailto:osoletskaya@wiley.com'>Olga Soletskaya</a>
 * @version 1.0
 */
@Entity
@Table(name = "COCHRANE_DELIVERY_FILE")
@NamedQueries({
    @NamedQuery(
        name = "findNotCompletedPackage",
        query = "SELECT df from DeliveryFileEntity df where df.status=" + IDeliveryFileStatus.STATUS_BEGIN
    ),
    @NamedQuery(
        name = "findNotCompletedByDb",
        query = "SELECT count(df.id) from DeliveryFileEntity df where df.status="
            + IDeliveryFileStatus.STATUS_BEGIN + " and df.db.id=:dbId"
    ),
    @NamedQuery(
        name = DeliveryFileEntity.QUERY_SELECT_BY_DB_AND_STATUS,
        query = "SELECT new com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO (df) from DeliveryFileEntity df"
                + " WHERE df.db.id=:db AND df.status.id IN (:st)"
    ),
    @NamedQuery(
        name = DeliveryFileEntity.QUERY_SELECT_BY_DB_AND_ACK_STATUS_UNFINISHED,
        query = "SELECT new com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO (df) from DeliveryFileEntity df"
                + " WHERE df.db.id IN(:db) AND df.type >= " + DeliveryFileEntity.TYPE_ARIES
                + " AND df.type < " + DeliveryFileEntity.TYPE_WML3G + " AND df.status <> "
                + IDeliveryFileStatus.STATUS_PICKUP_FAILED  + " AND (df.modifyStatus.id IS NULL"
                + " OR df.modifyStatus.id <> " + IDeliveryFileStatus.OP_ARIES_ACK_SENT + ") ORDER BY df.id DESC"
    ),
    @NamedQuery(
        name = "deleteDfByDb",
        query = "delete from DeliveryFileEntity where db=:db"
    ),
    @NamedQuery(
        name = "deleteDfById",
        query = "delete from DeliveryFileEntity where id=:id"
    ),
    @NamedQuery(
        name = DeliveryFileEntity.QUERY_SELECT_VO_BY_DB,
        query = "SELECT new com.wiley.cms.cochrane.cmanager.data.DeliveryFileVO(df) FROM DeliveryFileEntity df"
                + " WHERE df.db.id=:db"
    ),
    @NamedQuery(
        name = DeliveryFileEntity.QUERY_SELECT_BY_ISSUE,
        query = "SELECT df FROM DeliveryFileEntity df WHERE df.issue.id=:i ORDER BY df.id DESC"
    ),
    @NamedQuery(
         name = DeliveryFileEntity.QUERY_SELECT_BY_ISSUE_AND_SPD_ISSUE,
         query = "SELECT df FROM DeliveryFileEntity df WHERE df.issue.id=:i OR df.issue.id=:spd ORDER BY df.id DESC"
    ),
    @NamedQuery(
         name = DeliveryFileEntity.QUERY_SELECT_LAST_BY_TYPE_AND_NAME,
         query = "SELECT df FROM DeliveryFileEntity df WHERE df.type=:tp AND  df.name=:na ORDER BY df.id DESC"
    ),
    @NamedQuery(
        name = "findLastDfByIssueAndName",
        query = "SELECT df FROM DeliveryFileEntity df WHERE df.issue.id =:issue AND df.name LIKE :name "
                + "ORDER BY df.id DESC"
    )})

public class DeliveryFileEntity implements java.io.Serializable {
    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_REVMAN = 1;
    public static final int TYPE_TA = 2;
    public static final int TYPE_JATS = 4;
    public static final int TYPE_JATS_TA = 8;
    public static final int TYPE_ARIES = 16;
    public static final int TYPE_WML3G = 32;

    static final String QUERY_SELECT_VO_BY_DB = "selectDfVOByDb";
    static final String QUERY_SELECT_BY_ISSUE = "selectDfByIssue";
    static final String QUERY_SELECT_BY_ISSUE_AND_SPD_ISSUE = "selectDfByIssueAndSpd";
    static final String QUERY_SELECT_BY_DB_AND_STATUS = "selectDfByDbAndStatus";
    static final String QUERY_SELECT_BY_DB_AND_ACK_STATUS_UNFINISHED = "selectDfByDbAndAckStatusUnfinished";
    static final String QUERY_SELECT_LAST_BY_TYPE_AND_NAME = "selectDfLastByTypeAndName";

    private static final int TYPE_REVMAN_TA = 3;
    private static final int BIT_JATS = 2;
    private static final int BIT_JATS_TA = 3;
    private static final int BIT_ARIES = 4;
    private static final int BIT_WML3G = 5;

    private Integer id;
    private String name;
    private String vendor;
    private Date date;
    private IssueEntity issue;
    private StatusEntity status;
    private StatusEntity interimStatus;
    private StatusEntity modifyStatus;
    private boolean isPdfCompleted;
    private boolean isHtmlCompleted;
    private boolean isCdCompleted;
    private ClDbEntity db;
    private int type = TYPE_DEFAULT;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public static Query queryDeliveryFilesVO(int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_VO_BY_DB).setParameter("db", dbId);
    }

    public static Query queryDeliveryFiles(Integer issueId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_ISSUE).setParameter("i", issueId);
    }

    public static Query queryDeliveryFiles(Integer issueId, Integer spdIssueId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_ISSUE_AND_SPD_ISSUE).setParameter(
                "i", issueId).setParameter("spd", spdIssueId);
    }

    public static Query queryDeliveryFiles(int dbId, Collection<Integer> statuses, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_STATUS).setParameter("db", dbId).setParameter(
                "st", statuses);
    }

    public static Query queryAcknowledgementUnfinished(Collection<Integer> dbIds, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_ACK_STATUS_UNFINISHED).setParameter("db", dbIds);
    }

    public static Query queryDeliveryFile(int type, String name, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_LAST_BY_TYPE_AND_NAME).setParameter("tp", type).setParameter(
                "na", name).setMaxResults(1);
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    @Column(length = DbEntity.STRING_VARCHAR_LENGTH_64)
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getVendor() {
        return vendor;
    }

    @Transient
    public String getVendorByType() {
        BaseType bt = BaseType.find(getDb().getTitle()).get();
        return vendor != null && !vendor.isEmpty() ? vendor : (isAriesSFTP() ? PackageChecker.ARIES : (bt.isCDSR()
                ? PackageChecker.ARCHIE : (bt.isCentral() ? PackageChecker.METAXIS : PackageChecker.APTARA)));
    }

    public void setVendor(final String vendor) {
        this.vendor = vendor;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(final Date date) {
        this.date = date;
    }

    @ManyToOne
    @JoinColumn(name = "issue_id")
    public IssueEntity getIssue() {
        return issue;
    }

    public void setIssue(final IssueEntity issue) {
        this.issue = issue;
    }

    @ManyToOne
    public StatusEntity getStatus() {
        return status;
    }

    public void setStatus(StatusEntity status) {
        this.status = status;
    }

    @ManyToOne
    public StatusEntity getInterimStatus() {
        return interimStatus;
    }

    public void setInterimStatus(StatusEntity interimStatus) {
        this.interimStatus = interimStatus;
    }

    @ManyToOne
    public StatusEntity getModifyStatus() {
        return modifyStatus;
    }

    public void setModifyStatus(StatusEntity modifyStatus) {
        this.modifyStatus = modifyStatus;
    }

    public boolean isPdfCompleted() {
        return isPdfCompleted;
    }

    public void setPdfCompleted(boolean pdfCompleted) {
        isPdfCompleted = pdfCompleted;
    }

    public boolean isHtmlCompleted() {
        return isHtmlCompleted;
    }

    public void setHtmlCompleted(boolean htmlCompleted) {
        isHtmlCompleted = htmlCompleted;
    }

    public boolean isCdCompleted() {
        return isCdCompleted;
    }

    public void setCdCompleted(boolean cdCompleted) {
        isCdCompleted = cdCompleted;
    }

    @ManyToOne
    public ClDbEntity getDb() {
        return db;
    }

    public void setDb(ClDbEntity db) {
        this.db = db;
    }

    @Column(name = "type")
    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    @Transient
    public boolean isMeshterm() {
        return DeliveryPackage.isMeshterm(getName());
    }

    @Transient
    public boolean isPropertyUpdate() {
        return DeliveryPackage.isPropertyUpdate(getName());
    }

    @Transient
    public boolean isSystemUpdate() {
        return isPropertyUpdate() || isMeshterm();
    }

    @Transient
    public boolean isMethReview() {
        return getName().contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLMETHREV));
    }

    @Transient
    public boolean isCentral() {
        return getName().contains(CochraneCMSProperties.getProperty(CochraneCMSPropertyNames.CLCENTRAL));
    }

    @Transient
    public boolean isWml3g() {
        return isWml3g(getType());
    }

    @Transient
    public boolean isAriesSFTP() {
        return isAriesSFTP(getType());
    }

    @Transient
    public String toString() {
        return String.format("%s, status=%s/%s/%s [%d]", name, status, interimStatus, modifyStatus, id);
    }

    public static int setJatsTranslation(int type) {
        return BitValue.setBit(BIT_JATS_TA, type);
    }

    public static int setJats(int type) {
        return BitValue.setBit(BIT_JATS, type);
    }

    public static int setWml3g(int type) {
        return BitValue.setBit(BIT_WML3G, type);
    }

    public static boolean hasJatsTranslation(int type) {
        return BitValue.getBit(BIT_JATS_TA, type);
    }

    public static boolean isJats(int type) {
        return type >= TYPE_JATS && type < TYPE_WML3G && type != TYPE_ARIES;
    }

    public static boolean isAriesSFTP(int type) {
        return (type >= TYPE_ARIES && type < TYPE_WML3G) || type > TYPE_WML3G;
    }

    public static boolean hasAries(int type) {
        return BitValue.getBit(BIT_ARIES, type);
    }

    public static boolean isAriesAcknowledge(int type) {
        return type == TYPE_ARIES;
    }

    public static boolean onlyTranslation(int type) {
        return type == TYPE_TA || type == TYPE_JATS_TA;
    }

    public static boolean hasRevmanReview(int type) {
        return type == TYPE_REVMAN || type == TYPE_REVMAN_TA;
    }

    public static boolean isRevman(int type) {
        return type > TYPE_DEFAULT && type <= TYPE_REVMAN_TA;
    }

    public static boolean isRevmanOrJats(int type) {
        return type > TYPE_DEFAULT && type < TYPE_WML3G;
    }

    public static boolean isDefault(int type) {
        return type == TYPE_DEFAULT;
    }

    public static boolean isWml3g(int type) {
        return type >= TYPE_WML3G;
    }
}