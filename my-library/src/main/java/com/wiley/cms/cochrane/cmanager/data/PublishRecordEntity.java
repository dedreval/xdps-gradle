package com.wiley.cms.cochrane.cmanager.data;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.wiley.cms.cochrane.cmanager.entitymanager.RecordHelper;
import com.wiley.cms.converter.services.RevmanMetadataHelper;

/**
 * @author <a href='mailto:osoletskay@wiley.ru'>Olga Soletskaya</a>
 *         Date: 4/12/2018
 */

@Entity
@Table(name = "COCHRANE_PUBLISH_RECORD")
@NamedQueries({
        @NamedQuery(
            name = PublishRecordEntity.QUERY_READY_PUBLISH,
            query = "SELECT pre FROM PublishRecordEntity pre WHERE pre.publishPacket.db.id IN (:db)"
                + " AND pre.handledDate IS NULL AND pre.date IS NOT NULL"
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_READY_PUBLISH_BY_NUMBER_AND_PUB,
            query = "SELECT pre FROM PublishRecordEntity pre WHERE pre.publishPacket.db.id IN (:db)"
                + " AND pre.number=:nu AND pre.pubNumber=:pu AND pre.handledDate IS NULL AND pre.date IS NOT NULL"
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_READY_PUBLISH_BY_NUMBER_AND_PUB_AND_TYPE,
            query = "SELECT pre FROM PublishRecordEntity pre WHERE pre.publishPacket.db.id IN (:db)"
                 + " AND pre.publishPacket.publishType IN (:pt) AND pre.number=:nu AND pre.pubNumber=:pu"
                 + " AND pre.handledDate IS NULL AND pre.date IS NOT NULL"
        ),
        @NamedQuery(
             name = PublishRecordEntity.QUERY_UNPUBLISHED_BY_TYPES_AND_NUMBER_AND_PUB,
             query = "SELECT pre FROM PublishRecordEntity pre WHERE"
                     + " pre.publishPacket.publishType IN (:pt) AND pre.number=:nu AND pre.pubNumber=:pu"
                     + " AND pre.handledDate IS NULL AND pre.date IS NULL"
        ),
        @NamedQuery (
             name = PublishRecordEntity.QUERY_SENT_UNPUBLISHED_BY_NUMBER,
             query = "SELECT pre FROM PublishRecordEntity pre WHERE pre.publishPacket.publishType IN (:pt)"
                 + " AND pre.publishPacket.db.database.id = :db AND pre.publishPacket.sent = TRUE"
                 + " AND pre.number=:nu"
                 + " AND pre.handledDate IS NULL AND pre.date IS NULL AND pre.publishPacket.sendingDate > :dt"
                 + " ORDER BY pre.publishPacket.sendingDate DESC"
        ),
        @NamedQuery (
            name = PublishRecordEntity.QUERY_SENT_UNPUBLISHED_BY_NUMBER_AND_PUB,
            query = "SELECT pre FROM PublishRecordEntity pre WHERE pre.publishPacket.publishType IN (:pt)"
                    + " AND pre.publishPacket.db.database.id = :db AND pre.publishPacket.sent = TRUE"
                    + " AND pre.number=:nu AND pre.pubNumber=:pu"
                    + " AND pre.handledDate IS NULL AND pre.date IS NULL AND pre.publishPacket.sendingDate > :dt"
                    + " ORDER BY pre.publishPacket.sendingDate DESC"
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_PUBLISH_BETWEEN_NUMBERS,
            query = "SELECT pre FROM PublishRecordEntity pre WHERE pre.number>=:n1 AND pre.number<=:n2"
                    + " ORDER BY pre.number, pre.pubNumber, pre.id DESC"
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_SENT_PUBLISH_BETWEEN_NUMBERS,
            query = "SELECT pre FROM PublishRecordEntity pre WHERE pre.publishPacket.id <>:id"
                    + " AND pre.publishPacket.parentId <>:id"
                    + " AND pre.publishPacket.publishType IN (:pt) AND pre.publishPacket.sent=true"
                    + " AND pre.number>=:n1 AND pre.number<=:n2"
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_SENT_NUMBERS_BETWEEN_NUMBERS,
            query = "SELECT new java.lang.Integer(pre.number) FROM PublishRecordEntity pre"
                + " WHERE pre.publishPacket.id <>:id AND pre.publishPacket.parentId <>:id"
                + " AND pre.publishPacket.publishType IN (:pt) AND pre.publishPacket.sent=true"
                + " AND pre.number>=:n1 AND pre.number<=:n2"
                + " AND pre.publishState > " + PublishRecordEntity.PUBLISHED_FAILED + " GROUP BY pre.number"
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_SENT_NUMBER_PUBS_BETWEEN_NUMBERS,
            query = "SELECT pre.number, pre.pubNumber FROM PublishRecordEntity pre"
                + " WHERE pre.publishPacket.id <>:id AND pre.publishPacket.parentId <>:id"
                + " AND pre.publishPacket.publishType IN (:pt) AND pre.publishPacket.sent=true"
                + " AND pre.number>=:n1 AND pre.number<=:n2"
                + " AND pre.publishState > " + PublishRecordEntity.PUBLISHED_FAILED
                + " GROUP BY pre.number, pre.pubNumber"
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_SENT_PUBLISH_BY_NUMBERS,
            query = "SELECT pre FROM PublishRecordEntity pre WHERE pre.publishPacket.id <>:id"
                    + " AND pre.publishPacket.parentId <>:id"
                    + " AND pre.publishPacket.publishType IN (:pt) AND pre.publishPacket.sent=true"
                    + " AND pre.number>=:n1 AND pre.number<=:n2 AND pre.number IN (:nu)"
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_SENT_NUMBERS_BY_NUMBERS,
            query = "SELECT pre.number FROM PublishRecordEntity pre WHERE pre.publishPacket.id <>:id"
                + " AND pre.publishPacket.parentId <>:id"
                + " AND pre.publishPacket.publishType IN (:pt) AND pre.publishPacket.sent=true"
                + " AND pre.number>=:n1 AND pre.number<=:n2 AND pre.number IN (:nu)"
                + " AND pre.publishState > " + PublishRecordEntity.PUBLISHED_FAILED + " GROUP BY pre.number"
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_SENT_NUMBER_PUBS_BY_NUMBERS,
            query = "SELECT pre.number, pre.pubNumber FROM PublishRecordEntity pre WHERE pre.publishPacket.id <>:id"
                + " AND pre.publishPacket.parentId <>:id"
                + " AND pre.publishPacket.publishType IN (:pt) AND pre.publishPacket.sent=true"
                + " AND pre.number>=:n1 AND pre.number<=:n2 AND pre.number IN (:nu)"
                + " AND pre.publishState > " + PublishRecordEntity.PUBLISHED_FAILED
                + " GROUP BY pre.number, pre.pubNumber"
        ),
        @NamedQuery(
                name = PublishRecordEntity.QUERY_SELECT_LAST_EPOCH_DATE,
                query = "SELECT MAX(pre.date) FROM PublishRecordEntity pre WHERE pre.date IS NOT NULL"
        ),
        @NamedQuery(
                name = PublishRecordEntity.QUERY_SELECT_BY_PUBLISH,
                query = "SELECT pre FROM PublishRecordEntity pre WHERE pre.publishPacket.id IN (:pi)"
                        + " ORDER BY pre.number, pre.id"
        ),
        @NamedQuery(
                name = PublishRecordEntity.QUERY_SELECT_COUNT_BY_PUBLISH,
                query = "SELECT COUNT (pre) FROM PublishRecordEntity pre WHERE pre.publishPacket.id =:pi"
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_COUNT_BY_PUBLISH_GROUP_BY_DF,
            query = "SELECT pre.deliveryId, COUNT (pre) FROM PublishRecordEntity pre WHERE pre.publishPacket.id =:pi"
                    + " GROUP BY pre.deliveryId"
        ),
        @NamedQuery(
                name = PublishRecordEntity.QUERY_DELETE_BY_DF_AND_NUMBER_AND_NULL_DATE,
                query = "DELETE FROM PublishRecordEntity pre WHERE pre.deliveryId =:df AND pre.number =:nu"
                    + " AND (pre.date IS NULL or pre.handledDate IS NULL)"
        ),
        @NamedQuery(
                name = PublishRecordEntity.QUERY_SELECT_BY_DB,
                query = "SELECT pre FROM PublishRecordEntity pre WHERE pre.publishPacket.db.id =:db"
        ),
        @NamedQuery(
                name = PublishRecordEntity.QUERY_SELECT_BY_DB_AND_NUMBER,
                query = "SELECT pre FROM PublishRecordEntity pre WHERE pre.publishPacket.db.id =:db"
                        + " AND pre.number =:nu"
        ),
        @NamedQuery(
                name = PublishRecordEntity.QUERY_SELECT_SENT_BY_DB_AND_TYPES_AND_NUMBERS,
                query = "SELECT pre FROM PublishRecordEntity pre WHERE pre.publishPacket.db.id =:db"
                        + " AND pre.publishPacket.publishType IN (:pt) AND pre.number IN (:nu)"
                        + " AND pre.publishPacket.sent = TRUE ORDER BY pre.id DESC"
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_COUNT_SENT_BY_DB_AND_TYPES,
            query = "SELECT COUNT (DISTINCT pre.number) FROM PublishRecordEntity pre WHERE pre.publishPacket.db.id =:db"
                + " AND pre.publishPacket.publishType IN (:pt) AND pre.publishPacket.sent = TRUE"
        ),
        //@NamedQuery(
        //  name = PublishRecordEntity.QUERY_SELECT_COUNT_SENT_BY_DB_AND_DF_AND_TYPES,
        //  query = "SELECT COUNT (DISTINCT pre.number) FROM PublishRecordEntity pre WHERE pre.publishPacket.db.id =:db"
        //      + " AND pre.publishPacket.publishType IN (:pt) AND pre.deliveryId=:df AND pre.publishPacket.sent = TRUE"
        //),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_COUNT_SENT_OR_FAILED_BY_DB_AND_DF_AND_TYPES,
            query = "SELECT COUNT (DISTINCT pre.number) FROM PublishRecordEntity pre WHERE pre.publishPacket.db.id =:db"
                    + " AND pre.publishPacket.publishType IN (:pt) AND pre.deliveryId=:df"
                    + " AND pre.publishPacket.sent IS NOT NULL ORDER BY pre.id DESC"
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_COUNT_PUBLISHED_BY_DB_AND_TYPES,
            query = "SELECT COUNT (DISTINCT pre.number) FROM PublishRecordEntity pre WHERE pre.publishPacket.db.id =:db"
                + " AND pre.publishPacket.publishType IN (:pt) AND pre.publishPacket.sent = TRUE"
                + " AND pre.publishState = " + PublishRecordEntity.PUBLISHED
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_COUNT_PUBLISHED_BY_DB_AND_DF_AND_TYPES,
            query = "SELECT COUNT (DISTINCT pre.number) FROM PublishRecordEntity pre WHERE pre.publishPacket.db.id =:db"
                + " AND pre.publishPacket.publishType IN (:pt) AND pre.deliveryId=:df AND pre.publishPacket.sent = TRUE"
                + " AND pre.publishState=" + PublishRecordEntity.PUBLISHED
        ),
        @NamedQuery(
            name = PublishRecordEntity.QUERY_SELECT_COUNT_HANDLED_BY_DB_AND_DF_AND_TYPES,
            query = "SELECT COUNT (DISTINCT pre.number) FROM PublishRecordEntity pre WHERE pre.publishPacket.db.id =:db"
                 + " AND pre.publishPacket.publishType IN (:pt) AND pre.deliveryId=:df AND pre.publishPacket.sent=TRUE"
                 + " AND pre.publishState <> 0"
        ),
        @NamedQuery(
                name = PublishRecordEntity.QUERY_SELECT_BY_DF_AND_NUMBER,
                query = "SELECT pre FROM PublishRecordEntity pre  WHERE pre.deliveryId =:df"
                        + " AND pre.publishPacket.publishType IN (:pt) AND pre.number =:nu"
        ),
        @NamedQuery(
                name = PublishRecordEntity.QUERY_SELECT_SENT_AFTER_DATE_BY_DB_AND_PUB_TYPES,
                query = "SELECT pre FROM PublishRecordEntity pre"
                        + " WHERE pre.publishPacket.sendingDate > :date"
                        + " AND pre.publishPacket.sent = TRUE"
                        + " AND pre.publishPacket.publishType IN (:pt)"
                        + " AND pre.publishPacket.db.database.name = :db"
        ),
        @NamedQuery(
                name = PublishRecordEntity.QUERY_UPDATE_EPOCH_DATE,
                query = "UPDATE PublishRecordEntity pre SET pre.date =:ed WHERE pre.publishPacket.id =:pi"
        ),
        @NamedQuery(
                name = PublishRecordEntity.QUERY_SELECT_BY_RECORD_AND_PUB_AND_DF,
                query = "SELECT pre FROM PublishRecordEntity pre"
                                + " WHERE pre.deliveryId =:df"
                                + " AND pre.number =:nu"
                                + " AND pre.pubNumber =:pub"
        )
    })
