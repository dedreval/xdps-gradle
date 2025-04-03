package com.wiley.cms.cochrane.cmanager.data.record;

import java.util.Set;

import com.wiley.cms.cochrane.cmanager.entitymanager.ICDSRMeta;
import com.wiley.cms.cochrane.cmanager.translated.ITranslatedAbstractsInserter;
import com.wiley.cms.process.LabeledVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 11/2/2019
 */
public class TinyRecordVO extends LabeledVO implements IRecord {
    private static final long serialVersionUID = 1L;

    private int pub;
    private String path;
    private Integer unitStatusId;
    private Integer stageId;
    private int status;
    private boolean rawExist;
    private Integer historyNumber;
    private Integer dfId;
    private int versionFirstIssue;
    private String finalOnlineForm;
    private int citationIssue;
    private String firstOnlineDate;
    private String citationOnline;
    private int protocolFirstIssue;
    private int reviewFirstIssue;
    private int selfCitationIssue;

    private int insertTa;
    private Set<String> languages;
    private Set<String> retractedLanguages;

    private boolean jats;
    private String cochraneVersion = null;
    private boolean successful = true;
    private String message = null;

    public TinyRecordVO(RecordEntity re) {

        super(re.getId(), re.getName());

        initRecordFields(re.getRecordPath(), re.getSubTitle(), re.getUnitStatusId(), re.isRawDataExists(),
                re.getDeliveryFile().getId());
        ICDSRMeta rme = re.getMetadata();
        if (re.getMetadata() != null) {
            initMetaFields(rme.getPubNumber(), rme.getStatus(), rme.getCochraneVersion(), rme.getHistoryNumber(),
                rme.isJats());
            initPublishDates(rme.getPublishedIssue(), rme.getPublishedOnlineFinalForm(), rme.getCitationIssue(),
                rme.getPublishedOnlineCitation(), rme.getFirstOnline(), rme.getSelfCitationIssue());
            initFirstIssues(rme.getProtocolFirstIssue(), rme.getReviewFirstIssue());
        }
    }

    public TinyRecordVO(Integer id, String name, int pub, String path, Integer stageId, Integer unitStatusId,
        boolean rawExist, Integer dfId, int status, String cochraneVersion, Integer historyNumber,
        int versionFirstIssue, String finalOnlineForm, int citationIssue, String onlineCitation, String firstOnline,
        int protocolFirstIssue, int reviewFirstIssue, int selfCitationIssue, int metaType) {

        super(id, name);

        initRecordFields(path, stageId, unitStatusId, rawExist, dfId);
        initMetaFields(pub, status, cochraneVersion, historyNumber, metaType > 0);
        initPublishDates(versionFirstIssue, finalOnlineForm, citationIssue, onlineCitation, firstOnline,
                selfCitationIssue);
        initFirstIssues(protocolFirstIssue, reviewFirstIssue);

        insertTa = ITranslatedAbstractsInserter.INSERT_ISSUE;
    }

    @Override
    public String getName() {
        return getLabel();
    }

    @Override
    public Integer getSubTitle() {
        return stageId;
    }

    @Override
    public int getPubNumber() {
        return pub;
    }

    @Override
    public String getRecordPath() {
        return path;
    }

    @Override
    public void setRecordPath(String path) {
        this.path = path;
    }

    @Override
    public boolean isUnchanged() {
        return UnitStatusEntity.isUnchanged(unitStatusId);
    }

    @Override
    public boolean isStageR() {
        return ProductSubtitleEntity.ProductSubtitle.REVIEWS == stageId;
    }

    @Override
    public boolean isWithdrawn() {
        return UnitStatusEntity.isWithdrawn(status);
    }

    @Override
    public boolean insertTaFromEntire() {
        return insertTa == ITranslatedAbstractsInserter.INSERT_ENTIRE;
    }

    @Override
    public boolean isRawExist() {
        return rawExist;
    }

    @Override
    public void setLanguages(Set<String> languages) {
        this.languages = languages;
    }

    @Override
    public Set<String> getLanguages() {
        return languages;
    }

    @Override
    public void setRetractedLanguages(Set<String> languages) {
        this.retractedLanguages = languages;
    }

    @Override
    public Set<String> getRetractedLanguages() {
        return retractedLanguages;
    }

    @Override
    public Integer getHistoryNumber() {
        return historyNumber;
    }

    @Override
    public boolean isJats() {
        return jats;
    }

    @Override
    public Integer getDeliveryFileId() {
        return dfId;
    }

    @Override
    public int getPublishedIssue() {
        return versionFirstIssue;
    }

    @Override
    public int getCitationIssue() {
        return citationIssue;
    }

    @Override
    public int getProtocolFirstIssue() {
        return protocolFirstIssue;
    }

    @Override
    public int getReviewFirstIssue() {
        return reviewFirstIssue;
    }

    @Override
    public int getSelfCitationIssue() {
        return selfCitationIssue;
    }

    @Override
    public String getPublishedOnlineFinalForm() {
        return finalOnlineForm;
    }

    @Override
    public String getFirstOnline() {
        return firstOnlineDate;
    }

    @Override
    public String getPublishedOnlineCitation() {
        return citationOnline;
    }

    @Override
    public String getCochraneVersion() {
        return cochraneVersion;
    }

    @Override
    public boolean isSuccessful() {
        return successful;
    }

    @Override
    public void setSuccessful(boolean value) {
        this.successful = value;
    }

    @Override
    public void setMessages(String messages) {
        message = messages;
    }

    @Override
    public String getMessages() {
        return message;
    }

    private void initRecordFields(String path, Integer stageId, Integer unitStatusId, boolean rawExist, Integer dfId) {
        setRecordPath(path);
        this.stageId = stageId;
        this.unitStatusId = unitStatusId;
        this.rawExist = rawExist;
        this.dfId = dfId;
    }

    public void initPublishDates(int versionFirstIssue, String finalOnlineForm, int citationIssue, String citationDate,
                                 String firstOnlineDate, int selfCitationIssue) {
        this.versionFirstIssue = versionFirstIssue;
        this.finalOnlineForm = finalOnlineForm;
        this.citationIssue = citationIssue;
        this.citationOnline = citationDate;
        this.firstOnlineDate = firstOnlineDate;
        this.selfCitationIssue = selfCitationIssue;
    }

    public void initFirstIssues(int protocolFirstIssue, int reviewFirstIssue) {
        this.protocolFirstIssue = protocolFirstIssue;
        this.reviewFirstIssue = reviewFirstIssue;
    }

    public void initMetaFields(int pub, int status, String cochraneVersion, Integer historyNumber, boolean jats) {
        this.pub = pub;
        this.status = status;
        this.cochraneVersion = cochraneVersion;
        this.historyNumber = historyNumber;
        this.jats = jats;
    }
}
