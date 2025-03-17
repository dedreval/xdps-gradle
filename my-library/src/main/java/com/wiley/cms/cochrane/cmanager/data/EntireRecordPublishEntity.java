package com.wiley.cms.cochrane.cmanager.data;

import com.wiley.cms.cochrane.cmanager.data.entire.EntireDBEntity;
import com.wiley.cms.cochrane.utils.Constants;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Query;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
import java.util.List;


/**
 * @author <a href='mailto:aasadulin@wiley.com'>Alexandr Asadulin</a>
 * @version 03.07.2012
 */
@Entity
@Table(name = "COCHRANE_ENTIRE_RECORD_PUBLISH")
@NamedQueries({
        @NamedQuery(
                name = EntireRecordPublishEntity.Q_VO_BY_REC_IDS_AND_STATES,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.RecordPublishVO(rp)"
                        + " FROM EntireRecordPublishEntity rp WHERE rp.record.id IN (:id) AND rp.state IN (:state)"
        ),
        @NamedQuery(
                name = EntireRecordPublishEntity.Q_REC_ID_BY_REC_IDS_AND_STATE,
                query = "SELECT rp.record.id FROM EntireRecordPublishEntity rp WHERE rp.record.id IN (:id)"
                        + " AND rp.state = :state"
        ),
        @NamedQuery(
                name = EntireRecordPublishEntity.Q_REC_ID_BY_REC_IDS_AND_STATES,
                query = "SELECT rp.record.id FROM EntireRecordPublishEntity rp WHERE rp.record.id IN (:id)"
                        + " AND rp.state IN (:state)"
        ),
        @NamedQuery(
                name = EntireRecordPublishEntity.Q_UPDATE_STATE_BY_RECORD_IDS,
                query = "UPDATE EntireRecordPublishEntity rp SET rp.date = :date, rp.state = :state"
                        + " WHERE rp.record.id IN (:id)"
        ),
        @NamedQuery(
                name = EntireRecordPublishEntity.Q_DELETE_BY_RECORD_ID,
                query = "DELETE FROM EntireRecordPublishEntity rp WHERE rp.record.id = :id"
        ),
        @NamedQuery(
                name = EntireRecordPublishEntity.Q_DELETE_BY_RECORD_IDS,
                query = "DELETE FROM EntireRecordPublishEntity rp WHERE rp.record.id IN (:id)"
        ),
        @NamedQuery(
                name = "entireRecordPublishCountConvertedSuccessfulByRecordIds",
                query = "SELECT COUNT(rp.id) FROM EntireRecordPublishEntity rp WHERE rp.record.id IN (:ids)"
                        + " AND rp.state = " + RecordPublishEntity.CONVERTED
        ),
        @NamedQuery(
                name = "deleteEntireRecordPublishByRecord",
                query = "DELETE FROM EntireRecordPublishEntity rp WHERE rp.record = :record"
        ),
        @NamedQuery(
                name = "deleteEntireRecordPublishByIssueAndDb",
                query = "DELETE FROM EntireRecordPublishEntity rp WHERE"
                        + " rp.record.id IN (SELECT r.id FROM EntireDBEntity r WHERE r.database = :db"
                        + " AND r.lastIssuePublished = :lastIssuePublished)"
        )
    })
public class EntireRecordPublishEntity extends RecordPublishEntity implements Serializable {

    static final String Q_VO_BY_REC_IDS_AND_STATES = "entireRecordPublishVOByRecIdsAndStates";
    static final String Q_REC_ID_BY_REC_IDS_AND_STATE = "entireRecordPublishRecIdByRecIdsAndState";
    static final String Q_REC_ID_BY_REC_IDS_AND_STATES = "entireRecordPublishRecIdByRecIdsAndStates";
    static final String Q_UPDATE_STATE_BY_RECORD_IDS = "updateEntireRecordPublishStateByRecordIds";
    static final String Q_DELETE_BY_RECORD_ID = "deleteEntireRecordPublishByRecordId";
    static final String Q_DELETE_BY_RECORD_IDS = "deleteEntireRecordPublishByRecordIds";

    private EntireDBEntity record;

    public static Query qVOByRecordIdsAndStates(List<Integer> recIds, List<Integer> states, EntityManager manager) {
        return manager.createNamedQuery(Q_VO_BY_REC_IDS_AND_STATES)
                .setParameter(Constants.ID_PRM, recIds)
                .setParameter(Constants.STATE_PRM, states);
    }

    public static Query qRecordId(List<Integer> recIds, int state, EntityManager manager) {
        return manager.createNamedQuery(Q_REC_ID_BY_REC_IDS_AND_STATE)
                .setParameter(Constants.ID_PRM, recIds)
                .setParameter(Constants.STATE_PRM, state);
    }

    public static Query qRecordId(List<Integer> recIds, List<Integer> states, EntityManager manager) {
        return manager.createNamedQuery(Q_REC_ID_BY_REC_IDS_AND_STATES)
                .setParameter(Constants.ID_PRM, recIds)
                .setParameter(Constants.STATE_PRM, states);
    }

    public static Query qUpdateStateByRecordIds(Date date, int state, Collection<Integer> recordIds, EntityManager em) {
        return em.createNamedQuery(Q_UPDATE_STATE_BY_RECORD_IDS)
                .setParameter(Constants.DATE_PRM, date)
                .setParameter(Constants.STATE_PRM, state)
                .setParameter(Constants.ID_PRM, recordIds);
    }

    public static Query qDeleteByRecordId(int recordId, EntityManager manager) {
        return manager.createNamedQuery(Q_DELETE_BY_RECORD_ID).setParameter(Constants.ID_PRM, recordId);
    }

    public static Query qDeleteByRecordIds(Collection<Integer> recordIds, EntityManager manager) {
        return manager.createNamedQuery(Q_DELETE_BY_RECORD_IDS).setParameter(Constants.ID_PRM, recordIds);
    }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false, unique = true)
    public EntireDBEntity getRecord() {
        return record;
    }

    public void setRecord(EntireDBEntity record) {
        this.record = record;
    }
}
