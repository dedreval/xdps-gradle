package com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions;

import io.swagger.v3.oas.annotations.media.Schema;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.UUID;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

public class AuthenticationException extends Exception {
    private String errorCode;
    private UUID refId;

    public AuthenticationException(String message) {
        super(message);
    }

    @Schema(example = "AUTHSVC_UNAUTHORIZED", description = "Authentication error code")
    @JsonProperty("errorCode")
    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    @Schema(description = "Transaction ID")
    @JsonProperty("refId")
    public UUID getRefId() {
        return refId;
    }

    public void setRefId(UUID refId) {
        this.refId = refId;
    }

    @Override
    public String toString() {
        return String.format("{\n  \"errorCode\": \"%s\",\n"
                + "  \"refId\": \"%s\"\n}", errorCode, refId);
    }
}
