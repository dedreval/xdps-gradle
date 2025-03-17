package com.wiley.cms.cochrane.services.editorialuirestservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.codehaus.jackson.annotate.JsonProperty;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

public class AuthenticationSuccessResponse {
    private String firstName;
    private String lastName;
    private String email;

    public AuthenticationSuccessResponse(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }

    @Schema(description = "Authenticated user first name")
    @JsonProperty("firstName")
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @Schema(description = "Authenticated user last name")
    @JsonProperty("lastName")
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Schema(description = "Authenticated user email")
    @JsonProperty("email")
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
