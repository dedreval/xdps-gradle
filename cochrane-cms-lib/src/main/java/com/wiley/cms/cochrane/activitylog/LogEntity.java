package com.wiley.cms.cochrane.activitylog;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/26/2017
 */
@MappedSuperclass
public class LogEntity implements Serializable {
    /**
     *
     */
    public enum LogLevel {
        ERROR, WARN, INFO
    }

    /**
     *
     */
    public enum EntityLevel {
        SYSTEM, ISSUE, DB, FILE, RECORD, EXPORT, TERM2NUM, ENTIREDB, WR, PROCESS, ENTIRE, HISTORY, FLOW {

            @Override
            String getComments(LogEntity le) {
                return FlowLogCommentsPart.parseComments(le.getComments());
            }
        };

        String getComments(LogEntity le) {
            return le.getComments();
        }
    }

    private static final long serialVersionUID = 1L;

    private Long id;

    private Integer entityId;

    private String entityName;

    private Date date;

    private LogLevel logLevel;

    private EntityLevel entityLevel;

    private String comments;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Enumerated(EnumType.ORDINAL)
    @Column(updatable = false, nullable = false)
    public LogLevel getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(LogLevel level) {
        this.logLevel = level;
    }

    @Enumerated(EnumType.ORDINAL)
    @Column(updatable = false, nullable = false)
    public EntityLevel getEntityLevel() {
        return entityLevel;
    }

    public void setEntityLevel(EntityLevel level) {
        this.entityLevel = level;
    }

    @Column(updatable = false, nullable = false)
    public int getEntityId() {
        return entityId;
    }

    public void setEntityId(int id) {
        this.entityId = id;
    }

    @Column(updatable = false)
    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    @Column(updatable = false, nullable = false)
    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    @Column(updatable = false)
    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