public class PublishRecordEntity extends RecordAbstract {
    public static final int MAX_DIAPASON = 5000;
    public static final String QUERY_SELECT_PUBLISH_BETWEEN_NUMBERS = "selectPublishRecBetweenNumbers";
    public static final String QUERY_SELECT_SENT_PUBLISH_BETWEEN_NUMBERS = "selectSentPublishRecBetweenNumbers";
    public static final String QUERY_SELECT_SENT_PUBLISH_BY_NUMBERS = "selectSentPublishRecByNumbers";
    public static final String QUERY_SELECT_SENT_NUMBERS_BETWEEN_NUMBERS = "selectSentNumbersBetweenNumbers";
    public static final String QUERY_SELECT_SENT_NUMBERS_BY_NUMBERS = "selectSentNumbersByNumbers";
    public static final String QUERY_SELECT_SENT_NUMBER_PUBS_BETWEEN_NUMBERS = "selectSentNumbersAndPubsBetweenNumbers";
    public static final String QUERY_SELECT_SENT_NUMBER_PUBS_BY_NUMBERS = "selectSentNumbersAndPubsByNumbers";

    static final String QUERY_UPDATE_EPOCH_DATE = "updateEpochDate";
    static final String QUERY_SELECT_LAST_EPOCH_DATE = "selectLastEpochDate";
    static final String QUERY_READY_PUBLISH = "selectReadyPublishRec";
    static final String QUERY_READY_PUBLISH_BY_NUMBER_AND_PUB = "selectReadyPublishRecByNumberAndPub";
    static final String QUERY_READY_PUBLISH_BY_NUMBER_AND_PUB_AND_TYPE = "selectReadyPublishRecByNumberAndPubAndType";
    static final String QUERY_UNPUBLISHED_BY_TYPES_AND_NUMBER_AND_PUB = "selectUnpublishedByDbTypesAndNumberAndPub";
    static final String QUERY_SENT_UNPUBLISHED_BY_NUMBER = "selectSentUnpublishedRecByNumber";
    static final String QUERY_SENT_UNPUBLISHED_BY_NUMBER_AND_PUB = "selectSentUnpublishedRecByNumberAndPub";
    static final String QUERY_SELECT_BY_PUBLISH =  "selectPublishRecByPublish";
    static final String QUERY_SELECT_COUNT_BY_PUBLISH =  "selectCountPublishRecByPublish";
    static final String QUERY_SELECT_COUNT_BY_PUBLISH_GROUP_BY_DF =  "selectCountPublishRecByPublishGroupByDf";
    static final String QUERY_DELETE_BY_DF_AND_NUMBER_AND_NULL_DATE = "deletePublishRecByDfAndNumberAndNullDate";

