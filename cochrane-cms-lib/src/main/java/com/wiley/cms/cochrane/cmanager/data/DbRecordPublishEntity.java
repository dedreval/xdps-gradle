package com.wiley.cms.cochrane.cmanager.data;

import com.wiley.cms.cochrane.cmanager.data.record.RecordEntity;
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
@Table(name = "COCHRANE_RECORD_PUBLISH")
@NamedQueries({
        @NamedQuery(
                name = DbRecordPublishEntity.Q_VO_BY_REC_ID,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.RecordPublishVO(rp)"
                        + " FROM DbRecordPublishEntity rp WHERE rp.record.id = :id"
        ),
        @NamedQuery(
                name = DbRecordPublishEntity.Q_VO_BY_REC_IDS_AND_STATES,
                query = "SELECT new com.wiley.cms.cochrane.cmanager.data.RecordPublishVO(rp)"
                        + " FROM DbRecordPublishEntity rp WHERE rp.record.id IN (:id) AND rp.state IN (:state)"
        ),
        @NamedQuery(
                name = DbRecordPublishEntity.Q_REC_ID_BY_REC_IDS_AND_STATES,
                query = "SELECT rp.record.id FROM DbRecordPublishEntity rp WHERE rp.record.id IN (:id)"
                        + " AND rp.state IN (:state)"
        ),
        @NamedQuery(
                name = DbRecordPublishEntity.Q_COUNT_BY_STATE_AND_DB_ID,
                query = "SELECT COUNT(rp.id) FROM DbRecordPublishEntity rp WHERE rp.state = :state"
                        + " AND rp.record.db.id = :id"
        ),
        @NamedQuery(
                name = DbRecordPublishEntity.Q_COUNT_BY_RECORD_IDS_AND_STATES,
                query = "SELECT COUNT(rp.id) FROM DbRecordPublishEntity rp WHERE rp.record.id IN (:id)"
                        + " AND rp.state IN (:state)"
        ),
        @NamedQuery(
                name = DbRecordPublishEntity.Q_UPDATE_STATE_BY_REC_IDS,
                query = "UPDATE DbRecordPublishEntity rp SET rp.date = :date, rp.state = :state"
                        + " WHERE rp.record.id IN (:id)"
        ),
        @NamedQuery(
                name = DbRecordPublishEntity.Q_DELETE_BY_RECORD_IDS,
                query = "DELETE FROM DbRecordPublishEntity rp WHERE rp.record.id IN (:id)"
        ),
        @NamedQuery(
                name = "dbRecordPublishCountConvertedSuccessfulByRecordIds",
                query = "SELECT COUNT(rp.id) FROM DbRecordPublishEntity rp WHERE rp.record.id IN (:ids)"
                        + " AND rp.state = " + RecordPublishEntity.CONVERTED
        ),
        @NamedQuery(
                name = "deleteDbRecordPublishByRecord",
                query = "DELETE FROM DbRecordPublishEntity rp WHERE rp.record = :record"
        ),
        @NamedQuery(
                name = "deleteDbRecordPublishByDb",
                query = "DELETE FROM DbRecordPublishEntity rp WHERE"
                        + " rp.record.id IN (SELECT r.id FROM RecordEntity r WHERE r.db = :db)"
        )
    })
public class DbRecordPublishEntity extends RecordPublishEntity implements Serializable {

    static final String Q_VO_BY_REC_ID = "dbRecordPublishVOByRecId";
    static final String Q_VO_BY_REC_IDS_AND_STATES = "dbRecordPublishVOByRecIdsAndStates";
    static final String Q_REC_ID_BY_REC_IDS_AND_STATES = "dbRecordPublishRecIdByRecIdsAndStates";
    static final String Q_COUNT_BY_STATE_AND_DB_ID = "dbRecordPublishCountByStateAndDbId";
    static final String Q_COUNT_BY_RECORD_IDS_AND_STATES = "dbRecordPublishCountByRecordIdsAndStates";
    static final String Q_UPDATE_STATE_BY_REC_IDS = "updateDbRecordPublishStateByRecIds";
    static final String Q_DELETE_BY_RECORD_IDS = "deleteDbRecordPublishByRecordIds";

    private RecordEntity record;

    public static Query qVOByRecordId(int recId, EntityManager manager) {
        return manager.createNamedQuery(Q_VO_BY_REC_ID).setParameter(Constants.ID_PRM, recId);
    }

    public static Query qVOByRecordIdsAndStates(List<Integer> recIds, List<Integer> states, EntityManager manager) {
        return manager.createNamedQuery(Q_VO_BY_REC_IDS_AND_STATES)
                .setParameter(Constants.ID_PRM, recIds)
                .setParameter(Constants.STATE_PRM, states);
    }

    public static Query qRecordId(List<Integer> recIds, List<Integer> states, EntityManager manager) {
        return manager.createNamedQuery(Q_REC_ID_BY_REC_IDS_AND_STATES)
                .setParameter(Constants.ID_PRM, recIds)
                .setParameter(Constants.STATE_PRM, states);
    }

    public static Query qCountByStateAndDbId(int state, int dbId, EntityManager manager) {
        return manager.createNamedQuery(Q_COUNT_BY_STATE_AND_DB_ID)
                .setParameter(Constants.STATE_PRM, state)
                .setParameter(Constants.ID_PRM, dbId);
    }

    public static Query qCountByRecordIdsAndStates(List<Integer> recordIds,
                                                   List<Integer> states,
                                                   EntityManager manager) {
        return manager.createNamedQuery(Q_COUNT_BY_RECORD_IDS_AND_STATES)
                .setParameter(Constants.ID_PRM, recordIds)
                .setParameter(Constants.STATE_PRM, states);
    }

    public static Query qUpdateStateByRecordIds(Date date, int state, List<Integer> recIds, EntityManager manager) {
        return manager.createNamedQuery(Q_UPDATE_STATE_BY_REC_IDS)
                .setParameter(Constants.DATE_PRM, date)
                .setParameter(Constants.STATE_PRM, state)
                .setParameter(Constants.ID_PRM, recIds);
    }

    public static Query qDeleteByRecordIds(Collection<Integer> recordIds, EntityManager manager) {
        return manager.createNamedQuery(Q_DELETE_BY_RECORD_IDS).setParameter(Constants.ID_PRM, recordIds);
    }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false, unique = true)
    public RecordEntity getRecord() {
        return record;
    }

    public void setRecord(RecordEntity record) {
        this.record = record;
    }
}
