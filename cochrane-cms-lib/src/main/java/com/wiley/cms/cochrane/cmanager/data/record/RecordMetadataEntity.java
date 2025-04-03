package com.wiley.cms.cochrane.cmanager.data.record;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.Set;

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
import javax.xml.bind.annotation.XmlRootElement;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.entity.TitleEntity;
import com.wiley.cms.cochrane.cmanager.entity.VersionEntity;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.res.ContentAccessType;
import com.wiley.cms.cochrane.utils.Constants;
import com.wiley.cms.converter.services.RevmanMetadataHelper;
import com.wiley.cms.process.entity.DbEntity;
import com.wiley.tes.util.Logger;
import com.wiley.tes.util.res.Res;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * @version 06.06.2012
 */
@XmlRootElement
@Entity
@Table(name = "COCHRANE_RECORD_METADATA")
@NamedQueries({
        @NamedQuery(
                name = RecordMetadataEntity.QUERY_SELECT_ALL,
                query = "SELECT r FROM RecordMetadataEntity r WHERE r.version.number >=:n1 AND r.version.number <=:n2"
                    + " AND r.version.historyNumber > " + RecordEntity.VERSION_SHADOW
                    + " ORDER BY r.issue DESC, r.version.pubNumber DESC, r.id DESC"
        ),
        @NamedQuery(
                name = RecordMetadataEntity.QUERY_SELECT_BY_CD_NUMBER,
                query = "SELECT r FROM RecordMetadataEntity r WHERE r.cdNumber =:nu"
                    + " ORDER BY r.issue DESC, r.version.pubNumber DESC, r.id DESC"
        ),
        @NamedQuery(
                name = RecordMetadataEntity.QUERY_SELECT_LAST_BY_CD_NUMBER,
                query = "SELECT r FROM RecordMetadataEntity r WHERE r.cdNumber =:nu AND r.version.historyNumber="
                    + RecordEntity.VERSION_LAST + " ORDER BY r.issue DESC, r.version.pubNumber DESC, r.id DESC"
        ),
        @NamedQuery(
                name = RecordMetadataEntity.QUERY_SELECT_BY_CD_NUMBER_AND_HISTORY_NUMBER,
                query = "SELECT r FROM RecordMetadataEntity r WHERE r.cdNumber = :nu"
                    + " AND r.version.historyNumber =:v ORDER BY r.version.pubNumber DESC, r.id DESC"
        ),
        @NamedQuery(
            name = RecordMetadataEntity.QUERY_SELECT_BY_CD_NUMBERS,
            query = "SELECT r FROM RecordMetadataEntity r WHERE r.cdNumber IN(:nu)"
                    + " AND r.version.historyNumber > " + RecordEntity.VERSION_SHADOW
                    + " ORDER BY r.cdNumber, r.issue DESC, r.version.pubNumber DESC, r.id DESC"
        ),
        @NamedQuery(
            name = RecordMetadataEntity.QUERY_SELECT_BY_ISSUE_AND_CD_NUMBER,
            query = "SELECT r FROM RecordMetadataEntity r WHERE r.issue =:i AND r.cdNumber =:nu"
                    + " ORDER BY r.version.pubNumber DESC, r.id DESC"
        ),
        @NamedQuery(
            name = RecordMetadataEntity.QUERY_SELECT_BY_CD_NUMBER_AND_PUB,
            query = "SELECT r FROM RecordMetadataEntity r WHERE r.cdNumber = :nu AND r.version.pubNumber = :pu"
                    + " ORDER BY r.id DESC"
        ),
        @NamedQuery(
            name = RecordMetadataEntity.QUERY_SELECT_HISTORY_BY_PUB,
            query = "SELECT r FROM RecordMetadataEntity r WHERE r.cdNumber = :nu AND r.version.pubNumber <= :pu"
                    + " AND r.version.historyNumber > " + RecordEntity.VERSION_SHADOW
                    + " ORDER BY r.version.pubNumber DESC, r.id DESC"
        ),
        @NamedQuery(
            name = RecordMetadataEntity.QUERY_SELECT_HISTORY_BY_ISSUE,
            query = "SELECT r FROM RecordMetadataEntity r WHERE r.issue <= :i AND r.cdNumber = :nu"
                    + " ORDER BY r.issue DESC, r.version.pubNumber DESC, r.id DESC"
        ),
        @NamedQuery(
            name = RecordMetadataEntity.QUERY_SELECT_HISTORY_BY_ISSUE_AND_PUB,
            query = "SELECT r FROM RecordMetadataEntity r WHERE r.issue <= :i AND r.cdNumber = :nu"
                    + " AND r.version.pubNumber <= :pu ORDER BY r.issue DESC, r.version.pubNumber DESC, r.id DESC"
        ),
        @NamedQuery(
            name = RecordMetadataEntity.QUERY_SELECT_BY_ISSUE,
            query = "SELECT r FROM RecordMetadataEntity r WHERE r.issue =:i AND r.version.number >=:n1"
                    + " AND r.version.number <=:n2 ORDER BY r.cdNumber, r.version.pubNumber DESC, r.id DESC"
        ),
        @NamedQuery(
            name = RecordMetadataEntity.QUERY_SELECT_BY_ISSUE_AND_CD_NUMBERS,
            query = "SELECT r FROM RecordMetadataEntity r WHERE r.issue =:i AND r.cdNumber IN (:nu)"
                    + " ORDER BY r.version.pubNumber DESC, r.id DESC"
        ),
        @NamedQuery(
            name = RecordMetadataEntity.QUERY_SELECT_CD_NUMBER_BY_ISSUE_AND_OPEN_ACCESS,
            query = "SELECT r.cdNumber FROM RecordMetadataEntity r WHERE r.issue = :i AND r.accessTypeId > "
                    + ContentAccessType.DEFAULT + " AND r.version.number >=:n1 AND r.version.number <=:n2"
        ),
        @NamedQuery(
            name = RecordMetadataEntity.QUERY_SELECT_FOR_EDI_CCA_BY_CD_NUMBER_FIRST_ONLINE,
            query = "SELECT r FROM RecordMetadataEntity r WHERE r.cdNumber = :nu AND r.firstOnline IS NOT NULL"
                    + " AND r.version.historyNumber=" + RecordEntity.VERSION_LAST + " ORDER BY r.id DESC"
        )
    })
