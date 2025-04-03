package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.data.record.RecordMetadataEntity;
import com.wiley.cms.cochrane.utils.ErrorInfo;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 * Date: 10/20/2019
 */
public class PreviousVersionException extends CmsJTException {

    public PreviousVersionException(ErrorInfo errInfo) {
        super(errInfo);
    }

    public PreviousVersionException(String msg) {
        super(msg);
    }

    public static PreviousVersionException createForMetadata(ArchieEntry meta, String message) {
        return new PreviousVersionException(new ErrorInfo<>(meta, ErrorInfo.Type.METADATA, message));
    }

    public static void checkPreviousRecord(ArchieEntry ae, RecordMetadataEntity prev) throws PreviousVersionException {
        if (prev.getPubNumber() > ae.getPubNumber()) {
            throw createForMetadata(ae, String.format(
                    "a publication number %d is less than the latest one %d", ae.getPubNumber(), prev.getPubNumber()));
        }
    }

    public static void checkTranslationRecord(ArchieEntry ae, RecordMetadataEntity rm) throws PreviousVersionException {
        if (ae.getPubNumber() != rm.getPubNumber()) {
            throw createForMetadata(ae, msgTranslationPublicationNumber(ae.getPubNumber(), rm.getPubNumber()));
        }
    }

    public static String msgAlreadyLifeSpd(String publisherId) {
        return String.format(
                "The latest version %s is already LIVE. SPD should not be set for DOI which has been published.",
                    publisherId);
    }

    public static String msgAmendedArticlePublicationDate() {
        return "an amended article must have a pre-defined first-online publication date";
    }

    public static String msgAlreadySubmittedSpdLast(String publisherId) {
        return String.format("The latest version %s has already been submitted with SPD", publisherId);
    }

    public static String msgAlreadySubmittedSpd(String publisherId) {
        return String.format("%s %s ", publisherId, CochraneCMSProperties.getProperty("spd_submitted"));
    }

    public static String msgAlreadySubmittedSpd(String publisherId, String prefix, String notAllowedPublisherId) {
        return String.format("%s has already been submitted with SPD. The %s version %s is not allowed.",
                publisherId, prefix, notAllowedPublisherId);
    }

    public static String msgTranslationPublicationNumber(int translationPub, int latestPub) {
        return String.format("a translation publication number %d does not match to the latest entire version %d",
                translationPub, latestPub);
    }
}