    static final String QUERY_SELECT_BY_DB = "selectEntirePublishRecByDb";
    static final String QUERY_SELECT_BY_DB_AND_NUMBER = "selectPublishRecByDbAndNumber";
    static final String QUERY_SELECT_SENT_BY_DB_AND_TYPES_AND_NUMBERS = "selectPublishRecSentByDbAndTypesAndNumbers";
    static final String QUERY_SELECT_COUNT_SENT_BY_DB_AND_TYPES = "selectCountPublishRecSentByDbAndTypes";
    static final String QUERY_SELECT_COUNT_PUBLISHED_BY_DB_AND_TYPES = "selectCountPublishRecPublishedByDbAndTypes";
    static final String QUERY_SELECT_COUNT_PUBLISHED_BY_DB_AND_DF_AND_TYPES =
            "selectCountPublishRecPublishedByDbAndDfAndTypes";
    static final String QUERY_SELECT_COUNT_HANDLED_BY_DB_AND_DF_AND_TYPES =
            "selectCountPublishReHandledByDbAndDfAndTypes";
    //static final String QUERY_SELECT_COUNT_SENT_BY_DB_AND_DF_AND_TYPES = "selectCountPublishRecSentByDbAndDfAndTypes";
    static final String QUERY_SELECT_COUNT_SENT_OR_FAILED_BY_DB_AND_DF_AND_TYPES =
            "selectCountPublishRecSentOrFailedByDbAndDfAndTypes";
    static final String QUERY_SELECT_BY_DF_AND_NUMBER = "selectPublishRecByDfAndNumber";
    static final String QUERY_SELECT_SENT_AFTER_DATE_BY_DB_AND_PUB_TYPES = "selectSentAfterDateByDbAndPubTypes";
    static final String QUERY_SELECT_BY_RECORD_AND_PUB_AND_DF = "findPublishByRecordAndPubAndDf";