public class RecordMetadataEntity implements Serializable, ICDSRMeta {

    public static final int VERSION_LENGTH = 8;
    public static final int IMPORTED = 3;

    public static final String PARAM_ISSUE = "issue";

    static final String QUERY_SELECT_ALL = "recordsMetadataAll";
    static final String QUERY_SELECT_BY_CD_NUMBER = "recordsMetadataByCdNumber";
    static final String QUERY_SELECT_BY_CD_NUMBER_AND_HISTORY_NUMBER = "recordsMetadataByCdNumberAndHistoryNumber";
    static final String QUERY_SELECT_LAST_BY_CD_NUMBER = "recordsMetadataLastByCdNumber";
    static final String QUERY_SELECT_BY_CD_NUMBERS = "recordsMetadataByCdNumbers";
    static final String QUERY_SELECT_BY_ISSUE_AND_CD_NUMBER = "recordsMetadataByIssueAndCdNumber";
    static final String QUERY_SELECT_BY_ISSUE_AND_CD_NUMBERS = "recordsMetadataByIssueAndCdNumbers";
    static final String QUERY_SELECT_BY_CD_NUMBER_AND_PUB = "recordsMetadataByCdNumberAndPub";
    static final String QUERY_SELECT_HISTORY_BY_PUB = "recordsMetadataHistoryByPub";
    static final String QUERY_SELECT_HISTORY_BY_ISSUE = "recordsMetadataHistoryByIssue";
    static final String QUERY_SELECT_HISTORY_BY_ISSUE_AND_PUB = "recordsMetadataHistoryByIssueAndPub";
    static final String QUERY_SELECT_BY_ISSUE = "recordsMetadataByIssue";
    static final String QUERY_SELECT_CD_NUMBER_BY_ISSUE_AND_OPEN_ACCESS = "namesByIssueAndOpenAccess";
    static final String QUERY_SELECT_FOR_EDI_CCA_BY_CD_NUMBER_FIRST_ONLINE = "recordsMetadataByCdNumberFirstOnline";

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(RecordMetadataEntity.class);

