package com.wiley.cms.cochrane.services.editorialuirestservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

/**
 * @author <a href='mailto:atolkachev@wiley.com'>Andrey Tolkachev</a>
 * Date: 29.08.19
 */

@XmlRootElement
public class AuthenticationRequestBody implements Serializable {
    private static final long serialVersionUID = 1L;

    private String email;
    private String password;
    private String systemId;
    private int maxAge;

    @Schema(required = true, description = "User email")
    @JsonProperty("email")
    @NotNull
    @NotBlank
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Schema(required = true, description = "User password hash (raw password before hash func is chosen)")
    @JsonProperty("password")
    @NotNull
    @NotBlank
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Schema(description = "System Id")
    @JsonProperty("systemId")
    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    @Schema(description = "Token expiration period in seconds")
    @JsonProperty("maxAge")
    public int getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
    }
}