    static final int PUBLISHED_FAILED = -1;
    static final int PUBLISHED = 1;

    private static final String QUERY_UPDATE_EPOCH_DATE_BY_TYPE = "UPDATE  COCHRANE_PUBLISH_RECORD pr"
        + " JOIN COCHRANE_PUBLISH p ON (pr.publish_id = p.id)  SET pr.date =:ed WHERE p.db_id IN (:db)"
        + " AND p.type_id =:pt AND pr.number =:nu AND pr.pub =:pu AND pr.handled IS NULL";

    private static final String QUERY_UPDATE_PUB_EVENT_DATE_BY_TYPES_AND_PACKAGE = "UPDATE COCHRANE_PUBLISH_RECORD pr"
        + " JOIN COCHRANE_PUBLISH p ON (pr.publish_id = p.id) SET pr.date =:ed WHERE p.db_id IN (:db)"
        + " AND p.type_id IN (:pt) AND pr.number =:nu AND pr.pub =:pu AND p.fileName =:fn";

    //private static final String QUERY_SELECT_BY_WAIT_PUBLISH = "SELECT pre.* FROM COCHRANE_PUBLISH_WAIT w"
    //    + " JOIN COCHRANE_PUBLISH_RECORD pre ON (w.publish_id=pre.publish_id)"
    //        + " WHERE w.delivery_sid=:fn AND pre.publishDate IS NULL AND pre.date IS NULL";

