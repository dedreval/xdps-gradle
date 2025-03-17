package com.wiley.cms.cochrane.cmanager.entitymanager;

import java.io.Serializable;
import java.util.Date;
import java.util.Set;

import javax.validation.constraints.NotNull;

import com.wiley.cms.cochrane.cmanager.CmsUtils;
import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.cmanager.res.ContentAccessType;
import com.wiley.cms.cochrane.services.PublishDate;
import com.wiley.cms.cochrane.utils.Constants;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 5/25/2019
 */
public class CDSRMetaVO extends ArchieEntry implements Serializable, ICDSRMeta {
    private static final long serialVersionUID = 1L;

    private String group;

    private PublishDate citationChangedDate = PublishDate.EMPTY;
    private PublishDate versionFirstDate = PublishDate.EMPTY;
    private String firstOnline;
    private int protocolFirstIssue;
    private int reviewFirstIssue;
    private int selfCitationIssue;
    private boolean citation = false;

    private int issue;
    private Date date;

    private String stage;

    private String type;
    private String subType;

    private String revmanId;
    private String groupTitle;
    private ContentAccessType accessType;

    private boolean commented;
    private boolean apcWaiver;
    private boolean nihFunded;
    private boolean highProfile;

    private String title;
    private ICDSRMeta history = null;

    private Set<String> languages = null;
    private Integer unitStatusId = null;
    private String publicationType = null;

    private boolean jats = false;
    private Integer historyNumber = RecordEntity.VERSION_LAST;

    private boolean successful = true;

    public CDSRMetaVO(String cdNumber, int pub, String rmVersion) {
        super(cdNumber, pub, rmVersion, RecordMetadataEntity.RevmanStatus.UNCHANGED.dbKey);
    }

    public CDSRMetaVO(String cdNumber, int pub, String group, String stage, int status, String rmVersion) {
        super(cdNumber, pub, rmVersion, status);

        this.group = group;
        this.stage = stage;
    }

    public CDSRMetaVO(RecordMetadataEntity rme) {
        this(rme.getCdNumber(), rme.getPubNumber(), rme.getGroup().getSid(), rme.getStage(), rme.getStatus(),
                rme.getCochraneVersion());

        setType(rme.getType(), rme.getSubType());

        setPubDates(rme.getPublishedIssue(), rme.getPublishedDate(), rme.getPublishedOnlineFinalForm(),
                    rme.getCitationIssue(), rme.getCitationLastChanged(), rme.getPublishedOnlineCitation(),
                    rme.getFirstOnline());
        setFirstIssues(rme.getProtocolFirstIssue(), rme.getReviewFirstIssue(), rme.getSelfCitationIssue());
        citation = rme.isNewCitation();

        setIssue(rme.getIssue(), rme.getReceived());
        setId(rme.getId());

        setGroupTitle(rme.getGroup().getTitle());
        accessType = rme.getAccessTypeRes();

        setFlags(rme.isCommented(), rme.isApcWaiver(), rme.isNihFunded());
        setTitle(rme.getTitle());
        
        historyNumber = rme.getHistoryNumber();
        setJats(rme.isJats());
        setHighProfile(rme.isHighProfile());
    }

    public boolean isFirstPub() {
        return getPubNumber() == Constants.FIRST_PUB;
    }

    public String getCdNumber() {
        return getName();
    }

    public String getGroupSid() {
        return group;
    }

