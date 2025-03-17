package com.wiley.cms.cochrane.utils;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 26.10.12
 *
 * @param <T> An entity related with this error info.
 */
public class ErrorInfo<T extends Serializable> implements Serializable {
    public static final ErrorInfo SPD_CANCELED = new ErrorInfo(Type.SPD_CANCELED);

    private static final long serialVersionUID = 1L;

    private static final Set<Type> COMMON_FLOW_RECORD_VALIDATION_ERR_TYPES = new HashSet<>();

    private final boolean isError;
    private final Type errorType;
    private String errorDetail;
    private String shortMessage;

    private T errorEntity;

    static {
        COMMON_FLOW_RECORD_VALIDATION_ERR_TYPES.add(ErrorInfo.Type.CONTENT);
        COMMON_FLOW_RECORD_VALIDATION_ERR_TYPES.add(ErrorInfo.Type.METADATA);
        COMMON_FLOW_RECORD_VALIDATION_ERR_TYPES.add(ErrorInfo.Type.VALIDATION);
        COMMON_FLOW_RECORD_VALIDATION_ERR_TYPES.add(ErrorInfo.Type.VALIDATION_SPD);
        COMMON_FLOW_RECORD_VALIDATION_ERR_TYPES.add(ErrorInfo.Type.SPD_CANCELED);
    }

    public ErrorInfo(T entity, String errorDetail) {
        this(entity, true, Type.SYSTEM, errorDetail, null);
    }

    public ErrorInfo(T entity, boolean isError) {
        this(entity, isError, Type.SYSTEM, "", null);
    }

    public ErrorInfo(Type errorType) {
        this(null, true, errorType, errorType.getMsg(), null);
    }

    public ErrorInfo(Type errorType, boolean isError) {
        this(null, isError, errorType, errorType.getMsg(), null);
    }

    public ErrorInfo(T entity, Type errorType, String errorDetail) {
        this(entity, true, errorType, errorDetail, errorDetail);
    }

    public ErrorInfo(T entity, Type errorType, String errorDetail, String shortMessage) {
        this(entity, true, errorType, errorDetail, shortMessage);
    }

    public ErrorInfo(T entity, boolean isError, Type errorType, String errorDetail, String shortMessage) {

        this.errorEntity = entity;
        this.isError = isError;
        this.errorType = errorType;
        this.errorDetail = errorDetail;

        setShortMessage(shortMessage);
    }

    public static ErrorInfo missingArticle(Serializable meta) {
        return new ErrorInfo<>(meta, ErrorInfo.Type.METADATA, "an article is missing");
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public void setErrorDetails(String detail)  {
        errorDetail = detail;
    }

    private void setShortMessage(String message)  {
        if (message == null) {
            return;
        }
        shortMessage = message;
    }

    public String getShortMessage() {
        return shortMessage;
    }

    public T getErrorEntity() {
        return errorEntity;
    }

    public void setErrorEntity(T entity) {
        errorEntity = entity;
    }

    public boolean isError() {
        return isError;
    }

    public boolean isSystemError() {
        return isError && errorType == Type.SYSTEM;
    }

    public boolean isRecordBlockedError() {
        return isError && errorType == Type.RECORD_BLOCKED;
    }

    public boolean is500ServerError() {
        return errorType == Type.HTTP_500_SYSTEM;
    }

    public Type getErrorType() {
        return errorType;
    }

    public boolean isCommonFlowRecordValidationError() {
        return COMMON_FLOW_RECORD_VALIDATION_ERR_TYPES.contains(getErrorType());
    }

    @Override
    public String toString() {
        return String.format("%s - %s (%s)", errorEntity, errorType, errorDetail);
    }

    /**
     * Error type
     */
    public enum Type {

        SYSTEM("system error"),
        RECORD_BLOCKED("blocked record"),
        HTTP_500_SYSTEM("5xx server error"),
        VALIDATION("validation error"),
        VALIDATION_SPD("SPD validation error"),
        SPD_CANCELED("scheduled publication cancelled"),
        REVMAN_CONVERSION("conversion error"),
        METADATA("metadata error"),
        CRG_GROUP("a error for articles under CRG group"),
        CONTENT("content error"),
        EMPTY_CONTENT("empty content"),
        NO_SPEC_RENDERING("special rendering not found"),
        NO_PUBLISHING_RECORD("no records for publication"),
        NO_GENERATING_RECORD_FOUND("no records for generation found within given scope"),
        ML3G_CONVERSION("ML3G conversion is not successful"),
        QA_STAGE("QA is not successful"),
        RENDERING_STAGE("rendering is not successful"),
        WRONG_SEMANTICO_SID("this message ID doesn't belong current profile"),
        NO_UNIT_STATUS("status information not found"),
        WRONG_UNIT_STATUS("such a status is not supported"),
        WRONG_DOI("DOI invalid"),
        WRONG_DOI_PATTERN("DOI unrecognized"),
        WRONG_ACCESSION_ID_DOI("accession id DOI unrecognized"),
        WRONG_ACCESSION_ID_REF("accession id ref unrecognized"),
        WRONG_IMAGE_THUMBNAIL("cannot create thumbnail");

        private final String msg;

        Type(String msg) {
            this.msg = msg;
        }

        public String getMsg() {
            return msg;
        }

        @Override
        public String toString() {
            return msg;
        }
    }
}