    //private static final String QUERY_FIND_BY = "UPDATE COCHRANE_TRANSLATED_ABSTRACTS_PUBLISHED pae"
    //        + " LEFT JOIN COCHRANE_RECORD r ON (r.id=pae.record_id)"
    //        + " SET pae.notified =:no, r.state = " + RecordEntity.STATE_PROCESSING + " WHERE pae.id IN (:id)";

    private PublishEntity publishPacket;
    private Date handled;
    private int published;  // 0 - unknown published state, 1 - published (CO) , -1 - failed (LTP), 2 - loaded (LTP)
    //private Integer recordId;

    public PublishRecordEntity() {
    }

    public PublishRecordEntity(int number, int pub, Integer dfId, PublishEntity pe) {
        setNumber(number);
        setPubNumber(pub);
        setDeliveryId(dfId);
        setPublishPacket(pe);
    }

    public static Query querySetEpochDate(int number, int pub, Date date, Integer ptype, Collection<Integer> dbIds,
                                          EntityManager manager) {
        return manager.createNativeQuery(QUERY_UPDATE_EPOCH_DATE_BY_TYPE).setParameter("pt", ptype).setParameter(
                "nu", number).setParameter("pu", pub).setParameter("ed", date).setParameter("db", dbIds);
    }

    public static Query querySetPubEventDate(int number, int pub, Date date, String fileName, Collection<Integer> types,
                                             Collection<Integer> dbIds, EntityManager manager) {
        return manager.createNativeQuery(QUERY_UPDATE_PUB_EVENT_DATE_BY_TYPES_AND_PACKAGE).setParameter(
                "pt", types).setParameter("nu", number).setParameter("pu", pub).setParameter("ed", date).setParameter(
                        "fn", fileName).setParameter("db", dbIds);
    }