    private static final String REVMAN_TRUE = "YES";
    private static final String REVMAN_FALSE = "NO";

    private int id;
    private String cdNumber;
    private int pubNumber;
    private int issue;
    private Date received;
    private String publishedOnline;
    private String publishedFirstOnline;
    private String publishedOnlineCitation;

    private boolean newCitation;
    private int citationIssue;
    private Date citationLastChanged;
    private int versionFirstPublishedIssue;
    private Date versionFirstPublished;
    private int selfCitationIssue;
    private Integer status;
    private GroupEntity group;
    private GroupEntity prevGroup;
    private String stage;
    private String type;
    private String subtype;
    private ContentAccessType accessType;
    private boolean commented;
    private boolean nih;
    private boolean apc;
    //private boolean highProfile = false;
    //private String title;
    private TitleEntity titleEntity;

    private int metaType;

    private int citationIssueCT;
    private int selfCitationIssueCT;
    private String citationLastChangedCT;
    private int versionIssueCT;
    private Date versionPublishedCT;
    private int protocolIssueFirst;
    private int reviewIssueFirst;
    private String spdCT;

    private VersionEntity version;

    private Set<String> languages;
    private Boolean spdChanged;

    public RecordMetadataEntity() {
    }

    public RecordMetadataEntity(CDSRMetaVO meta, VersionEntity ve, TitleEntity ute,
                                GroupEntity ge, RecordMetadataEntity prev) {
        setVersion(ve);

        setCdNumber(meta.getCdNumber());
        setPubNumber(meta.getPubNumber());
        setStatus(meta.getStatus());
        setType(meta.getType());
        setSubType(meta.getSubType());
        setStage(meta.getStage());
        setAccessTypeId(meta.getAccessTypeId());
        setIssue(meta.getIssue());
        setReceived(meta.getDate());
        setPublishedIssue(meta.getPublishedIssue());
        setPublishedDate(meta.getPublishedDate());
        setCitationIssue(meta.getCitationIssue());
        setCitationLastChanged(meta.getCitationLastChanged());
        setProtocolFirstIssue(meta.getProtocolFirstIssue());
        setReviewFirstIssue(meta.getReviewFirstIssue());
        setSelfCitationIssue(meta.getSelfCitationIssue());
        setNewCitation(meta.isNewCitation());
        setCommented(meta.isCommented());
        setNihFunded(meta.isNihFunded());
        setApcWaiver(meta.isApcWaiver());
        //setLegacyTitle(meta.getTitle());
        setTitleEntity(ute);
        setGroup(ge);
        if (prev != null) {
            setPreviousGroup(prev.getGroup());
        }
        setHistoryNumber(meta.getHistoryNumber());
        ve.setFutureHistoryNumber(meta.getHistoryNumber());
        ve.setCochraneVersion(meta.getCochraneVersion());
        if (meta.isJats()) {
            setMetaType(1);
        }
        //setHighProfile(meta.isHighProfile());

        // save initial source content dates for test or audit purposes (can be removed later)
        setCitationIssueCT(meta.getCitationIssue());
        setCitationLastChangedCT(meta.getPublishedOnlineCitation());
        setSelfCitationIssueCT(meta.getSelfCitationIssue());
        setVersionIssueCT(meta.getPublishedIssue());
        setVersionDateCT(meta.getPublishedDate());
    }