    public void setGroupSid(String groupSid) {
        group = groupSid;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public boolean isStageR() {
        return RecordMetadataEntity.isStageR(getStage());
    }

    public boolean isStageUP() {
        return RecordMetadataEntity.isStageUP(getStage());
    }

    public boolean isStageP() {
        return RecordMetadataEntity.isStageP(getStage());
    }

    public String getType() {
        return type;
    }

    public void setType(String type, String subType) {
        this.type = type;
        this.subType = subType;
    }

    public String getSubType() {
        return subType;
    }

    public String getWMLPublicationType() {
        return publicationType;
    }

    public void setWMLPublicationType(String publicationType) {
        this.publicationType = publicationType;
    }

    @Override
    public Integer getSubTitle() {
        return RecordMetadataEntity.getSubTitle(getStage());
    }

    public Date getPublishedDate() {
        return versionFirstDate.date();
    }

    public int getPublishedIssue() {
        return versionFirstDate.issue();
    }

    public String getPublishedOnlineFinalForm() {
        return versionFirstDate.get();
    }

    public String getFirstOnline() {
        return firstOnline;
    }

    public Date getCitationLastChanged() {
        return citationChangedDate.date();
    }

    public String getPublishedOnlineCitation() {
        return citationChangedDate.get();
    }

    public int getCitationIssue() {
        return citationChangedDate.issue();
    }

    public int getProtocolFirstIssue() {
        return protocolFirstIssue;
    }

    public int getReviewFirstIssue() {
        return reviewFirstIssue;
    }

    public int getSelfCitationIssue() {
        return selfCitationIssue;
    }

    public boolean isNewCitation() {
        return citation;
    }

    public int getIssue() {
        return issue;
    }

    public void setPubDates(int versionIssue, Date versionDate, int citationIssue, Date citationDate,
                            int selfCitationIssue, boolean citationChanged) {
        setVersionPubDates(versionIssue, versionDate, null);
        setCitationPubDates(new PublishDate(null, citationDate, citationIssue));
        this.selfCitationIssue = selfCitationIssue;
        citation = citationChanged;
    }

    public void setPubDates(int versionIssue, Date versionDate, String versionDateSrc,
                            int citationIssue, Date citationDate, String citationChangedSrc, String firstOnline) {
        setVersionPubDates(versionIssue, versionDate, versionDateSrc);
        setCitationPubDates(new PublishDate(citationChangedSrc, citationDate, citationIssue));
        this.firstOnline = firstOnline;
    }

    public void setVersionPubDates(int versionIssue, Date versionDate, String versionDateSrc) {
        setVersionPubDates(new PublishDate(versionDateSrc, versionDate, versionIssue));
    }

    public void setVersionPubDates(@NotNull PublishDate publishDate) {
        versionFirstDate = publishDate;
    }

    public void setCitationPubDates(int citationIssue, Date citationDate, String citationChangedSrc,
                                    String firstOnline, int selfCitationIssue) {
        setCitationPubDates(new PublishDate(citationChangedSrc, citationDate, citationIssue));
        this.firstOnline = firstOnline;
        this.selfCitationIssue = selfCitationIssue;
    }

    public void setCitationPubDates(@NotNull PublishDate publishDate) {
        citationChangedDate = publishDate;
    }

    public void setFirstIssues(int protocolFirstIssue, int reviewFirstIssue, int selfCitationIssue) {
        this.protocolFirstIssue = protocolFirstIssue;
        this.reviewFirstIssue = reviewFirstIssue;
        this.selfCitationIssue = selfCitationIssue;
    }

    public void setIssue(int issue, Date date) {
        this.issue = issue;
        this.date = date;
    }

    public Date getDate() {
        return date;
    }

    public String getCochraneVersion() {
        return version;
    }

    public String getNewCitationAsString() {
        return RecordMetadataEntity.getRevmanBoolean(isNewCitation());
    }

    public boolean isCommented() {
        return commented;
    }

    public String getCommentedAsString() {
        return RecordMetadataEntity.getRevmanBoolean(isCommented());
    }

    public String getNihFundedAsString() {
        return RecordMetadataEntity.getRevmanBoolean(nihFunded);
    }

    public boolean isNihFunded() {
        return nihFunded;
    }

    public String getApcWaiverAsString() {
        return RecordMetadataEntity.getRevmanBoolean(apcWaiver);
    }

    public boolean isApcWaiver() {
        return apcWaiver;
    }

    public void setFlags(boolean commented, boolean nihFunded, boolean apcWaiver) {
        this.commented = commented;
        this.nihFunded = nihFunded;
        this.apcWaiver = apcWaiver;
    }

    public String getGroupTitle() {
        return groupTitle;
    }

    public void setGroupTitle(String groupTitle) {
        this.groupTitle = groupTitle;
    }

    public String getRevmanId() {
        return revmanId;
    }

    public void setRevmanId(String revmanId) {
        this.revmanId = revmanId;
    }

    public boolean isGoldOpenAccess() {
        return accessType != null && accessType.getId() != ContentAccessType.DEFAULT;
    }

    public String getAccessType() {
        return accessType.getType();
    }

    public int getAccessTypeId() {
        return accessType.getId();
    }

    public String getAccessTypeMetadata() {
        return accessType.getMetadata();
    }

    public void setAccessTypeMetadata(String metadata) {
        accessType = ContentAccessType.findByMetadata(metadata).get();
    }

    public void setAccessTypeMetadata(ContentAccessType accessType) {
        this.accessType = accessType;
    }

    @Override
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ICDSRMeta getHistory() {
        return history;
    }

    public void setHistory(ICDSRMeta meta) {
        history = meta;
    }

    @Override
    public Set<String> getLanguages() {
        return languages;
    }

    @Override
    public void setLanguages(Set<String> languages) {
        this.languages = languages;
    }

    public boolean isJats() {
        return jats;
    }

    public void setJats(boolean value) {
        jats = value;
    }

    public void setUnitStatusId(Integer unitStatusId) {
        this.unitStatusId = unitStatusId;
    }

    public Integer getUnitStatusId() {
        return unitStatusId;
    }

    public Integer getHistoryNumber() {
        return historyNumber;
    }

    public void setHistoryNumber(Integer number) {
        historyNumber = number;
    }

    public String getRecordPath() {
        return getPath();
    }

    public void setRecordPath(String path) {
        setPath(path);
    }

    @Override
    public boolean isScheduled() {
        return CmsUtils.isScheduledIssueNumber(getIssue());
    }

    @Override
    public boolean isHighProfile() {
        return highProfile;
    }

    public void setHighProfile(boolean value) {
        highProfile = value;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean value) {
        successful = value;
    }

    //@Override
    //public String toString() {
    //    return String.format("%s [%d] %s jats=%b %d (%d/%d) %s v%s",
    //        super.toString(), getId(), stage, isJats(), citationChangedDate.issue(), issue, versionFirstDate.issue(),
    //            group, historyNumber);
    //}

    public static String getPublishDatesStr(int versionIssue, Date versionDate, String onlineFinal, int citationIssue,
                                            String onlineCitation, int selfCitationIssue, String firstOnline) {
        return String.format(
            "\nversionFirst: %d %s; publishedOnlineFinalForm: %s\ncitation: %d %s; self-citation: %d\nfirstOnline: %s",
                versionIssue, versionDate, onlineFinal, citationIssue, onlineCitation, selfCitationIssue, firstOnline);
    }
}