    public static Query querySetAbstractsDate(int publishId, Date date, EntityManager manager) {
        return manager.createNamedQuery(QUERY_UPDATE_EPOCH_DATE).setParameter("pi", publishId).setParameter("ed", date);
    }

    @Deprecated
    public static Query queryReadyToPublish(int recordNumber, int pub, Collection<Integer> dbIds, EntityManager m) {
        return m.createNamedQuery(QUERY_READY_PUBLISH_BY_NUMBER_AND_PUB).setParameter(
                "nu", recordNumber).setParameter("pu", pub).setParameter("db", dbIds);
    }

    public static Query queryReadyToPublish(int recordNumber, int pub, Collection<Integer> dbIds,
                                            Collection<Integer> pubTypesIds, EntityManager m) {
        return m.createNamedQuery(QUERY_READY_PUBLISH_BY_NUMBER_AND_PUB_AND_TYPE).setParameter(
                "nu", recordNumber).setParameter("pu", pub).setParameter("db", dbIds).setParameter("pt", pubTypesIds);
    }

    public static Query querySentUnpublished(int recordNumber, int pub, Collection<Integer> pubTypesIds, int baseType,
                                             Date date, EntityManager m) {
        return pub > 0 ? m.createNamedQuery(QUERY_SENT_UNPUBLISHED_BY_NUMBER_AND_PUB).setParameter(
            "nu", recordNumber).setParameter("pu", pub).setParameter("pt", pubTypesIds).setParameter(
                    "db", baseType).setParameter("dt", date)
                : m.createNamedQuery(QUERY_SENT_UNPUBLISHED_BY_NUMBER).setParameter("nu", recordNumber).setParameter(
                    "pt", pubTypesIds).setParameter("db", baseType).setParameter("dt", date);
    }

    public static Query queryUnhandled(int recordNumber, int pub, Collection<Integer> pubTypesIds, EntityManager m) {
        return m.createNamedQuery(QUERY_UNPUBLISHED_BY_TYPES_AND_NUMBER_AND_PUB).setParameter(
                "nu", recordNumber).setParameter("pu", pub).setParameter("pt", pubTypesIds);
    }

