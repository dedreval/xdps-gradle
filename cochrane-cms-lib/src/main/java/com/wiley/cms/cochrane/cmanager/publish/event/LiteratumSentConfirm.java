package com.wiley.cms.cochrane.cmanager.publish.event;

import org.apache.commons.lang.StringUtils;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import com.wiley.cms.cochrane.cmanager.CochraneCMSPropertyNames;

/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 07.07.2017
 */
public class LiteratumSentConfirm implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonRequired
    private String eventId = StringUtils.EMPTY;

    private String transactionId = StringUtils.EMPTY;

    @JsonRequired
    private String sourceSystem = StringUtils.EMPTY;

    @JsonRequired
    private String eventType = StringUtils.EMPTY;

    @JsonRequired
    private String doi = StringUtils.EMPTY;

    @JsonRequired
    private String endedAtTime = StringUtils.EMPTY;

    private List<Messages> messages = Collections.emptyList();

    private String deliveryId = StringUtils.EMPTY;

    private List<WorkflowEventGroup> workflowEventGroup = Collections.emptyList();

    private List<HasPart> hasPart = Collections.emptyList();

    private String rawData;

    private Date responseDate;

    private boolean firstPublishedOnline;

    private boolean fullGroup;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDoi() {
        return doi;
    }

    public void setDoi(String doi) {
        this.doi = doi;
    }

    public String getEndedAtTime() {
        return endedAtTime;
    }

    public void setEndedAtTime(String endedAtTime) {
        this.endedAtTime = endedAtTime;
    }

    public Date getResponseDate() {
        return responseDate;
    }

    public void setResponseDate(Date date) {
        this.responseDate = date;
    }

    public List<Messages> getMessages() {
        return messages;
    }

    public void setMessages(List<Messages> messages) {
        this.messages = messages;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public boolean getFullGroup() {
        return fullGroup;
    }

    public void setFullGroup(boolean value) {
        fullGroup = value;
    }

    public List<WorkflowEventGroup> getWorkflowEventGroup() {
        return workflowEventGroup;
    }

    public void setWorkflowEventGroup(List<WorkflowEventGroup> workflowEventGroup) {
        this.workflowEventGroup = workflowEventGroup;
    }

    public String getWorkflowEventValue(String workflowEventType) {
        return workflowEventGroup.stream()
                       .filter(eventGroup -> eventGroup.getWorkflowEventType().equals(workflowEventType))
                       .findFirst()
                       .map(WorkflowEventGroup::getWorkflowEventValue)
                       .orElse(null);
    }

    public List<HasPart> getHasPart() {
        return hasPart;
    }

    public void setHasPart(List<HasPart> hasPart) {
        this.hasPart = hasPart;
    }

    public boolean isFirstPublishedOnline() {
        return firstPublishedOnline;
    }

    public void setFirstPublishedOnline(boolean firstPublishedOnline) {
        this.firstPublishedOnline = firstPublishedOnline;
    }

    public boolean isHW() {
        return getSourceSystem().equals(CochraneCMSPropertyNames.getLiteratumSourceSystemFilterSemantico());
    }

    public boolean isOnline() {
        return getEventType().equals(CochraneCMSPropertyNames.getLiteratumEventOnlineFilter());
    }

    public boolean isOffline() {
        return getEventType().equals(CochraneCMSPropertyNames.getLiteratumEventOfflineFilter());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LiteratumSentConfirm that = (LiteratumSentConfirm) o;
        return this == o
                || Objects.equals(eventId, that.eventId)
                && Objects.equals(transactionId, that.transactionId)
                && Objects.equals(sourceSystem, that.sourceSystem)
                && Objects.equals(eventType, that.eventType)
                && Objects.equals(doi, that.doi)
                && Objects.equals(endedAtTime, that.endedAtTime)
                && Objects.equals(deliveryId, that.deliveryId)
                && Objects.equals(workflowEventGroup, that.workflowEventGroup)
                && Objects.equals(firstPublishedOnline, that.firstPublishedOnline)
                && Objects.equals(hasPart, that.hasPart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, transactionId, sourceSystem, eventType, doi, endedAtTime, deliveryId, hasPart,
                            workflowEventGroup, firstPublishedOnline);
    }

    @Override
    public String toString() {
        return "LiteratumSentConfirm{"
                + "eventId='" + eventId + '\''
                + transactionId == null ? "" : (", transactionId='" + transactionId + '\'')
                + ", sourceSystem='" + sourceSystem + '\''
                + ", eventType='" + eventType + '\''
                + ", doi='" + doi + '\''
                + ", endedAtTime='" + endedAtTime + '\''
                + ", messages='" + messages + '\''
                + ", deliveryId='" + deliveryId + '\''
                + ", workflowEventGroup='" + workflowEventGroup + '\''
                + ", firstPublishedOnline=" + firstPublishedOnline
                + ", fullGroup=" + fullGroup
                + ", hasPart='" + hasPart + '\''
                + '}';
    }

    /**
     * Literatum LOAD_TO_PUBLISH error/info messages
     */
    public static class Messages implements Serializable {
        private static final long serialVersionUID = 1L;

        private String messageLevel = StringUtils.EMPTY;
        private String messageText = StringUtils.EMPTY;
        private String messageUri = StringUtils.EMPTY;

        public Messages() {
        }

        public Messages(String level, String text, String uri) {
            this.messageLevel = level;
            this.messageText = text;
            this.messageUri = uri;
        }

        public String getMessageLevel() {
            return messageLevel;
        }

        public void setMessageLevel(String level) {
            this.messageLevel = level;
        }

        public String getMessageText() {
            return messageText;
        }

        public void setMessageText(String text) {
            this.messageText = text;
        }

        public String getMessageUri() {
            return messageUri;
        }

        public void setMessageUri(String uri) {
            this.messageUri = uri;
        }

        @Override
        public String toString() {
            return "Messages{messageLevel='" + messageLevel + "', messageText='" + messageText
                    + "', messageUri='" + messageUri + "'}";
        }

        public String getMessageAsString() {
            return "{level=" + messageLevel + ", text=" + messageText
                    + (StringUtils.isNotEmpty(messageUri) ? (", uri=" + messageUri) : "") + "}";
        }
    }

    /**
     *
     */
    public static class WorkflowEventGroup implements Serializable {
        private static final long serialVersionUID = 1L;

        private String workflowEventType = StringUtils.EMPTY;
        private String workflowEventValue = StringUtils.EMPTY;

        public WorkflowEventGroup() {
        }

        public WorkflowEventGroup(String workflowEventType, String workflowEventValue) {
            this.workflowEventType = workflowEventType;
            this.workflowEventValue = workflowEventValue;
        }

        public String getWorkflowEventType() {
            return workflowEventType;
        }

        public void setWorkflowEventType(String workflowEventType) {
            this.workflowEventType = workflowEventType;
        }

        public String getWorkflowEventValue() {
            return workflowEventValue;
        }

        public void setWorkflowEventValue(String workflowEventValue) {
            this.workflowEventValue = workflowEventValue;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            WorkflowEventGroup that = (WorkflowEventGroup) o;
            return this == o || Objects.equals(workflowEventType, that.workflowEventType)
                                        && Objects.equals(workflowEventValue, that.workflowEventValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(workflowEventType, workflowEventValue);
        }

        @Override
        public String toString() {
            return "WorkflowEventGroup{workflowEventType='" + workflowEventType + '\''
                           + ", workflowEventValue='" + workflowEventValue + '\''
                           + '}';
        }
    }

    /**
     *
     */
    public static class HasPart implements Serializable {
        private static final long serialVersionUID = 1L;

        private String doi = StringUtils.EMPTY;

        private List<Messages> messages = Collections.emptyList();

        public HasPart() {
        }

        public HasPart(String doi) {
            this.doi = doi;
        }

        public String getDoi() {
            return doi;
        }

        public void setDoi(String doi) {
            this.doi = doi;
        }

        public List<Messages> getMessages() {
            return messages;
        }

        public void setMessages(List<Messages> messages) {
            this.messages = messages;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof HasPart)) {
                return false;
            }
            return this.doi.equals(((HasPart) o).doi);
        }

        @Override
        public int hashCode() {
            return doi.hashCode();
        }

        @Override
        public String toString() {
            return "Part{doi='" + doi + "'}";
        }
    }

    /**
     *
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface JsonRequired {
    }
}
