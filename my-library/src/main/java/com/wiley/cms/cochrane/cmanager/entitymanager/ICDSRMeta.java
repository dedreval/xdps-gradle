package com.wiley.cms.cochrane.cmanager.entitymanager;

import java.util.Date;

import com.wiley.cms.cochrane.cmanager.data.record.IRecord;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 5/25/2019
 */
public interface ICDSRMeta extends IRecord {

    String getCommentedAsString();

    String getNihFundedAsString();

    String getApcWaiverAsString();

    boolean isNewCitation();

    String getNewCitationAsString();

    Date getCitationLastChanged();

    int getCitationIssue();

    int getPublishedIssue();

    Date getPublishedDate();

    int getProtocolFirstIssue();

    int getReviewFirstIssue();

    int getSelfCitationIssue();

    int getIssue();

    String getStage();

    boolean isFirstPub();

    String getCdNumber();

    String getGroupSid();

    String getGroupTitle();

    int getStatus();

    String getType();

    String getSubType();

    boolean isStageR();

    boolean isStageP();

    boolean isStageUP();

    String getTitle();

    String getRevmanId();

    void setRevmanId(String revmanId);

    boolean isGoldOpenAccess();

    String getAccessType();

    String getAccessTypeMetadata();

    boolean isDeleted();

    ICDSRMeta getHistory();

    void setHistory(ICDSRMeta meta);

    boolean isJats();

    boolean isScheduled();

    void setHistoryNumber(Integer historyNumber);

    boolean isHighProfile();

    default boolean notEqualIssuesVsCT() {
        return false;
    }
}