    public static Query queryReadyToPublish(Collection<Integer> dbIds, EntityManager m) {
        return m.createNamedQuery(QUERY_READY_PUBLISH).setParameter("db", dbIds);
    }

    //public static Query queryWaitForPublish(String pfSid, EntityManager m) {
    //    return m.createNativeQuery(QUERY_SELECT_BY_WAIT_PUBLISH).setParameter("fn", pfSid);
    //}

    public static Query querySent(int minNumber, int maxNumber, Collection<Integer> pubTypesIds, Integer skipPublishId,
                                  EntityManager m) {
        return querySent(minNumber, maxNumber, pubTypesIds, skipPublishId,
                QUERY_SELECT_SENT_PUBLISH_BETWEEN_NUMBERS, m);
    }

    public static Query querySent(int minNumber, int maxNumber, Collection<Integer> pubTypesIds,
                                  Integer skipPublishId, String query, EntityManager m) {
        return m.createNamedQuery(query).setParameter("n1", minNumber).setParameter("n2", maxNumber).setParameter(
                "pt", pubTypesIds).setParameter("id", skipPublishId);
    }

    public static Query querySent(int minNumber, int maxNumber, Collection<Integer> numbers,
                                  Collection<Integer> pubTypesIds, Integer skipPublishId, EntityManager m) {
        return querySent(minNumber, maxNumber, numbers, pubTypesIds, skipPublishId,
                QUERY_SELECT_SENT_PUBLISH_BY_NUMBERS, m);
    }

    public static Query querySent(int minNumber, int maxNumber, Collection<Integer> numbers,
        Collection<Integer> pubTypesIds, Integer skipPublishId, String query, EntityManager m) {
        return m.createNamedQuery(query).setParameter("nu", numbers).setParameter("n1", minNumber).setParameter(
                "n2", maxNumber).setParameter("pt", pubTypesIds).setParameter("id", skipPublishId);
    }

    public static Query queryAll(int minNumber, int maxNumber, int beginIndex, int limit, EntityManager m) {
        return appendBatchResults(m.createNamedQuery(QUERY_SELECT_PUBLISH_BETWEEN_NUMBERS).setParameter(
                "n1", minNumber).setParameter("n2", maxNumber), beginIndex, limit);
    }

