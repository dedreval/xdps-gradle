package com.wiley.cms.notification.service;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author <a href='mailto:nchernyshe@wiley.com'>Nikita Chernyshev</a>
 * Date: 25/3/2024
 */

public class AsNotification {

    public static final String XDPS = "XDPS";
    public static final String EMAIL = "email";
    public static final String HTML = "html";
    private String applicationId;
    private Sender sender;
    private List<User> recipients;
    private String type;
    private String format;
    private String subject;
    private String content;

    public AsNotification(String senderEmail, List<User> recipients, String subject, String body) {
        this.applicationId = XDPS;
        this.sender = new Sender(senderEmail);
        this.recipients = recipients;
        this.type = EMAIL;
        this.format = HTML;
        this.subject = subject;
        this.content = generateHtmlContent(subject, body);
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public Sender getSender() {
        return sender;
    }

    public void setSender(Sender sender) {
        this.sender = sender;
    }

    public List<User> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<User> recipients) {
        this.recipients = recipients;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    private static String generateHtmlContent(String subject, String body) {
        String htmlBody = body.replace("\n", "<br/>");
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head>"
                + "<meta charset=\"UTF-8\"/>"
                + "<title>" + subject + "</title>"
                + "</head>"
                + "<body>"
                + htmlBody
                + "</body>"
                + "</html>";
    }
}

class Sender {
    private final String email;

    public Sender(String email) {
        this.email = email;
    }

    @JsonProperty("email")
    public String getEmail() {
        return email;
    }
}