    public static Query queryRecordMetadata(int startNumber, int endNumber, int skip, int batchSize, EntityManager em) {
        return DbEntity.appendBatchResults(em.createNamedQuery(QUERY_SELECT_ALL).setParameter(
                "n1", startNumber).setParameter("n2", endNumber), skip, batchSize);
    }

    public static Query queryRecordMetadata(String cdNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_CD_NUMBER).setParameter("nu", cdNumber);
    }

    public static Query queryRecordMetadataHistory(String cdNumber, Integer historyNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_CD_NUMBER_AND_HISTORY_NUMBER).setParameter(
                "nu", cdNumber).setParameter("v", historyNumber);
    }

    public static Query queryRecordMetadataLast(String cdNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_LAST_BY_CD_NUMBER).setParameter("nu", cdNumber);
    }

    public static Query queryRecordMetadata(Collection<String> cdNumbers, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_CD_NUMBERS).setParameter("nu", cdNumbers);
    }

    public static Query queryRecordMetadata(String cdNumber, int start, int maxSize, EntityManager manager) {
        return DbEntity.appendBatchResults(manager.createNamedQuery(
                QUERY_SELECT_BY_CD_NUMBER).setParameter("nu", cdNumber), start, maxSize);
    }

    public static Query queryRecordMetadataByIssue(int issue, String cdNumber, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_ISSUE_AND_CD_NUMBER).setParameter(
                "nu", cdNumber).setParameter("i", issue);
    }

    public static Query queryRecordMetadata(String cdNumber, int pub, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_CD_NUMBER_AND_PUB).setParameter(
                "nu", cdNumber).setParameter("pu", pub);
    }

    public static Query queryRecordMetadataHistory(String cdNumber, int pub, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_HISTORY_BY_PUB).setParameter(
                "nu", cdNumber).setParameter("pu", pub);
    }

    public static Query queryRecordMetadataHistory(int issueNumber, String cdNumber, int pub, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_HISTORY_BY_ISSUE_AND_PUB).setParameter(
                "i", issueNumber).setParameter("nu", cdNumber).setParameter("pu", pub).setMaxResults(1);
    }

    public static Query queryRecordMetadataByIssue(int issueNumber, String cdNumber, int count, EntityManager manager) {
        return DbEntity.appendBatchResults(manager.createNamedQuery(
            QUERY_SELECT_HISTORY_BY_ISSUE).setParameter("i", issueNumber).setParameter("nu", cdNumber), 0, count);
    }

    public static Query queryRecordMetadataByIssue(int startNumber, int endNumber, int issueNumber, EntityManager em) {
        return em.createNamedQuery(QUERY_SELECT_BY_ISSUE).setParameter(
                "i", issueNumber).setParameter("n1", startNumber).setParameter("n2", endNumber);
    }

    public static Query queryRecordMetadataByIssue(int issueNumber, Collection<String> names, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_ISSUE_AND_CD_NUMBERS).setParameter(
                "i", issueNumber).setParameter("nu", names);
    }

    public static Query queryOpenAccessCdNumbers(int startNumber, int endNumber, int issueNumber, EntityManager em) {
        return em.createNamedQuery(QUERY_SELECT_CD_NUMBER_BY_ISSUE_AND_OPEN_ACCESS).setParameter(
                "i", issueNumber).setParameter("n1", startNumber).setParameter("n2", endNumber);
    }

    public static Query queryRecordMetadataForEDIAndCCAFirstOnline(String cdNumber, EntityManager em) {
        return em.createNamedQuery(QUERY_SELECT_FOR_EDI_CCA_BY_CD_NUMBER_FIRST_ONLINE).setParameter("nu", cdNumber);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @Column(length = DbEntity.STRING_VARCHAR_LENGTH_32, nullable = false, updatable = false)
    public String getCdNumber() {
        return cdNumber;
    }

    public void setCdNumber(String cdNumber) {
        this.cdNumber = cdNumber;
    }

    @Column(nullable = false)
    public int getPubNumber() {
        return pubNumber;
    }

    public void setPubNumber(int pubNumber) {
        this.pubNumber = pubNumber;
    }

    @Column(name = "issue", nullable = false)
    public int getIssue() {
        return issue;
    }

    public void setIssue(int issue) {
        this.issue = issue;
    }

    @Column(name = "received", nullable = false, updatable = false)
    public Date getReceived() {
        return received;
    }

    public void setReceived(Date received) {
        this.received = received;
    }

    @Column(name = "published", length = DbEntity.STRING_VARCHAR_LENGTH_32)
    public String getPublishedOnlineFinalForm() {
        return publishedOnline;
    }

    public void setPublishedOnlineFinalForm(String publishedOnline) {
        this.publishedOnline = publishedOnline;
    }

    @Column(name = "published_online_first", length = DbEntity.STRING_VARCHAR_LENGTH_32)
    public String getFirstOnline() {
        return publishedFirstOnline;
    }

    public void setFirstOnline(String publishedFirstOnline) {
        this.publishedFirstOnline = publishedFirstOnline;
    }

    @Column(name = "published_online_citation", length = DbEntity.STRING_VARCHAR_LENGTH_32)
    public String getPublishedOnlineCitation() {
        return publishedOnlineCitation;
    }

    public void setPublishedOnlineCitation(String publishedOnlineCitation) {
        this.publishedOnlineCitation = publishedOnlineCitation;
    }

    @Column(length = 2, nullable = false, updatable = false)
    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    @Column(name = "cit", nullable = false)
    public boolean isNewCitation() {
        return newCitation;
    }

    public void setNewCitation(boolean newCitation) {
        this.newCitation = newCitation;
    }

    @Transient
    public String getNewCitationAsString() {
        return getRevmanBoolean(isNewCitation());
    }

    @Transient
    public String getNihFundedAsString() {
        return getRevmanBoolean(isNihFunded());
    }

    @Transient
    public String getApcWaiverAsString() {
        return getRevmanBoolean(isApcWaiver());
    }

    @Column(name = "version_published")
    public Date getPublishedDate() {
        return versionFirstPublished;
    }

    public void setPublishedDate(Date versionPublished) {
        this.versionFirstPublished = versionPublished;
    }

    @Column(name = "version_issue", nullable = false)
    public int getPublishedIssue() {
        return versionFirstPublishedIssue;
    }

    public void setPublishedIssue(int versionIssue) {
        this.versionFirstPublishedIssue = versionIssue;
    }

    @Column(name = "citation_changed")
    public Date getCitationLastChanged() {
        return citationLastChanged;
    }

    public void setCitationLastChanged(Date citationLastChanged) {
        this.citationLastChanged = citationLastChanged;
    }

    @Column(name = "citation_issue", nullable = false)
    public int getCitationIssue() {
        return citationIssue;
    }

    public void setCitationIssue(int issue) {
        citationIssue = issue;
    }

    @Column(name = "self_citation_issue", nullable = false)
    public int getSelfCitationIssue() {
        return selfCitationIssue;
    }

    public void setSelfCitationIssue(int selfCitationIssue) {
        this.selfCitationIssue = selfCitationIssue;
    }

    @Column(name = "ct_self_citation_issue")
    public int getSelfCitationIssueCT() {
        return selfCitationIssueCT;
    }

    public void setSelfCitationIssueCT(int selfCitationIssue) {
        this.selfCitationIssueCT = selfCitationIssue;
    }

    @Column(nullable = false, updatable = false)
    public int getStatus() {
        return status;
    }

    @Transient
    public String getStatusAsString() {
        return RevmanStatus.getStatusName(status);
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false)
    public GroupEntity getGroup() {
        return group;
    }

    public void setGroup(GroupEntity group) {
        this.group = group;
    }

    @ManyToOne
    @JoinColumn(name = "prev_group_id")
    public GroupEntity getPreviousGroup() {
        return prevGroup;
    }

    public void setPreviousGroup(GroupEntity group) {
        this.prevGroup = group;
    }

    @Column(length = 1, nullable = false, updatable = false)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Column(length = 2)
    public String getSubType() {
        return subtype;
    }

    public void setSubType(String stype) {
        subtype = stype;
    }

    @Column(name = "ct_version_published", updatable = false)
    public Date getVersionDateCT() {
        return versionPublishedCT;
    }

    public void setVersionDateCT(Date versionPublished) {
        this.versionPublishedCT = versionPublished;
    }

    @Column(name = "ct_version_issue", nullable = false, updatable = false)
    public int getVersionIssueCT() {
        return versionIssueCT;
    }

    public void setVersionIssueCT(int versionIssue) {
        this.versionIssueCT = versionIssue;
    }

    @Column(name = "ct_citation_changed", updatable = false)
    public String getCitationLastChangedCT() {
        return citationLastChangedCT;
    }

    public void setCitationLastChangedCT(String citationLastChanged) {
        this.citationLastChangedCT = citationLastChanged;
    }

    @Column(name = "ct_citation_issue", nullable = false, updatable = false)
    public int getCitationIssueCT() {
        return citationIssueCT;
    }

    public void setCitationIssueCT(int issue) {
        citationIssueCT = issue;
    }

    @Column(name = "ct_protocol_first", nullable = false)
    public int getProtocolFirstIssue() {
        return protocolIssueFirst;
    }

    public void setProtocolFirstIssue(int issue) {
        protocolIssueFirst = issue;
    }

    @Column(name = "ct_review_first", nullable = false)
    public int getReviewFirstIssue() {
        return reviewIssueFirst;
    }

    public void setReviewFirstIssue(int issue) {
        reviewIssueFirst = issue;
    }

    @Column(name = "ct_spd", updatable = false)
    public String getScheduledDateCT() {
        return spdCT;
    }

    public void setScheduledDateCT(String spd) {
        spdCT = spd;
    }

    @Transient
    public boolean notEqualIssuesVsCT() {
        return (getSelfCitationIssueCT() != 0 && getSelfCitationIssueCT() != getSelfCitationIssue())
            || (getVersionIssueCT() != 0 && getVersionIssueCT() != getPublishedIssue())
                || getIssue() != getPublishedIssue();
    }

    @Transient
    public String getRevmanId() {
        return "";
    }

    @Transient
    public void setRevmanId(String revmanId) {
    }

    @Transient
    public String getCochraneVersion() {
        return getVersion().getCochraneVersion();
    }

    @ManyToOne
    @JoinColumn(name = "version_id", nullable = false, updatable = false)
    public VersionEntity getVersion() {
        return version;
    }

    public void setVersion(VersionEntity versionEntity) {
        version = versionEntity;
    }

    @Column(name = "comm", nullable = false)
    public boolean isCommented() {
        return commented;
    }

    public void setCommented(boolean commented) {
        this.commented = commented;
    }

    @Column(name = "nih_funded", nullable = false)
    public boolean isNihFunded() {
        return nih;
    }

    public void setNihFunded(boolean nih) {
        this.nih = nih;
    }

    @Column(name = "apc_waiver", nullable = false)
    public boolean isApcWaiver() {
        return apc;
    }

    public void setApcWaiver(boolean apc) {
        this.apc = apc;
    }

    //@Column(name = "title", nullable = false, updatable = false)
    //public String getLegacyTitle() {
    //    return title;
    //}

    //public void setLegacyTitle(String title) {
    //    this.title = title;
    //}

    @Transient
    public String getTitle() {
        return titleEntity == null ? null : getTitleEntity().getTitle();
    }

    //@ManyToOne(fetch = FetchType.LAZY)
    @ManyToOne
    @JoinColumn(name = "title_id")
    public TitleEntity getTitleEntity() {
        return titleEntity;
    }

    public void setTitleEntity(TitleEntity title) {
        titleEntity = title;
    }

    @Column(name = "accesstype", nullable = false, updatable = false)
    public int getAccessTypeId() {
        return accessType != null ? accessType.getId() : ContentAccessType.DEFAULT;
    }

    public void setAccessTypeId(int id) {
        Res<ContentAccessType> res = ContentAccessType.find(id);
        if (Res.valid(res)) {
            accessType = res.get();
        }
    }

    @Column(name = "metatype", nullable = false)
    public int getMetaType() {
        return metaType;
    }

    public void setMetaType(int type) {
        metaType = type;
    }

    @Override
    @Transient
    //@Column(name = "high", nullable = false, updatable = false)
    public boolean isHighProfile() {
        return false;
    }

    //public void setHighProfile(boolean value) {
    //    highProfile = value;
    //}

    @Transient
    public Integer getHistoryNumber() {
        return version.getHistoryNumber();
    }

    @Transient
    public void setHistoryNumber(Integer historyNumber) {
        version.setHistoryNumber(historyNumber);
    }

    @Transient
    public String getAccessType() {
        return accessType.getType();
    }

    @Transient
    public ContentAccessType getAccessTypeRes() {
        return accessType;
    }

    @Transient
    public String getAccessTypeMetadata() {
        return accessType.getMetadata();
    }

    @Transient
    public boolean isGoldOpenAccess() {
        return accessType != null && accessType.isGoldOpenAccess();
    }

    @Transient
    public String getCommentedAsString() {
        return getRevmanBoolean(isCommented());
    }

    @Transient
    public String getGroupSid() {
        return getGroup().getSid();
    }

    @Transient
    public String getGroupTitle() {
        return getGroup().getTitle();
    }

    @Transient
    public boolean isJats() {
        return metaType > 0;
    }

    @Transient
    public boolean isJatsImported() {
        return metaType == 2;
    }

    @Transient
    public ICDSRMeta getHistory() {
        return null;
    }

    @Transient
    public void setHistory(ICDSRMeta history) {
    }

    @Transient
    public boolean isFirstPub() {
        return getPubNumber() == Constants.FIRST_PUB;
    }

    @Transient
    public boolean isStageP() {
        return isStageP(getStage());
    }

    @Transient
    public boolean isStageUP() {
        return isStageUP(getStage());
    }

    @Transient
    public boolean isStageR() {
        return isStageR(getStage());
    }

    @Transient
    public boolean isStageE() {
        return isStageE(getStage());
    }

    @Transient
    @Override
    public Integer getSubTitle() {
        return getSubTitle(getStage());
    }

    @Transient
    public boolean isDeleted() {
        return isDeleted(status);
    }

    @Transient
    public boolean isWithdrawn() {
        return isWithdrawn(getStatus());
    }

    @Transient
    public boolean isUnchanged() {
        return true;
    }

    @Transient
    public String getName() {
        return getCdNumber();
    }

    @Transient
    public String getPublisherId() {
        return RevmanMetadataHelper.buildPubName(getCdNumber(), getPubNumber());
    }

    @Transient
    public boolean wasUploaded() {
        return version.getHistoryNumber() > RecordEntity.VERSION_SHADOW;
    }

    public static boolean isDeleted(int status) {
        return RevmanStatus.DELETED.dbKey == status;
    }

    public static boolean isWithdrawn(int status) {
        return RevmanStatus.WITHDRAWN.dbKey == status;
    }

    public static boolean isUnchanged(int status) {
        return RevmanStatus.UNCHANGED.dbKey == status;
    }

    public static boolean isNew(int status) {
        return RevmanStatus.NEW.dbKey == status;
    }

    @Transient
    @Override
    public boolean isScheduled() {
        return spdChanged != null || CmsUtils.isScheduledIssueNumber(getIssue());
    }

    @Transient
    public boolean isSPDChanged() {
        return spdChanged != null && spdChanged;
    }

    @Transient
    public void setSPDChanged(boolean value) {
        spdChanged = value;
    }

    @Transient
    @Override
    public Set<String> getLanguages() {
        return languages;
    }

    @Transient
    @Override
    public void setLanguages(Set<String> languages) {
        this.languages = languages;
    }

    @Transient
    public String getPublishDatesStr() {
        return CDSRMetaVO.getPublishDatesStr(versionFirstPublishedIssue, versionFirstPublished, publishedOnline,
                citationIssue, publishedOnlineCitation, selfCitationIssue, publishedFirstOnline);
    }

    @Override
    public String toString() {
        //return String.format("%s.pub%d [%d] %s jats=%b %d (%d/%d) %s v%s", cdNumber, pubNumber, id, stage, isJats(),
        //        citationIssue, issue, versionFirstPublishedIssue, group.getSid(), getHistoryNumber());
        return getPublisherId();
    }

    public static boolean getRevmanBoolean(String value) {
        return value != null && value.matches(REVMAN_TRUE);
    }

    public static String getRevmanBoolean(boolean value) {
        return value ? REVMAN_TRUE : REVMAN_FALSE;
    }

    public static boolean isTitleValid(String title) {
        return title != null && !title.trim().isEmpty();
    }

    public static boolean isStageP(String value) {
        return RevmanMetadataHelper.STAGE_P.equals(value);
    }

    public static boolean isStageUP(String value) {
        return RevmanMetadataHelper.STAGE_UP.equals(value);
    }

    public static boolean isStageR(String value) {
        return RevmanMetadataHelper.STAGE_R.equals(value);
    }

    public static boolean isStageE(String value) {
        return "E".equals(value);
    }

    public static Integer getSubTitle(String stage) {
        return isStageR(stage) ? ProductSubtitleEntity.ProductSubtitle.REVIEWS : isStageP(stage)
            ? ProductSubtitleEntity.ProductSubtitle.PROTOCOLS : isStageUP(stage)
                ? ProductSubtitleEntity.ProductSubtitle.UPDATE_PROTOCOLS : isStageE(stage)
                    ? ProductSubtitleEntity.ProductSubtitle.EDITORIALS : ProductSubtitleEntity.ProductSubtitle.NONE;
    }

    /**
     *
     */
    public enum RevmanStatus {

        UNCHANGED(0),
        NEW(1),
        //2 New search for studies completed, conclusions not changed
        UPDATED(2),
        WITHDRAWN(3),
        //4 Edited
        AMENDED(4),
        //5 Edited, conclusions changed
        AMENDED_CON_CHANGED(5),
        //6  Major change
        AMENDED_MAJOR_CHANGE(6),
        //7  New search for studies completed, conclusions changed
        UPDATED_CON_CHANGED(7),
        STABLE(8),
        DELETED(9),

        // fake status to validate status transition - record not exist
        NONE(100);

        public final int dbKey;

        RevmanStatus(int id) {
            dbKey = id;
        }

        public static RevmanStatus getEntity(String status) {
            try {
                return valueOf(status);
            } catch (Exception e) {
                LOG.error(String.format("RevMan status '%s' not found: %s", status, e.getMessage()));
                return null;
            }
        }

        public static int getStatus(String status) {
            RevmanStatus revmanStatus = getEntity(status);
            if (revmanStatus == null) {
                return UNCHANGED.dbKey;
            }
            return revmanStatus.dbKey;
        }

        public static String getStatusName(int status) {
            try {
                return status != Constants.UNDEF ? checkStatus(status) : null;

            } catch (Throwable tr) {
                LOG.error(String.format("RevMan status [%d] not found: %s", status, tr.getMessage()));
                return "";
            }
        }

        private static String checkStatus(int status) {
            if (status == NONE.dbKey) {
                return "";
            }
            RevmanStatus revmanStatus = values()[status];
            return revmanStatus.name();
        }
    }
}
