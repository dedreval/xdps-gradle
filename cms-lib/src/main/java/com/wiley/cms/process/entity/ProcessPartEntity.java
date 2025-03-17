package com.wiley.cms.process.entity;

import com.wiley.cms.process.ProcessState;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;
import java.util.Collection;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 04.07.13
 */
@Entity
@Table(name = "CMS_PROCESS_PART")
@NamedQueries({
        @NamedQuery(
            name = ProcessPartEntity.QUERY_SELECT_BY_PROCESS,
            query = "SELECT new com.wiley.cms.process.ProcessPartVO(p) FROM ProcessPartEntity p"
                    + " WHERE p.parent.id = :prId"
        ),
        @NamedQuery(
            name = ProcessPartEntity.QUERY_SELECT_BY_IDS,
            query = "SELECT new com.wiley.cms.process.ProcessPartVO(p) FROM ProcessPartEntity p WHERE p.id IN (:ids)"
        ),
        @NamedQuery(
            name = ProcessPartEntity.QUERY_SELECT_UID_BY_PROCESS,
            query = "SELECT new java.lang.Integer(p.uri) FROM ProcessPartEntity p WHERE p.parent.id = :prId"
        ),
        @NamedQuery(
            name = ProcessPartEntity.QUERY_SELECT_MESSAGE_BY_PROCESS,
            query = "SELECT p.message FROM ProcessPartEntity p WHERE p.parent.id = :prId"
        ),
        @NamedQuery(
            name = ProcessPartEntity.QUERY_SELECT_BY_PROCESS_AND_STATE,
            query = "SELECT p.id FROM ProcessPartEntity p WHERE p.parent.id = :prId AND p.state in (:state)"
        ),
        @NamedQuery(
                name = ProcessPartEntity.QUERY_COUNT_BY_PROCESS,
                query = "SELECT COUNT (p) FROM ProcessPartEntity p WHERE p.parent.id = :prId"
        ),
        @NamedQuery(
            name = ProcessPartEntity.QUERY_COUNT_BY_PROCESS_AND_NO_STATE,
            query = "SELECT COUNT (p) FROM ProcessPartEntity p WHERE p.parent.id = :prId"
                    + " AND p.state not in (:state)"

        ),
        @NamedQuery(
            name = ProcessPartEntity.QUERY_COUNT_BY_PROCESS_AND_STATES,
            query = "SELECT COUNT (p) FROM ProcessPartEntity p WHERE p.parent.id = :prId AND p.state IN (:state)"
        ),
        @NamedQuery(
            name = ProcessPartEntity.QUERY_UPDATE_STATE,
            query = "UPDATE ProcessPartEntity p SET p.state =:state WHERE p.id in (:ids)"
        ),
        @NamedQuery(
            name = ProcessPartEntity.QUERY_UPDATE_STATE_BY_STATES,
            query = "UPDATE ProcessPartEntity p SET p.state =:state WHERE p.state IN (:st) AND p.id in (:ids)"
        ),
        @NamedQuery(
            name = ProcessPartEntity.QUERY_DELETE_BY_PROCESS,
            query = "DELETE FROM ProcessPartEntity p WHERE p.parent.id=:prId"
        )
    })
public class ProcessPartEntity extends DbEntity {
    public static final int STRING_PARAMS_LENGTH = 512;

    static final String QUERY_SELECT_BY_PROCESS = "selectPartsByProcess";
    static final String QUERY_SELECT_BY_IDS = "selectPartsByIds";
    static final String QUERY_SELECT_UID_BY_PROCESS = "selectPartsUIDByProcess";
    static final String QUERY_SELECT_MESSAGE_BY_PROCESS = "selectPartsMessagesByProcess";
    static final String QUERY_SELECT_BY_PROCESS_AND_STATE = "selectPartsByProcessAndState";
    static final String QUERY_COUNT_BY_PROCESS = "countPartsByProcess";
    static final String QUERY_COUNT_BY_PROCESS_AND_NO_STATE = "countPartsByProcessAndNoState";
    static final String QUERY_COUNT_BY_PROCESS_AND_STATES = "countPartsByProcessAndStates";
    static final String QUERY_UPDATE_STATE = "updatePartsState";
    static final String QUERY_UPDATE_STATE_BY_STATES = "updatePartsStateByStates";
    static final String QUERY_DELETE_BY_PROCESS = "deletePartsByProcess";

