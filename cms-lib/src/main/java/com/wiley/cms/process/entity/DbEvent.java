package com.wiley.cms.process.entity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.Lob;
import javax.persistence.MappedSuperclass;

import com.wiley.cms.process.ProcessState;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/8/2014
 */
@MappedSuperclass
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class DbEvent extends LabelEntity {

    static final String PARAM_STATE = "state";
    static final String PARAM_LABEL = "label";

    private ProcessState state = ProcessState.WAITED;
    private String params;
    private Date lastDate;
    private Date creationDate;
    private Date startDate;
    private String message;

    public DbEvent() {
    }

    @Column(name = "startdate")
    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    @Column(name = "lastdate", nullable = false)
    public Date getLastDate() {
        return lastDate;
    }

    public void setLastDate(Date lastDate) {
        this.lastDate = lastDate;
    }

    @Column(name = "creationdate", nullable = false)
    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    @Column(nullable = false)
    @Enumerated(EnumType.ORDINAL)
    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    @Lob
    @Column(name = "params", nullable = false, updatable = false)
    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String msg) {
        message = checkSting(msg, STRING_VARCHAR_LENGTH_FULL);
    }
}

