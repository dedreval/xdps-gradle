package com.wiley.cms.notification.service;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author <a href='mailto:nchernyshe@wiley.com'>Nikita Chernyshev</a>
 * Date: 14/3/2024
 */

public class User {
    private String email;
    private String name;

    public User(String email, String name) {
        this.email = email;
        this.name = name;
    }

    @JsonProperty("email")
    public String getEmail() {
        return email;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