    @Deprecated
    public static Query queryLastEpochDate(EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_LAST_EPOCH_DATE);
    }

    public static Query queryAll(Collection<Integer> publishIds, int beginIndex, int limit, EntityManager manager) {
        return appendBatchResults(manager.createNamedQuery(QUERY_SELECT_BY_PUBLISH).setParameter(
                "pi", publishIds), beginIndex, limit);
    }

    public static Query queryCount(Integer publishId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_COUNT_BY_PUBLISH_GROUP_BY_DF).setParameter("pi", publishId);
    }

    public static Query queryDeleteUnPublishedRecords(int recordNumber, int dfId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_DELETE_BY_DF_AND_NUMBER_AND_NULL_DATE).setParameter(
                "df", dfId).setParameter("nu", recordNumber);
    }

    public static Query queryPublishByRecordAndPubAndDfId(int recordNumber, int pub, int dfId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_RECORD_AND_PUB_AND_DF).setParameter("df", dfId)
                      .setParameter("nu", recordNumber).setParameter("pub", pub);
    }

    public static Query queryPublishedRecordsByDb(int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB).setParameter("db", dbId);
    }

    public static Query queryPublishedRecordsByDb(int recordNumber, int dbId, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DB_AND_NUMBER).setParameter("db", dbId).setParameter(
                "nu", recordNumber);
    }

    public static Query querySentByDb(Collection<Integer> recordNumbers, Integer dbId, Collection<Integer> pubTypesIds,
                                      EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_SENT_BY_DB_AND_TYPES_AND_NUMBERS).setParameter(
                "db", dbId).setParameter("nu", recordNumbers).setParameter("pt", pubTypesIds);
    }

    //public static Query queryCountSentByDf(Integer dbId, Integer dfId, Collection<Integer> pubTypesIds,
    //                                       EntityManager manager) {
    //    return manager.createNamedQuery(QUERY_SELECT_COUNT_SENT_BY_DB_AND_DF_AND_TYPES).setParameter(
    //            "db", dbId).setParameter("df", dfId).setParameter("pt", pubTypesIds);
    //}

    public static Query queryCountSentOrFailedByDf(Integer dbId, Integer dfId, Collection<Integer> pubTypesIds,
                                           EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_COUNT_SENT_OR_FAILED_BY_DB_AND_DF_AND_TYPES).setParameter(
                "db", dbId).setParameter("df", dfId).setParameter("pt", pubTypesIds);
    }

    public static Query queryCountSentByDb(Integer dbId, Collection<Integer> pubTypesIds, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_COUNT_SENT_BY_DB_AND_TYPES).setParameter(
                "db", dbId).setParameter("pt", pubTypesIds);
    }

    public static Query queryCountPublishedByDb(Integer dbId, Collection<Integer> pubTypesIds, EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_COUNT_PUBLISHED_BY_DB_AND_TYPES).setParameter(
                "db", dbId).setParameter("pt", pubTypesIds);
    }

    public static Query queryCountPublishedByDf(Integer dbId, Integer dfId, Collection<Integer> pubTypesIds,
                                                EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_COUNT_PUBLISHED_BY_DB_AND_DF_AND_TYPES).setParameter(
                "db", dbId).setParameter("df", dfId).setParameter("pt", pubTypesIds);
    }

    public static Query queryCountHandledByDf(Integer dbId, Integer dfId, Collection<Integer> pubTypesIds,
                                                EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_COUNT_HANDLED_BY_DB_AND_DF_AND_TYPES).setParameter(
                "db", dbId).setParameter("df", dfId).setParameter("pt", pubTypesIds);
    }

    public static Query queryPublishedRecordsByDf(int recordNumber, int dfId, Collection<Integer> pubTypesIds,
                                                  EntityManager manager) {
        return manager.createNamedQuery(QUERY_SELECT_BY_DF_AND_NUMBER).setParameter("df", dfId).setParameter(
                "nu", recordNumber).setParameter("pt", pubTypesIds);
    }

    public static Query querySentAfterDate(String dbName,
                                           List<Integer> pubTypeIds,
                                           Date date,
                                           int offset,
                                           int limit,
                                           EntityManager manager) {
        Query q = manager.createNamedQuery(QUERY_SELECT_SENT_AFTER_DATE_BY_DB_AND_PUB_TYPES)
                .setParameter("db", dbName)
                .setParameter("pt", pubTypeIds)
                .setParameter("date", date);
        if (offset >= 0) {
            q.setFirstResult(offset);
        }
        if (limit > 0) {
            q.setMaxResults(limit);
        }
        return q;
    }

    @ManyToOne
    @JoinColumn(name = "publish_id", nullable = false, updatable = false)
    public PublishEntity getPublishPacket() {
        return publishPacket;
    }

    public void setPublishPacket(PublishEntity packet) {
        publishPacket = packet;
    }

    @Column(name = "handled")
    public Date getHandledDate() {
        return handled;
    }

    public void setHandledDate(Date date) {
        this.handled = date;
    }

    @Column(name = "published")
    public int getPublishState() {
        return published;
    }

    public void setPublishState(int value) {
        this.published = value;
    }

    @Transient
    public boolean isPublished() {
        return published == PUBLISHED;
    }

    @Transient
    public void setPublished() {
        published = PUBLISHED;
    }

    @Transient
    public boolean isFailed() {
        return published == PUBLISHED_FAILED;
    }

    @Transient
    public void setFailed() {
        published = PUBLISHED_FAILED;
    }

    @Transient
    public String getPubName() {
        return RevmanMetadataHelper.buildPubName(RecordHelper.buildCdNumber(getNumber()), getPubNumber());
    }
}
