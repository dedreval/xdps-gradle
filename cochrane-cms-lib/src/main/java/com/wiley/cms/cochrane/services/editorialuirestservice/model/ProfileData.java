package com.wiley.cms.cochrane.services.editorialuirestservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

public class ProfileData {
    private String timeZoneOffset;

    public ProfileData() {
    }

    public ProfileData(String timeZoneOffset) {
        this.timeZoneOffset = timeZoneOffset;
    }

    @Schema(description = "Time Zone Offset in hours")
    @JsonProperty("timeZoneOffset")
    @NotNull
    @NotBlank
    public String getTimeZoneOffset() {
        return timeZoneOffset;
    }

    public void setTimeZoneOffset(String timeZoneOffset) {
        this.timeZoneOffset = timeZoneOffset;
    }
}