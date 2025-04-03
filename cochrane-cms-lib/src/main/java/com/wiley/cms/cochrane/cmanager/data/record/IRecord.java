package com.wiley.cms.cochrane.cmanager.data.record;

import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.cochrane.utils.Constants;


/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 6/4/2019
 */
public interface IRecord extends Serializable {

    String getName();

    default Integer getId() {
        return Constants.UNDEF;
    }

    default void setId(int id) {
    }

    default Integer getDeliveryFileId() {
        return null;
    }

    default Integer getUnitStatusId() {
        return null;
    }

    default String getRecordPath() {
        return null;
    }

    default void setRecordPath(String path) {
    }

    default Set<String> getLanguages() {
        return null;
    }

    default void setLanguages(Set<String> languages) {
    }

    default void addLanguages(Set<String> toAdd) {
        if (getLanguages() == null) {
            setLanguages(new HashSet<>());
        }
        if (getLanguages() != null) {
            getLanguages().addAll(toAdd);
        }
    }

    default Set<String> getRetractedLanguages() {
        return null;
    }

    default void setRetractedLanguages(Set<String> languages) {
    }

    default boolean isRawExist() {
        return false;
    }

    default void setRawExist(boolean  exist) {
    }

    default boolean isStageR() {
        return false;
    }

    default boolean isWithdrawn() {
        return false;
    }

    default boolean isUnchanged() {
        return true;
    }

    default Integer getSubTitle() {
        return null;
    }

    default boolean insertTaFromEntire() {
        return true;
    }

    default void setName(String name) {
    }

    default boolean isSuccessful() {
        return true;
    }

    default boolean isCompleted() {
        return true;
    }

    default boolean isDeleted() {
        return false;
    }

    default void setSuccessful(boolean value) {
    }

    default void setSuccessful(boolean value, String msg) {
        setSuccessful(value);
        setMessages(msg);
    }

    default void setMessages(String messages) {
    }

    default void addMessages(String messages) {
    }

    default String getTitle() {
        return null;
    }

    default void setTitle(String title) {
    }

    default String getMessages() {
        return null;
    }

    default int getPubNumber() {
        return 0;
    }

    default Integer getHistoryNumber() {
        return null;
    }

    default void setHistoryNumber(Integer historyNumber) {
    }

    default String getPublisherId() {
        return RecordHelper.buildPubName(getName(), getPubNumber());
    }

    default boolean isJats() {
        return false;
    }

    default void setJats(boolean value) {
    }

    default boolean isWml3g() {
        return false;
    }

    default String getGroupSid() {
        return null;
    }

    default void setGroupSid(String groupSid) {
    }

    default String getCochraneVersion() {
        return null;
    }

    default int getPublishedIssue() {
        return 0;
    }

    default Date getPublishedDate() {
        return null;
    }

    default String getPublishedOnlineFinalForm() {
        return null;
    }

    default int getCitationIssue() {
        return 0;
    }

    default String getFirstOnline() {
        return null;
    }

    default String getPublishedOnlineCitation() {
        return null;
    }

    default int getProtocolFirstIssue() {
        return 0;
    }

    default int getReviewFirstIssue() {
        return 0;
    }

    default int getSelfCitationIssue() {
        return 0;
    }

    default StringBuilder getTranslationsState(Collection<String> list, String message, int up) {
        StringBuilder sb = new StringBuilder().append("\n").append(getName()).append(" - ").append(message).append(":");
        if (list != null && !list.isEmpty()) {
            list.forEach(lang -> sb.append(lang).append(", "));
        }
        return up == 2 ? sb : (up == 1
                ? sb.append(getTranslationsState(getRetractedLanguages(), "retracted languages", 2))
                : sb.append(getTranslationsState(getLanguages(), "languages", 1)));
    }
}
