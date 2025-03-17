package com.wiley.cms.process.entity;

import java.util.Collection;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;

import com.wiley.cms.process.ProcessState;
import com.wiley.cms.process.task.TaskVO;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 12/8/2014
 */
@Entity
@Table(name = "CMS_TASK")
@NamedQueries({
        @NamedQuery(
            name = TaskEntity.QUERY_SELECT_ALL,
            query = "SELECT new com.wiley.cms.process.task.TaskVO(e) FROM TaskEntity e"
        ),
        @NamedQuery(
            name = TaskEntity.QUERY_SELECT_BY_LABEL,
            query = "SELECT new com.wiley.cms.process.task.TaskVO(e) FROM TaskEntity e WHERE e.label =:label"
        ),
        @NamedQuery(
            name = TaskEntity.QUERY_SELECT_BY_PREFIX,
            query = "SELECT new com.wiley.cms.process.task.TaskVO(e) FROM TaskEntity e WHERE e.label LIKE:label"
        ),
        @NamedQuery(
            name = TaskEntity.QUERY_SELECT_BY_LABELS,
            query = "SELECT new com.wiley.cms.process.task.TaskVO(e) FROM TaskEntity e WHERE e.label IN (:label)"
        ),
        //@NamedQuery(
        //    name = TaskEntity.QUERY_SELECT_BY_STATE,
        //    query = "SELECT new com.wiley.cms.event.TaskVO(e) FROM TaskEntity e WHERE e.state in (:state)"
        //),
        @NamedQuery(
            name = TaskEntity.QUERY_UPDATE_STATE_ON_SENT,
            query = "UPDATE TaskEntity e SET e.state =:state, e.sent=:s WHERE e.id IN (:ids)"
        ),
        @NamedQuery(
            name = TaskEntity.QUERY_UPDATE_ON_SENT,
            query = "UPDATE TaskEntity e SET e.sent=:s WHERE e.id =:id"
        )
    })
public class TaskEntity extends DbEvent {
    static final String QUERY_SELECT_ALL = "taskAll";
    static final String QUERY_SELECT_BY_LABEL = "taskByLabel";
    static final String QUERY_SELECT_BY_PREFIX = "taskByPrefix";
    //static final String QUERY_SELECT_BY_STATE = "eventByState";
    static final String QUERY_SELECT_BY_LABELS = "taskByLabels";
    static final String QUERY_UPDATE_STATE_ON_SENT = "updateTaskStateOnSent";
    static final String QUERY_UPDATE_ON_SENT = "updateTaskOnSent";

    private static final int SCHEDULE_LENGTH = 64;

    private String schedule;
    private int count = 0;
    private boolean fast = false;
    private boolean sent = false;

    public TaskEntity() {
    }

    public TaskEntity(TaskVO event) {

        setParams(event.getHandler().getParamString());
        setLabel(event.getLabel());
        setState(event.getState());
        setStartDate(event.getStartDate());
        setCreationDate(event.getCreationDate());
        setLastDate(new Date());
        setSchedule(event.getSchedule());
        setFast(event.isFast());
    }

    public static Query queryTask(EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_ALL);
    }

    public static Query queryTask(String label, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_LABEL).setParameter(PARAM_LABEL, label);
    }

    public static Query queryTaskByPrefix(String prefix, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_PREFIX).setParameter(PARAM_LABEL, prefix + "%");
    }

    //public static Query queryEvent(Collection<ProcessState> states, EntityManager manager) {
    //    return manager.createNamedQuery(QUERY_SELECT_BY_STATE).setParameter(ProcessEntity.PARAM_STATE, states);
    //}

    public static Query queryTask(Collection labels, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_LABELS).setParameter(PARAM_LABEL, labels);
    }

    public static Query queryUpdateTaskStateOnSent(Collection<Integer> taskIds, ProcessState state, boolean sent,
                                                   EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_STATE_ON_SENT).setParameter(
            ProcessEntity.PARAM_STATE, state).setParameter(PARAM_IDS, taskIds).setParameter("s", sent);
    }

    public static Query queryUpdateTaskOnSent(int taskId, boolean sent, EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_ON_SENT).setParameter("id", taskId).setParameter("s", sent);
    }

    public String getSchedule() {
        return schedule;
    }

    @Column(name = "schedule", length = SCHEDULE_LENGTH)
    public void setSchedule(String value) {
        schedule = value;
    }

    @Column(name = "was_run")
    public int getRunCount() {
        return count;
    }

    public void setRunCount(int count) {
        this.count = count;
    }

    @Column(name = "fast", updatable = false)
    public boolean isFast() {
        return fast;
    }

    public void setFast(boolean value) {
        fast = value;
    }

    @Column(name = "sent")
    public boolean isSent() {
        return sent;
    }

    public void setSent(boolean value) {
        sent = value;
    }
}
