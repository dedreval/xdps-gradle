package com.wiley.cms.cochrane.cmanager;

import com.wiley.cms.cochrane.cmanager.contentworker.ArchieEntry;
import com.wiley.cms.cochrane.cmanager.entitymanager.CDSRMetaVO;
import com.wiley.cms.cochrane.utils.ErrorInfo;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 07.06.12
 */
public class CmsException extends Exception implements ICmsException {
    private static final long serialVersionUID = 1L;

    private final ErrorInfo errInfo;

    public CmsException(ErrorInfo errInfo) {

        super(errInfo.getErrorDetail());

        this.errInfo = errInfo;
    }

    public CmsException(String msg) {
        super(msg);
        errInfo = null;
    }

    public CmsException(Exception ex) {
        this(ex, null);
    }

    public CmsException(Exception ex, ErrorInfo errInfo) {
        super(ex);
        this.errInfo = errInfo;
    }

    @Override
    public CmsException get() {
        return this;
    }

    @Override
    public boolean hasErrorInfo() {
        return errInfo != null;
    }

    @Override
    public ErrorInfo getErrorInfo() {
        return errInfo;
    }

    public boolean isNoPublishingRecords() {
        return hasErrorInfo() && ErrorInfo.Type.NO_PUBLISHING_RECORD == getErrorInfo().getErrorType();
    }

    public boolean isNoGeneratingRecordsFound() {
        return hasErrorInfo() && ErrorInfo.Type.NO_GENERATING_RECORD_FOUND == getErrorInfo().getErrorType();
    }

    public static CmsException create(ArchieEntry meta, ErrorInfo.Type type) {
        return new CmsException(new ErrorInfo<>(meta, type, type.getMsg()));
    }

    public static CmsException create(ArchieEntry meta, ErrorInfo.Type type, String message) {
        return new CmsException(new ErrorInfo<>(meta, type, message));
    }

    public static CmsException createForContent(ArchieEntry meta, String message) {
        return new CmsException(new ErrorInfo<>(meta, ErrorInfo.Type.CONTENT, message));
    }

    public static CmsException createForMetadata(ArchieEntry meta, String message) {
        return new CmsException(new ErrorInfo<>(meta, ErrorInfo.Type.METADATA, message));
    }

    public static void throwMetadataError(CDSRMetaVO meta, String err) throws CmsException {
        throw meta == null ? new CmsException(err) : createForMetadata(meta, err);
    }

    public static CmsException createForValidation(ArchieEntry meta, String message, boolean spd) {
        return new CmsException(new ErrorInfo<>(meta, spd ? ErrorInfo.Type.VALIDATION_SPD
                : ErrorInfo.Type.VALIDATION, message));
    }

    public static CmsException createForScheduledIssue(int dfId) {
        return new CmsException(String.format("cannot upload delivery file [%d] to Scheduled Issue", dfId));
    }

    public static CmsException createForMissingMetadata(String packageFileName) {
        return new CmsException(String.format("%s contains no article details", packageFileName));
    }

    public static CmsException createForBeingProcessed(String cdNumber) {
        return new CmsException(new ErrorInfo<>(new ArchieEntry(cdNumber), ErrorInfo.Type.RECORD_BLOCKED,
                MessageSender.MSG_RECORD_PROCESSED));
    }

    //@Override
    //public synchronized Throwable fillInStackTrace() {
    //    return this;
    //}
}
