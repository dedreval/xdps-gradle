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

import com.wiley.cms.process.ExternalProcess;
import com.wiley.cms.process.IProcessManager;
import com.wiley.cms.process.ProcessHandler;
import com.wiley.cms.process.ProcessState;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 24.06.13
 */
@Entity
@Table(name = "CMS_PROCESS")
@NamedQueries({
        @NamedQuery(
            name = ProcessEntity.QUERY_SELECT_ALL,
            query = "SELECT p FROM ProcessEntity p"
        ),
        @NamedQuery(
                name = ProcessEntity.QUERY_SELECT_BY_LABEL,
                query = "SELECT new com.wiley.cms.process.ProcessVO(p) FROM ProcessEntity p WHERE p.label =:label"
                        + " ORDER BY p.id"
        ),
        @NamedQuery(
            name = ProcessEntity.QUERY_SELECT_BY_TYPE,
            query = "SELECT new com.wiley.cms.process.ProcessVO(p) FROM ProcessEntity p WHERE p.type =:ty"
        ),
        @NamedQuery(
                name = ProcessEntity.QUERY_SELECT_BY_CREATOR_ID_AND_STATES,
                query = "SELECT new com.wiley.cms.process.ProcessVO(p) FROM ProcessEntity p"
                        + " WHERE p.creatorId =:id AND p.state IN (:state)"
        ),
        @NamedQuery(
                name = ProcessEntity.QUERY_SELECT_BY_LABEL_AND_STATES_ORDERED_BY_PRIORITY_AND_CREATION_DATE,
                query = "SELECT new com.wiley.cms.process.ProcessVO(p) FROM ProcessEntity p"
                        + " WHERE p.label =:label AND p.state IN (:state) ORDER BY p.priority DESC, p.creationDate"
        ),
        @NamedQuery(
            name = ProcessEntity.QUERY_SELECT_CHILDREN,
            query = "SELECT new com.wiley.cms.process.ExternalProcess(p) FROM ProcessEntity p WHERE p.creatorId =:id"
        ),
        @NamedQuery(
            name = ProcessEntity.QUERY_SELECT_CHILDREN_BY_STATE,
            query = "SELECT new com.wiley.cms.process.ExternalProcess(p) FROM ProcessEntity p WHERE p.creatorId =:id"
                    + " AND p.state =:state"
         ),
        @NamedQuery(
            name = ProcessEntity.QUERY_COUNT_CHILDREN,
            query = "SELECT COUNT (p) FROM ProcessEntity p WHERE p.creatorId =:id"
        ),
        @NamedQuery(
            name = ProcessEntity.QUERY_COUNT_CHILDREN_BY_STATES,
            query = "SELECT COUNT (p) FROM ProcessEntity p"
                        + " WHERE p.creatorId =:id AND p.state IN (:state) ORDER BY p.creationDate"
        ),
        @NamedQuery(
            name = ProcessEntity.QUERY_COUNT_CHILDREN_BY_NOT_STATES,
            query = "SELECT COUNT (p) FROM ProcessEntity p WHERE p.creatorId =:id AND p.state NOT IN (:state)"
        )
    })
public class ProcessEntity extends DbEvent {

    static final String QUERY_SELECT_ALL = "processAll";
    static final String QUERY_SELECT_BY_LABEL = "processByLabel";
    static final String QUERY_SELECT_BY_TYPE = "processByType";
    static final String QUERY_SELECT_BY_CREATOR_ID_AND_STATES = "processByCreatorIdAndStates";
    static final String QUERY_SELECT_BY_LABEL_AND_STATES_ORDERED_BY_PRIORITY_AND_CREATION_DATE =
            "processByLabelAndStatesOrderedByLabelAndCreationDate";
    static final String QUERY_SELECT_CHILDREN = "processChildren";
    static final String QUERY_SELECT_CHILDREN_BY_STATE = "processChildrenByState";
    static final String QUERY_DELETE_BY_ID = "deleteProcessById";
    static final String QUERY_COUNT_CHILDREN = "countProcessChildren";
    static final String QUERY_COUNT_CHILDREN_BY_STATES = "countProcessChildrenByStates";
    static final String QUERY_COUNT_CHILDREN_BY_NOT_STATES = "countProcessChildrenByNotStates";

    private int priority = IProcessManager.USUAL_PRIORITY;
    private int creatorId = DbEntity.NOT_EXIST_ID;
    private int nextId = DbEntity.NOT_EXIST_ID;
    private int type = 0;
    private String owner;

    public ProcessEntity() {
    }

    ProcessEntity(int creatorId, ProcessHandler ph, String label, ProcessState state) {

        setParams(ph.getParamString());
        setCreatorId(creatorId);
        setLabel(label);
        setLastDate(new Date());
        setCreationDate(getLastDate());
        setState(state);
    }

    public static Query queryProcessChildren(int processId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_CHILDREN).setParameter(PARAM_ID, processId);
    }

    public static Query queryProcessChildren(int processId, ProcessState state, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_CHILDREN_BY_STATE).setParameter(
                PARAM_ID, processId).setParameter(PARAM_STATE, state);
    }

    public static Query queryProcessChildrenCount(int processId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_COUNT_CHILDREN).setParameter(PARAM_ID, processId);
    }

    public static Query queryProcessChildrenCount(int processId, Collection<ProcessState> states,
        EntityManager manager) {
        return manager.createNamedQuery(QUERY_COUNT_CHILDREN_BY_STATES).setParameter(PARAM_ID, processId)
                .setParameter(PARAM_STATE, states);
    }

    public static Query queryProcessChildrenCountNot(int processId, Collection<ProcessState> states,
        EntityManager manager) {
        return manager.createNamedQuery(QUERY_COUNT_CHILDREN_BY_NOT_STATES).setParameter(
                PARAM_ID, processId).setParameter(PARAM_STATE, states);
    }

    public static Query queryProcess(String label, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_LABEL).setParameter(PARAM_LABEL, label);
    }

    public static Query queryProcess(int type, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_TYPE).setParameter("ty", type);
    }

    public static Query queryProcess(int processId, Collection<ProcessState> states, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_CREATOR_ID_AND_STATES).setParameter(PARAM_ID, processId)
                .setParameter(PARAM_STATE, states);
    }

    public static Query queryProcessOrderedByPriorityAndCreationDate(String label, Collection states,
        EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_LABEL_AND_STATES_ORDERED_BY_PRIORITY_AND_CREATION_DATE)
                .setParameter(PARAM_LABEL, label).setParameter(PARAM_STATE, states);
    }

    @Column(name = "creator", nullable = false, updatable = false)
    public int getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(int creatorId) {
        this.creatorId = creatorId;
    }

    @Column(name = "next", nullable = false)
    public int getNextId() {
        return nextId;
    }

    public void setNextId(int nextId) {
        this.nextId = nextId;
    }


    @Column(nullable = false, updatable = false)
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Column(updatable = false)
    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    @Column(length = STRING_VARCHAR_LENGTH_64, updatable = false)
    public String getOwner() {
        return owner;
    }

    public void setOwner(String user) {
        this.owner = user;
    }

    @Override
    public String toString() {
        return ExternalProcess.toString(getLabel(), getCreatorId(), getId());
    }
}