    private static final String PARAMS_SEPARATOR = ";";
    private static final String PARAM_VALUE_SEPARATOR = "=";
    private static final String PARAM_PROCESS_ID = "prId";

    private com.wiley.cms.process.entity.ProcessEntity parent;
    private ProcessState state = ProcessState.NONE;
    private String uri;
    private String message;
    private String params;

    public ProcessPartEntity() {
    }

    public static Query queryProcessPartRecords(int processId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_PROCESS).setParameter(PARAM_PROCESS_ID, processId);
    }

    public static Query queryProcessPartRecords(Collection<Integer> processPartIds, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_IDS).setParameter(PARAM_IDS, processPartIds);
    }

    public static Query queryProcessPartRecordUIDs(int processId, int start, int size, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_UID_BY_PROCESS).setParameter(
                PARAM_PROCESS_ID, processId).setFirstResult(start).setMaxResults(size);
    }

    public static Query queryProcessPartRecordMessage(int processId, int skip, int size, EntityManager manager) {
        Query q = manager.createNamedQuery(QUERY_SELECT_MESSAGE_BY_PROCESS).setParameter(PARAM_PROCESS_ID, processId);
        appendBatchResults(q, skip, size);
        return q;
    }

    public static Query queryProcessPartRecordCount(int processId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_COUNT_BY_PROCESS)
                .setParameter(PARAM_PROCESS_ID, processId).setMaxResults(1);
    }

    public static Query queryProcessPartRecordCount(int processId, Collection<ProcessState> states,
            EntityManager manager) {
        return manager.createNamedQuery(QUERY_COUNT_BY_PROCESS_AND_STATES).setParameter(PARAM_PROCESS_ID,
                processId).setParameter(ProcessEntity.PARAM_STATE, states).setMaxResults(1);
    }

    public static Query queryProcessPartRecordCountNotInState(int processId, Collection<ProcessState> states,
            EntityManager manager) {
        return manager.createNamedQuery(QUERY_COUNT_BY_PROCESS_AND_NO_STATE).setParameter(PARAM_PROCESS_ID,
                processId).setParameter(ProcessEntity.PARAM_STATE, states).setMaxResults(1);
    }

    public static Query queryProcessPartRecord(int processId, Collection<ProcessState> states, int count,
            EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_PROCESS_AND_STATE).setParameter(PARAM_PROCESS_ID,
            processId).setParameter(ProcessEntity.PARAM_STATE, states).setMaxResults(count);
    }

    public static Query queryUpdateProcessPartRecordState(Collection<Integer> processPartIds, ProcessState state,
            EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_STATE).setParameter(
                ProcessEntity.PARAM_STATE, state).setParameter(PARAM_IDS, processPartIds);
    }

    public static Query queryUpdateProcessPartState(Collection<Integer> processPartIds, ProcessState state,
        Collection<ProcessState> states, EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_STATE_BY_STATES).setParameter(
                ProcessEntity.PARAM_STATE, state).setParameter(PARAM_IDS, processPartIds).setParameter("st", states);
    }

    public static Query queryDelete(int processId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_PROCESS).setParameter(PARAM_PROCESS_ID, processId);
    }

    @ManyToOne
    @JoinColumn(name = "process_id", updatable = false)
    public ProcessEntity getParent() {
        return parent;
    }

    public void setParent(ProcessEntity entity) {
        parent = entity;
    }

    @Column(updatable = false)
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    @Column(length = STRING_MEDIUM_TEXT_LENGTH)
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Column(name = "params", length = STRING_PARAMS_LENGTH, updatable = false)
    public String getParams() {
        return params;
    }

    public void setParams(String paramsStr) {
        params = paramsStr;
    }

    @Enumerated(EnumType.ORDINAL)
    @Column(nullable = false)
    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }
}
