package com.wiley.cms.cochrane.activitylog;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.Table;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/26/2017
 */
@MappedSuperclass
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "COCHRANE_ACTIVITY_LOG")
public abstract class BaseLogEntity extends LogEntity implements Serializable {

    private ActivityLogEventEntity event;
    private String who;

    @ManyToOne
    @JoinColumn(name = "log_event_id", updatable = false, nullable = false)
    public ActivityLogEventEntity getEvent() {
        return event;
    }

    public void setEvent(ActivityLogEventEntity event) {
        this.event = event;
    }

    @Column(updatable = false)
    public String getWho() {
        return who;
    }

    public void setWho(String who) {
        this.who = who;
    }
}
