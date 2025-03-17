package com.wiley.cms.cochrane.services.editorialuirestservice.model.exceptions;

import io.swagger.v3.oas.annotations.media.Schema;
import org.codehaus.jackson.annotate.JsonProperty;

import java.time.Instant;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

public class DefaultException extends Exception {
    private Instant timestamp;
    private Integer status;
    private String error;
    private String exception;
    private String message;
    private String path;

    public DefaultException(String message) {
        super(message);
        setMessage(message);
    }

    @Schema()
    @JsonProperty("timestamp")
    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Schema(description = "HTTP status code")
    @JsonProperty("status")
    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Schema(description = "HTTP status name")
    @JsonProperty("error")
    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    @Schema(description = "Exception class")
    @JsonProperty("exception")
    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    @Schema()
    @JsonProperty("message")
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Schema()
    @JsonProperty("path")
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return String.format("{\n  \"timestamp\": \"%s\",\n"
                + "  \"status\": %d,\n"
                + "  \"error\": \"%s\",\n"
                + "  \"exception\": \"%s\",\n"
                + "  \"message\": \"%s\",\n"
                + "  \"path\": \"%s\"\n"
                + "}", timestamp, status, error, exception, message, path);
    }
}
